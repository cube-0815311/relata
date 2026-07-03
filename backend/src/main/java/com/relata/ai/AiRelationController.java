package com.relata.ai;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/ai")
public class AiRelationController {

    private static final Logger log = LoggerFactory.getLogger(AiRelationController.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final HttpClient httpClient;
    private final String defaultModel;
    private final String deepSeekBaseUrl;
    private final String deepSeekApiKey;
    private final String deepSeekFlashModel;
    private final String deepSeekProModel;

    public AiRelationController(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${relata.ai.default-model:deepseek-v4-pro}") String defaultModel,
            @Value("${relata.ai.deepseek.base-url:https://api.deepseek.com}") String deepSeekBaseUrl,
            @Value("${relata.ai.deepseek.api-key:}") String deepSeekApiKey,
            @Value("${relata.ai.deepseek.flash-model:deepseek-v4-flash}") String deepSeekFlashModel,
            @Value("${relata.ai.deepseek.pro-model:deepseek-v4-pro}") String deepSeekProModel
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.defaultModel = defaultModel;
        this.deepSeekBaseUrl = deepSeekBaseUrl;
        this.deepSeekApiKey = deepSeekApiKey;
        this.deepSeekFlashModel = deepSeekFlashModel;
        this.deepSeekProModel = deepSeekProModel;
    }

    @GetMapping("/models")
    public AiModelsResponse models() {
        boolean deepSeekConfigured = hasText(deepSeekApiKey);
        return new AiModelsResponse(defaultModel, List.of(
                new AiModelOption("deepseek-v4-pro", "DeepSeek V4 Pro", "DEEPSEEK", deepSeekConfigured,
                        deepSeekConfigured ? "使用 DeepSeek V4 Pro 分析已采集元数据。" : "需要配置 DEEPSEEK_API_KEY 后启用。"),
                new AiModelOption("deepseek-v4-flash", "DeepSeek V4 Flash", "DEEPSEEK", deepSeekConfigured,
                        deepSeekConfigured ? "使用 DeepSeek V4 Flash 快速分析已采集元数据。" : "需要配置 DEEPSEEK_API_KEY 后启用。")
        ));
    }

    @PostMapping("/relations/analyze")
    public AnalyzeRelationsResponse analyzeRelations(@Valid @RequestBody AnalyzeRelationsRequest request) {
        log.info("AI relation analysis requested: dataSourceId={}, mainTable={}, model={}",
                request.dataSourceId(), request.mainTable(), request.model());
        List<TableMetadata> tables = loadMetadata(request.dataSourceId());
        int columnCount = tables.stream().mapToInt(table -> table.columns().size()).sum();
        log.info("Loaded metadata for AI relation analysis: dataSourceId={}, tableCount={}, columnCount={}",
                request.dataSourceId(), tables.size(), columnCount);
        if (tables.isEmpty()) {
            log.warn("AI relation analysis rejected because metadata is empty: dataSourceId={}", request.dataSourceId());
            throw new ResponseStatusException(BAD_REQUEST, "当前数据源尚未采集元数据");
        }
        TableMetadata mainTable = tables.stream()
                .filter(table -> table.name().equalsIgnoreCase(request.mainTable()))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("AI relation analysis rejected because main table was not found: dataSourceId={}, mainTable={}, availableTables={}",
                            request.dataSourceId(), request.mainTable(), tables.stream().map(TableMetadata::name).limit(50).toList());
                    return new ResponseStatusException(BAD_REQUEST, "主表不存在或尚未采集元数据：" + request.mainTable());
                });

        String model = hasText(request.model()) ? request.model() : defaultModel;
        boolean useDeepSeek = shouldUseDeepSeek(model);
        log.info("AI relation analysis model resolved: requestedModel={}, resolvedModel={}, provider={}, deepSeekConfigured={}",
                request.model(), model, useDeepSeek ? "DEEPSEEK" : "LOCAL", hasText(deepSeekApiKey));
        if (!useDeepSeek) {
            throw new ResponseStatusException(BAD_REQUEST, "请选择已启用的模型，并确认已配置 DEEPSEEK_API_KEY");
        }
        List<CandidateRelation> relations = analyzeWithDeepSeek(model, mainTable, tables);
        relations = enforceDirectedAcyclicRelations(mainTable.name(), relations);
        log.info("AI relation analysis completed: dataSourceId={}, mainTable={}, model={}, relationCount={}",
                request.dataSourceId(), request.mainTable(), model, relations.size());
        return new AnalyzeRelationsResponse(model, relations);
    }

    @PostMapping(value = "/relations/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeRelationsStream(@Valid @RequestBody AnalyzeRelationsRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);
        Thread worker = new Thread(() -> runStreamingAnalysis(request, emitter), "ai-relation-analysis-stream");
        worker.start();
        return emitter;
    }

    private void runStreamingAnalysis(AnalyzeRelationsRequest request, SseEmitter emitter) {
        try {
            emitProgress(emitter, "已收到分析请求，正在读取数据源元数据。");
            log.info("AI relation analysis stream requested: dataSourceId={}, mainTable={}, model={}",
                    request.dataSourceId(), request.mainTable(), request.model());

            List<TableMetadata> tables = loadMetadata(request.dataSourceId());
            int columnCount = tables.stream().mapToInt(table -> table.columns().size()).sum();
            emitProgress(emitter, "已读取 " + tables.size() + " 张表、" + columnCount + " 个字段。");
            log.info("Loaded metadata for streaming AI relation analysis: dataSourceId={}, tableCount={}, columnCount={}",
                    request.dataSourceId(), tables.size(), columnCount);
            if (tables.isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "当前数据源尚未采集元数据");
            }

            emitProgress(emitter, "正在定位主表 " + request.mainTable() + "。");
            TableMetadata mainTable = tables.stream()
                    .filter(table -> table.name().equalsIgnoreCase(request.mainTable()))
                    .findFirst()
                    .orElseThrow(() -> {
                        log.warn("Streaming AI relation analysis rejected because main table was not found: dataSourceId={}, mainTable={}, availableTables={}",
                                request.dataSourceId(), request.mainTable(), tables.stream().map(TableMetadata::name).limit(50).toList());
                        return new ResponseStatusException(BAD_REQUEST, "主表不存在或尚未采集元数据：" + request.mainTable());
                    });

            String model = hasText(request.model()) ? request.model() : defaultModel;
            boolean useDeepSeek = shouldUseDeepSeek(model);
            emitProgress(emitter, "已选择模型 " + model + "，分析模式：DeepSeek。");
            log.info("Streaming AI relation analysis model resolved: requestedModel={}, resolvedModel={}, provider={}, deepSeekConfigured={}",
                    request.model(), model, useDeepSeek ? "DEEPSEEK" : "LOCAL", hasText(deepSeekApiKey));
            if (!useDeepSeek) {
                throw new ResponseStatusException(BAD_REQUEST, "请选择已启用的模型，并确认已配置 DEEPSEEK_API_KEY");
            }

            List<CandidateRelation> relations = analyzeWithDeepSeekStream(model, mainTable, tables, emitter);

            emitProgress(emitter, "分析完成，共识别 " + relations.size() + " 条候选关系。");
            emitter.send(SseEmitter.event()
                    .name("result")
                    .data(new AnalyzeRelationsResponse(model, relations), MediaType.APPLICATION_JSON));
            emitter.send(SseEmitter.event().name("complete").data("done"));
            emitter.complete();
        } catch (Exception ex) {
            log.error("Streaming AI relation analysis failed: dataSourceId={}, mainTable={}, model={}",
                    request.dataSourceId(), request.mainTable(), request.model(), ex);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(errorMessage(ex), MediaType.TEXT_PLAIN));
            } catch (Exception sendEx) {
                log.warn("Failed to send streaming AI relation analysis error event", sendEx);
            }
            emitter.complete();
        }
    }

    private boolean shouldUseDeepSeek(String model) {
        return model != null && model.startsWith("deepseek-") && hasText(deepSeekApiKey);
    }

    private List<CandidateRelation> analyzeWithDeepSeek(String model, TableMetadata mainTable, List<TableMetadata> tables) {
        return analyzeWithDeepSeek(model, mainTable, tables, null);
    }

    private List<CandidateRelation> analyzeWithDeepSeek(String model, TableMetadata mainTable, List<TableMetadata> tables, SseEmitter emitter) {
        try {
            String modelName = deepSeekModelName(model);
            String prompt = buildPrompt(mainTable, tables);
            emitProgress(emitter, "正在整理表结构上下文，提示词长度 " + prompt.length() + " 字符。");

            log.info("Calling DeepSeek for relation analysis: logicalModel={}, apiModel={}, baseUrl={}, mainTable={}, tableCount={}, promptChars={},prompt={}",
                    model, modelName, normalizeBaseUrl(deepSeekBaseUrl), mainTable.name(), tables.size(),
                    prompt.length(),
                    prompt);
            emitProgress(emitter, "正在调用 DeepSeek 模型 " + modelName + "。");
            Map<String, Object> body = Map.of(
                    "model", modelName,
                    "temperature", 0.1,
                    "messages", List.of(
                            Map.of("role", "system", "content", """
                                    你是数据库建模助手。请只返回 JSON，不要 Markdown。
                                    基于表名、表注释、字段名、字段类型、主键和外键标记，从主表开始逐层识别表关系。
                                    关系必须以主表为根节点向下展开，sourceTable 是已发现的上层表，targetTable 是新发现的下层表。
                                    返回结果必须是有向无环图，不要输出会回指祖先、形成环或与主表不连通的关系。
                                    JSON 格式：{"relations":[{"sourceTable":"","sourceColumn":"","targetTable":"","targetColumn":"","relationType":"ONE_TO_MANY|MANY_TO_ONE|ONE_TO_ONE","confidence":0.0,"reason":""}]}
                                    """),
                            Map.of("role", "user", "content", prompt)
                    )
            );
            String response = restClient.post()
                    .uri(normalizeBaseUrl(deepSeekBaseUrl) + "/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + deepSeekApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            log.info("DeepSeek relation analysis response received: apiModel={}, responseChars={}",
                    modelName, response == null ? 0 : response.length());
            emitProgress(emitter, "DeepSeek 已返回结果，正在解析候选关系。");
            List<CandidateRelation> relations = parseModelRelations(response, tables);
            log.info("DeepSeek relation analysis parsed: apiModel={}, relationCount={}", modelName, relations.size());
            emitProgress(emitter, "AI 返回结果解析完成，得到 " + relations.size() + " 条候选关系。");
            return relations;
        } catch (RestClientResponseException ex) {
            log.error("DeepSeek relation analysis HTTP error: status={}, responseBody={}",
                    ex.getStatusCode(), abbreviate(ex.getResponseBodyAsString(), 2000), ex);
            emitProgress(emitter, "DeepSeek 调用失败：" + ex.getStatusCode() + "，已停止分析。");
            throw new ResponseStatusException(BAD_REQUEST, "DeepSeek 调用失败：" + ex.getStatusCode(), ex);
        } catch (RuntimeException ex) {
            log.error("DeepSeek relation analysis failed: logicalModel={}, mainTable={}",
                    model, mainTable.name(), ex);
            emitProgress(emitter, "DeepSeek 调用异常，已停止分析。");
            throw ex;
        }
    }

    private List<CandidateRelation> analyzeWithDeepSeekStream(String model, TableMetadata mainTable, List<TableMetadata> tables, SseEmitter emitter) {
        String modelName = deepSeekModelName(model);
        String prompt = buildPrompt(mainTable, tables);
        emitProgress(emitter, "正在整理表结构上下文，提示词长度 " + prompt.length() + " 字符。");
        emitProgress(emitter, "正在流式调用 DeepSeek 模型 " + modelName + "。");
        log.info("Calling DeepSeek streaming relation analysis: logicalModel={}, apiModel={}, baseUrl={}, mainTable={}, tableCount={}, prompt={}",
                model, modelName, normalizeBaseUrl(deepSeekBaseUrl), mainTable.name(), tables.size(), prompt);

        try {
            Map<String, Object> body = Map.of(
                    "model", modelName,
                    "temperature", 0.1,
                    "stream", true,
                    "messages", List.of(
                            Map.of("role", "system", "content", """
                                    你是数据库建模助手。请只返回 NDJSON，不要 Markdown，不要代码块，不要解释性正文。
                                    从主表开始逐层识别表关系，每识别到一个有向关系，立刻输出一行独立 JSON。
                                    sourceTable 必须是主表或已经发现的上层表，targetTable 必须是新发现的下层表。
                                    整体结果必须是以主表为根节点的有向无环图，不要输出会回指祖先、形成环或与主表不连通的关系。
                                    每行 JSON 格式：{"sourceTable":"","sourceColumn":"","targetTable":"","targetColumn":"","relationType":"ONE_TO_MANY|MANY_TO_ONE|ONE_TO_ONE","confidence":0.0,"reason":""}
                                    如果没有关系，输出空内容。
                                    """),
                            Map.of("role", "user", "content", prompt)
                    )
            );
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(deepSeekBaseUrl) + "/chat/completions"))
                    .timeout(Duration.ofMinutes(3))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + deepSeekApiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<java.io.InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                String responseBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                log.error("DeepSeek streaming relation analysis HTTP error: status={}, responseBody={}",
                        response.statusCode(), abbreviate(responseBody, 2000));
                emitProgress(emitter, "DeepSeek 流式调用失败：" + response.statusCode() + "，已停止分析。");
                throw new ResponseStatusException(BAD_REQUEST, "DeepSeek 流式调用失败：" + response.statusCode());
            }

            Set<String> tableNames = tables.stream().map(TableMetadata::name).collect(Collectors.toSet());
            Set<String> seen = new LinkedHashSet<>();
            Set<String> reached = new LinkedHashSet<>();
            reached.add(mainTable.name());
            List<CandidateRelation> pending = new ArrayList<>();
            List<CandidateRelation> relations = new ArrayList<>();
            StringBuilder content = new StringBuilder();
            StringBuilder relationLine = new StringBuilder();
            boolean announcedOutput = false;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    String delta = readStreamingContentDelta(data);
                    if (!hasText(delta)) {
                        continue;
                    }
                    if (!announcedOutput) {
                        emitProgress(emitter, "模型开始输出候选关系，识别到一条会立即展示。");
                        announcedOutput = true;
                    }
                    content.append(delta);
                    relationLine.append(delta);
                    drainCompletedRelationLines(relationLine, tableNames, reached, seen, pending, relations, emitter);
                }
            }
            drainRemainingRelationLine(relationLine, tableNames, reached, seen, pending, relations, emitter);
            for (CandidateRelation relation : parseContentRelations(content.toString(), tables)) {
                acceptDagCandidate(relation, reached, seen, pending, relations, emitter);
            }
            if (!pending.isEmpty()) {
                emitProgress(emitter, "已跳过 " + pending.size() + " 条会形成环或暂未连接到主表的候选关系。");
            }
            emitProgress(emitter, "DeepSeek 流式输出结束，累计得到 " + relations.size() + " 条候选关系。");
            log.info("DeepSeek streaming relation analysis completed: apiModel={}, relationCount={}, contentChars={}",
                    modelName, relations.size(), content.length());
            return relations;
        } catch (Exception ex) {
            log.error("DeepSeek streaming relation analysis failed: logicalModel={}, mainTable={}",
                    model, mainTable.name(), ex);
            emitProgress(emitter, "DeepSeek 流式调用异常，已停止分析。");
            throw new ResponseStatusException(BAD_REQUEST, "DeepSeek 流式调用异常：" + errorMessage(ex), ex);
        }
    }

    private String deepSeekModelName(String model) {
        return Objects.equals(model, "deepseek-v4-flash") ? deepSeekFlashModel : deepSeekProModel;
    }

    private String buildPrompt(TableMetadata mainTable, List<TableMetadata> tables) {
        List<TableMetadata> compactTables = tables.stream()
                .limit(80)
                .toList();
        return "主表：" + mainTable.name() + "\n已采集元数据：\n" + compactTables.stream()
                .map(table -> table.name() + "(" + nullToBlank(table.comment()) + "): " + table.columns().stream()
                        .map(column -> column.name() + " " + column.type()
                                + (column.primaryKey() ? " PK" : "")
                                + (column.foreignKey() ? " FK" : "")
                                + (hasText(column.comment()) ? " " + column.comment() : ""))
                        .collect(Collectors.joining("; ")))
                .collect(Collectors.joining("\n"));
    }

    private List<CandidateRelation> parseModelRelations(String response, List<TableMetadata> tables) {
        if (!hasText(response)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            log.debug("DeepSeek relation analysis message content: {}", abbreviate(content, 3000));
            return parseContentRelations(content, tables);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse DeepSeek relation analysis response: response={}", abbreviate(response, 3000), ex);
            return List.of();
        }
    }

    private List<CandidateRelation> parseContentRelations(String content, List<TableMetadata> tables) {
        Set<String> tableNames = tables.stream().map(TableMetadata::name).collect(Collectors.toSet());
        List<CandidateRelation> parsed = new ArrayList<>();
        for (String line : content.split("\\R")) {
            parseCandidateRelationLine(line, tableNames).ifPresent(parsed::add);
        }
        if (!parsed.isEmpty()) {
            return dedupe(parsed);
        }
        try {
            JsonNode result = objectMapper.readTree(extractJson(content));
            JsonNode relations = result.path("relations");
            if (!relations.isArray()) {
                return List.of();
            }
            for (JsonNode node : relations) {
                candidateFromNode(node, tableNames).ifPresent(parsed::add);
            }
            return dedupe(parsed);
        } catch (JsonProcessingException ex) {
            log.warn("DeepSeek relation analysis content was not parseable as JSON relations: content={}",
                    abbreviate(content, 2000));
            return List.of();
        }
    }

    private String readStreamingContentDelta(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode delta = root.path("choices").path(0).path("delta");
            String content = delta.path("content").asText("");
            if (hasText(content)) {
                return content;
            }
            return "";
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse DeepSeek streaming chunk: data={}", abbreviate(data, 1000), ex);
            return "";
        }
    }

    private void drainCompletedRelationLines(
            StringBuilder relationLine,
            Set<String> tableNames,
            Set<String> reached,
            Set<String> seen,
            List<CandidateRelation> pending,
            List<CandidateRelation> relations,
            SseEmitter emitter
    ) {
        int newlineIndex;
        while ((newlineIndex = relationLine.indexOf("\n")) >= 0) {
            String line = relationLine.substring(0, newlineIndex);
            relationLine.delete(0, newlineIndex + 1);
            parseCandidateRelationLine(line, tableNames).ifPresent(relation -> {
                acceptDagCandidate(relation, reached, seen, pending, relations, emitter);
            });
        }
    }

    private void drainRemainingRelationLine(
            StringBuilder relationLine,
            Set<String> tableNames,
            Set<String> reached,
            Set<String> seen,
            List<CandidateRelation> pending,
            List<CandidateRelation> relations,
            SseEmitter emitter
    ) {
        parseCandidateRelationLine(relationLine.toString(), tableNames).ifPresent(relation -> {
            acceptDagCandidate(relation, reached, seen, pending, relations, emitter);
        });
        relationLine.setLength(0);
    }

    private Optional<CandidateRelation> parseCandidateRelationLine(String line, Set<String> tableNames) {
        String normalized = line == null ? "" : line.trim();
        if (!normalized.startsWith("{") || !normalized.endsWith("}")) {
            return Optional.empty();
        }
        try {
            return candidateFromNode(objectMapper.readTree(normalized), tableNames);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse AI relation line: line={}", abbreviate(normalized, 1000), ex);
            return Optional.empty();
        }
    }

    private Optional<CandidateRelation> candidateFromNode(JsonNode node, Set<String> tableNames) {
        String sourceTable = node.path("sourceTable").asText("");
        String sourceColumn = node.path("sourceColumn").asText("");
        String targetTable = node.path("targetTable").asText("");
        String targetColumn = node.path("targetColumn").asText("");
        if (!tableNames.contains(sourceTable) || !tableNames.contains(targetTable) || !hasText(sourceColumn) || !hasText(targetColumn)) {
            log.warn("Skipping invalid AI relation candidate: sourceTable={}, sourceColumn={}, targetTable={}, targetColumn={}",
                    sourceTable, sourceColumn, targetTable, targetColumn);
            return Optional.empty();
        }
        return Optional.of(new CandidateRelation(
                sourceTable,
                sourceColumn,
                targetTable,
                targetColumn,
                normalizeRelationType(node.path("relationType").asText("AI_INFERRED")),
                clampConfidence(node.path("confidence").asDouble(0.75)),
                node.path("reason").asText("AI 根据字段命名、类型、键标记和注释识别出的候选关系。")
        ));
    }

    private String extractJson(String content) {
        String trimmed = content.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private List<CandidateRelation> dedupe(List<CandidateRelation> relations) {
        Set<String> seen = new LinkedHashSet<>();
        List<CandidateRelation> result = new ArrayList<>();
        for (CandidateRelation relation : relations) {
            String key = relationKey(relation);
            if (seen.add(key)) {
                result.add(relation);
            }
        }
        return result;
    }

    private List<CandidateRelation> enforceDirectedAcyclicRelations(String rootTable, List<CandidateRelation> candidates) {
        Set<String> reached = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();
        List<CandidateRelation> pending = new ArrayList<>(dedupe(candidates));
        List<CandidateRelation> accepted = new ArrayList<>();
        reached.add(rootTable);

        boolean changed;
        do {
            changed = false;
            for (int index = 0; index < pending.size(); ) {
                CandidateRelation relation = pending.get(index);
                DagCandidate candidate = normalizeDagCandidate(relation, reached, accepted);
                if (candidate.skip()) {
                    pending.remove(index);
                    changed = true;
                    continue;
                }
                if (candidate.relation() == null) {
                    index++;
                    continue;
                }
                CandidateRelation normalized = candidate.relation();
                if (seen.add(relationKey(normalized))) {
                    accepted.add(normalized);
                    reached.add(normalized.targetTable());
                }
                pending.remove(index);
                changed = true;
            }
        } while (changed);

        if (!pending.isEmpty()) {
            log.warn("Ignored non-rooted AI relation candidates: rootTable={}, ignoredCount={}", rootTable, pending.size());
        }
        return accepted;
    }

    private void acceptDagCandidate(
            CandidateRelation relation,
            Set<String> reached,
            Set<String> seen,
            List<CandidateRelation> pending,
            List<CandidateRelation> accepted,
            SseEmitter emitter
    ) {
        pending.add(relation);
        boolean changed;
        do {
            changed = false;
            for (int index = 0; index < pending.size(); ) {
                CandidateRelation pendingRelation = pending.get(index);
                DagCandidate candidate = normalizeDagCandidate(pendingRelation, reached, accepted);
                if (candidate.skip()) {
                    pending.remove(index);
                    changed = true;
                    continue;
                }
                if (candidate.relation() == null) {
                    index++;
                    continue;
                }
                CandidateRelation normalized = candidate.relation();
                if (seen.add(relationKey(normalized))) {
                    accepted.add(normalized);
                    reached.add(normalized.targetTable());
                    emitRelation(emitter, normalized);
                }
                pending.remove(index);
                changed = true;
            }
        } while (changed);
    }

    private DagCandidate normalizeDagCandidate(CandidateRelation relation, Set<String> reached, List<CandidateRelation> accepted) {
        if (relation.sourceTable().equals(relation.targetTable())) {
            return new DagCandidate(null, true);
        }
        boolean sourceReached = reached.contains(relation.sourceTable());
        boolean targetReached = reached.contains(relation.targetTable());
        if (sourceReached && !targetReached) {
            return new DagCandidate(relation, false);
        }
        if (targetReached && !sourceReached) {
            return new DagCandidate(reverseRelation(relation), false);
        }
        if (sourceReached && targetReached) {
            if (!pathExists(relation.targetTable(), relation.sourceTable(), accepted)) {
                return new DagCandidate(relation, false);
            }
            log.info("Skipping cyclic relation candidate: {}.{} -> {}.{}",
                    relation.sourceTable(), relation.sourceColumn(), relation.targetTable(), relation.targetColumn());
            return new DagCandidate(null, true);
        }
        return new DagCandidate(null, false);
    }

    private boolean pathExists(String from, String to, List<CandidateRelation> relations) {
        if (from.equals(to)) {
            return true;
        }
        Set<String> visited = new LinkedHashSet<>();
        List<String> queue = new ArrayList<>();
        queue.add(from);
        visited.add(from);
        while (!queue.isEmpty()) {
            String current = queue.remove(0);
            for (CandidateRelation relation : relations) {
                if (!relation.sourceTable().equals(current) || visited.contains(relation.targetTable())) {
                    continue;
                }
                if (relation.targetTable().equals(to)) {
                    return true;
                }
                visited.add(relation.targetTable());
                queue.add(relation.targetTable());
            }
        }
        return false;
    }

    private CandidateRelation reverseRelation(CandidateRelation relation) {
        return new CandidateRelation(
                relation.targetTable(),
                relation.targetColumn(),
                relation.sourceTable(),
                relation.sourceColumn(),
                reverseRelationType(relation.relationType()),
                relation.confidence(),
                relation.reason()
        );
    }

    private String reverseRelationType(String relationType) {
        return switch (normalizeRelationType(relationType)) {
            case "ONE_TO_MANY" -> "MANY_TO_ONE";
            case "MANY_TO_ONE" -> "ONE_TO_MANY";
            default -> normalizeRelationType(relationType);
        };
    }

    private String relationKey(CandidateRelation relation) {
        return relation.sourceTable() + "." + relation.sourceColumn() + "->" + relation.targetTable() + "." + relation.targetColumn();
    }

    private List<TableMetadata> loadMetadata(String dataSourceId) {
        log.debug("Loading table metadata: dataSourceId={}", dataSourceId);
        List<TableRow> rows = jdbcTemplate.query("""
                        select id, table_name, table_comment
                        from table_metadata
                        where data_source_id = ?
                        order by table_name
                        """,
                (rs, rowNum) -> new TableRow(
                        rs.getString("id"),
                        rs.getString("table_name"),
                        nullToBlank(rs.getString("table_comment"))
                ),
                dataSourceId
        );
        return rows.stream()
                .map(row -> new TableMetadata(row.name(), row.comment(), loadColumns(row.id())))
                .toList();
    }

    private List<ColumnMetadata> loadColumns(String tableMetadataId) {
        log.debug("Loading column metadata: tableMetadataId={}", tableMetadataId);
        return jdbcTemplate.query("""
                        select column_name, data_type, column_comment,
                               coalesce(primary_key_flag, false) as primary_key_flag,
                               coalesce(foreign_key_flag, false) as foreign_key_flag
                        from column_metadata
                        where table_metadata_id = ?
                        order by ordinal_position, column_name
                        """,
                (rs, rowNum) -> new ColumnMetadata(
                        rs.getString("column_name"),
                        nullToBlank(rs.getString("data_type")),
                        rs.getBoolean("primary_key_flag"),
                        rs.getBoolean("foreign_key_flag"),
                        nullToBlank(rs.getString("column_comment"))
                ),
                tableMetadataId
        );
    }

    private String normalizeRelationType(String value) {
        if (!hasText(value)) {
            return "AI_INFERRED";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ONE_TO_MANY", "MANY_TO_ONE", "ONE_TO_ONE", "MANY_TO_MANY" -> normalized;
            default -> "AI_INFERRED";
        };
    }

    private static double clampConfidence(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeBaseUrl(String value) {
        String baseUrl = Optional.ofNullable(value).orElse("https://api.deepseek.com").trim();
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private void emitProgress(SseEmitter emitter, String message) {
        if (emitter == null) {
            return;
        }
        try {
            log.debug("AI relation analysis progress: {}", message);
            emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(new StreamProgress(message, System.currentTimeMillis()), MediaType.APPLICATION_JSON));
        } catch (Exception ex) {
            throw new IllegalStateException("发送 AI 分析进度失败", ex);
        }
    }

    private void emitRelation(SseEmitter emitter, CandidateRelation relation) {
        if (emitter == null) {
            return;
        }
        try {
            log.info("AI relation analysis emitted relation: {}.{} -> {}.{}",
                    relation.sourceTable(), relation.sourceColumn(), relation.targetTable(), relation.targetColumn());
            emitter.send(SseEmitter.event()
                    .name("relation")
                    .data(relation, MediaType.APPLICATION_JSON));
            emitProgress(emitter, "识别到关系：" + relation.sourceTable() + "." + relation.sourceColumn()
                    + " -> " + relation.targetTable() + "." + relation.targetColumn());
        } catch (Exception ex) {
            throw new IllegalStateException("发送 AI 候选关系失败", ex);
        }
    }

    private static String errorMessage(Exception ex) {
        if (ex instanceof ResponseStatusException responseStatusException) {
            return responseStatusException.getReason();
        }
        return hasText(ex.getMessage()) ? ex.getMessage() : "AI 分析失败，请查看后端日志";
    }

    public record AnalyzeRelationsRequest(
            @NotBlank String dataSourceId,
            @NotBlank String mainTable,
            String model
    ) {
    }

    public record AnalyzeRelationsResponse(String model, List<CandidateRelation> relations) {
    }

    public record CandidateRelation(
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            String relationType,
            double confidence,
            String reason
    ) {
    }

    public record AiModelsResponse(String defaultModel, List<AiModelOption> models) {
    }

    public record AiModelOption(String id, String name, String provider, boolean enabled, String description) {
    }

    public record StreamProgress(String message, long timestamp) {
    }

    private record TableMetadata(String name, String comment, List<ColumnMetadata> columns) {
    }

    private record ColumnMetadata(String name, String type, boolean primaryKey, boolean foreignKey, String comment) {
    }

    private record TableRow(String id, String name, String comment) {
    }

    private record DagCandidate(CandidateRelation relation, boolean skip) {
    }
}
