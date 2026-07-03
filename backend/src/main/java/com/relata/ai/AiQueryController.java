package com.relata.ai;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Duration;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ai-query")
public class AiQueryController {

    private static final Logger log = LoggerFactory.getLogger(AiQueryController.class);
    private static final Pattern TABLE_REFERENCE = Pattern.compile("(?i)\\b(?:from|join)\\s+([`\"\\[]?)([a-zA-Z_][\\w$]*)(?:[`\"\\]]?)");
    private static final int DEFAULT_RESULT_LIMIT = 100;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final HttpClient httpClient;
    private final String defaultModel;
    private final String deepSeekBaseUrl;
    private final String deepSeekApiKey;
    private final String deepSeekFlashModel;
    private final String deepSeekProModel;

    public AiQueryController(
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
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(20));
        requestFactory.setReadTimeout(Duration.ofMinutes(2));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.defaultModel = defaultModel;
        this.deepSeekBaseUrl = deepSeekBaseUrl;
        this.deepSeekApiKey = deepSeekApiKey;
        this.deepSeekFlashModel = deepSeekFlashModel;
        this.deepSeekProModel = deepSeekProModel;
    }

    @GetMapping("/models/{modelId}/prompts")
    public List<ModelPrompt> prompts(@PathVariable String modelId) {
        findRelationModel(modelId);
        ensureDefaultPrompts(modelId);
        return loadPrompts(modelId);
    }

    @PostMapping("/models/{modelId}/prompts")
    @ResponseStatus(HttpStatus.CREATED)
    public ModelPrompt createPrompt(@PathVariable String modelId, @Valid @RequestBody PromptRequest request) {
        findRelationModel(modelId);
        String id = UUID.randomUUID().toString();
        int sortOrder = nextPromptSortOrder(modelId);
        jdbcTemplate.update("""
                        insert into ai_model_prompt (
                            id, relation_model_id, content, sort_order, created_at, updated_at
                        ) values (?, ?, ?, ?, current_timestamp, current_timestamp)
                        """,
                id,
                modelId,
                request.content().trim(),
                sortOrder
        );
        return findPrompt(modelId, id);
    }

    @PutMapping("/models/{modelId}/prompts/{promptId}")
    public ModelPrompt updatePrompt(
            @PathVariable String modelId,
            @PathVariable String promptId,
            @Valid @RequestBody PromptRequest request
    ) {
        findPrompt(modelId, promptId);
        jdbcTemplate.update("""
                        update ai_model_prompt
                        set content = ?, updated_at = current_timestamp
                        where id = ? and relation_model_id = ?
                        """,
                request.content().trim(),
                promptId,
                modelId
        );
        return findPrompt(modelId, promptId);
    }

    @DeleteMapping("/models/{modelId}/prompts/{promptId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePrompt(@PathVariable String modelId, @PathVariable String promptId) {
        findPrompt(modelId, promptId);
        jdbcTemplate.update("delete from ai_model_prompt where id = ? and relation_model_id = ?", promptId, modelId);
    }

    @PostMapping("/models/{modelId}/prompts/reset")
    public List<ModelPrompt> resetPrompts(@PathVariable String modelId) {
        findRelationModel(modelId);
        jdbcTemplate.update("delete from ai_model_prompt where relation_model_id = ?", modelId);
        insertDefaultPrompts(modelId);
        return loadPrompts(modelId);
    }

    @PostMapping("/models/{modelId}/summary")
    public ModelSummaryResponse summarizeModel(@PathVariable String modelId, @RequestBody(required = false) SummaryRequest request) {
        RelationModel model = findRelationModel(modelId);
        ModelContext context = loadModelContext(model);
        List<ModelPrompt> prompts = loadPromptsOrDefaults(modelId);
        ModelSummaryResponse summary = generateModelSummary(context, prompts, request == null ? "" : request.currentSummary());
        saveModelSummary(modelId, summary.summary(), summary.provider());
        return summary;
    }

    @GetMapping("/models/{modelId}/summary")
    public ModelSummaryResponse modelSummary(@PathVariable String modelId) {
        RelationModel model = findRelationModel(modelId);
        return loadModelSummary(modelId)
                .orElseGet(() -> {
                    ModelContext context = loadModelContext(model);
                    ModelSummaryResponse fallback = new ModelSummaryResponse(buildLocalModelSummary(context), "LOCAL");
                    saveModelSummary(modelId, fallback.summary(), fallback.provider());
                    return fallback;
                });
    }

    @PostMapping(value = "/models/{modelId}/summary/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter summarizeModelStream(@PathVariable String modelId, @RequestBody(required = false) SummaryRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);
        Thread worker = new Thread(() -> runSummaryStream(modelId, request, emitter), "ai-query-model-summary-stream");
        worker.start();
        return emitter;
    }

    @PostMapping("/chat")
    public AiQueryChatResponse chat(@Valid @RequestBody AiQueryChatRequest request) {
        RelationModel model = findRelationModel(request.modelId());
        ModelContext context = loadModelContext(model);
        List<ModelPrompt> prompts = loadPromptsOrDefaults(model.id());
        SqlPlan plan = deepSeekAvailable()
                ? planWithDeepSeek(request, context, prompts)
                : localPlan(request, context);
        String safeSql = "";
        QueryExecution execution = QueryExecution.empty();
        if (plan.requiresQuery()) {
            safeSql = validateAndLimitSql(plan.sql(), context);
            execution = executeSql(model.dataSourceId(), safeSql, safeLimit(request.limit()));
        }
        String answer = deepSeekAvailable()
                ? answerWithDeepSeek(request.question(), context, prompts, plan, execution)
                : localAnswer(request.question(), plan, execution);
        saveChatMessage(request.sessionId(), model, request.question(), answer, safeSql);
        return new AiQueryChatResponse(
                answer,
                plan.steps(),
                safeSql,
                execution.columns(),
                execution.rows(),
                execution.rowCount(),
                plan.entities(),
                plan.notes(),
                prompts
        );
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody AiQueryChatRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);
        Thread worker = new Thread(() -> runChatStream(request, emitter), "ai-query-chat-stream");
        worker.start();
        return emitter;
    }

    private void runChatStream(AiQueryChatRequest request, SseEmitter emitter) {
        String safeSql = "";
        StringBuilder answer = new StringBuilder();
        try {
            emitSse(emitter, "step", new ChatStep("读取模型上下文", "正在加载关系模型、实体、字段和模型提示词。"));
            RelationModel model = findRelationModel(request.modelId());
            ModelContext context = loadModelContext(model);
            List<ModelPrompt> prompts = loadPromptsOrDefaults(model.id());

            emitSse(emitter, "step", new ChatStep("理解用户问题", "正在结合模型提示词识别业务实体和查询意图。"));
            SqlPlan plan = deepSeekAvailable()
                    ? planWithDeepSeek(request, context, prompts)
                    : localPlan(request, context);
            for (int index = 0; index < plan.steps().size(); index++) {
                emitSse(emitter, "step", new ChatStep("查询步骤 " + (index + 1), plan.steps().get(index)));
            }

            QueryExecution execution = QueryExecution.empty();
            if (plan.requiresQuery()) {
                safeSql = validateAndLimitSql(plan.sql(), context);
                emitSse(emitter, "sql", new ChatSql(safeSql));

                emitSse(emitter, "step", new ChatStep("执行 SQL", "正在执行只读 SQL 并读取查询结果。"));
                execution = executeSql(model.dataSourceId(), safeSql, safeLimit(request.limit()));
                emitSse(emitter, "result", new ChatExecutionResult(execution.columns(), execution.rows(), execution.rowCount()));
            } else {
                emitSse(emitter, "step", new ChatStep("无需查询数据", "已识别为模型解释或规则说明类问题，将直接基于关系模型回答。"));
            }

            emitSse(emitter, "step", new ChatStep("生成结论", plan.requiresQuery() ? "正在根据 SQL 执行结果回答用户问题。" : "正在根据模型上下文回答用户问题。"));
            if (deepSeekAvailable()) {
                streamAnswerWithDeepSeek(request.question(), context, prompts, plan, execution, delta -> {
                    answer.append(delta);
                    emitSse(emitter, "answer-delta", delta);
                });
            }
            if (!hasText(answer.toString())) {
                String fallback = localAnswer(request.question(), plan, execution);
                answer.append(fallback);
                emitSse(emitter, "answer-delta", fallback);
            }

            saveChatMessage(request.sessionId(), model, request.question(), answer.toString(), safeSql);
            emitSse(emitter, "complete", new AiQueryChatResponse(
                    answer.toString(),
                    plan.steps(),
                    safeSql,
                    execution.columns(),
                    execution.rows(),
                    execution.rowCount(),
                    plan.entities(),
                    plan.notes(),
                    prompts
            ));
            emitter.complete();
        } catch (Exception ex) {
            log.error("Streaming AI query chat failed: modelId={}, question={}, sql={}",
                    request.modelId(), request.question(), safeSql, ex);
            try {
                emitSse(emitter, "error", ex.getMessage());
            } finally {
                emitter.complete();
            }
        }
    }

    private SqlPlan planWithDeepSeek(AiQueryChatRequest request, ModelContext context, List<ModelPrompt> prompts) {
        String prompt = """
                请根据关系模型、模型提示词和用户问题，先判断是否必须查询数据库。
                如果用户是在询问模型含义、实体关系、字段说明、依赖规则、能力边界、如何提问、数据口径解释等，不需要查询数据库，requiresQuery 返回 false，sql 返回空字符串。
                如果用户要求查询具体数据、记录、统计数量、明细、筛选结果、校验某条数据是否存在，则 requiresQuery 返回 true，并生成一条只读 SQL。
                SQL 只能查询模型内出现的表，必须是单条 SELECT，必须添加合理过滤条件和 limit。
                返回严格 JSON，不要 Markdown，不要代码块。
                JSON 格式：
                {"requiresQuery":true,"steps":[""],"entities":[""],"sql":"","notes":[""]}
                用户问题：%s
                模型提示词：
                %s
                模型上下文：
                %s
                """.formatted(
                request.question(),
                prompts.stream().map(ModelPrompt::content).collect(Collectors.joining("\n")),
                compactContext(context)
        );
        String content = callDeepSeekText(defaultModel, "你是严谨的数据库查询规划助手，只输出 JSON。", prompt);
        try {
            JsonNode root = objectMapper.readTree(extractJson(content));
            boolean requiresQuery = root.path("requiresQuery").asBoolean(hasQueryIntent(request.question()));
            List<String> steps = stringArray(root.path("steps"));
            List<String> entities = stringArray(root.path("entities"));
            List<String> notes = stringArray(root.path("notes"));
            String sql = root.path("sql").asText("");
            if (steps.isEmpty()) {
                throw new IllegalArgumentException("AI plan missing steps");
            }
            if (requiresQuery && !hasText(sql)) {
                throw new IllegalArgumentException("AI query plan missing sql");
            }
            return new SqlPlan(requiresQuery, steps, entities, sql, notes);
        } catch (Exception ex) {
            log.warn("AI query plan parse failed, fallback to local plan: content={}", abbreviate(content, 2000), ex);
            return localPlan(request, context);
        }
    }

    private ModelSummaryResponse generateModelSummary(ModelContext context, List<ModelPrompt> prompts, String currentSummary) {
        String fallback = buildLocalModelSummary(context);
        if (!deepSeekAvailable()) {
            return new ModelSummaryResponse(fallback, "LOCAL");
        }
        String prompt = buildSummaryPrompt(context, prompts, currentSummary);
        String content = callDeepSeekText(defaultModel, "你是数据关系模型解读助手。", prompt);
        return new ModelSummaryResponse(hasText(content) ? content.trim() : fallback, "DEEPSEEK");
    }

    private String buildSummaryPrompt(ModelContext context, List<ModelPrompt> prompts, String currentSummary) {
        return """
                请基于关系模型上下文，用中文生成一段适合产品界面展示的“模型理解”说明。
                要说明核心实体、主要关系、可支持的问题类型、需要注意的数据依赖风险。
                不要 Markdown，不要列表，控制在 180 字以内。
                用户补充说明：%s
                模型提示词：
                %s
                模型上下文：
                %s
                """.formatted(
                nullToBlank(currentSummary),
                prompts.stream().map(ModelPrompt::content).collect(Collectors.joining("\n")),
                compactContext(context)
        );
    }

    private void runSummaryStream(String modelId, SummaryRequest request, SseEmitter emitter) {
        StringBuilder summary = new StringBuilder();
        try {
            RelationModel model = findRelationModel(modelId);
            ModelContext context = loadModelContext(model);
            List<ModelPrompt> prompts = loadPromptsOrDefaults(modelId);
            if (!deepSeekAvailable()) {
                String fallback = buildLocalModelSummary(context);
                emitSse(emitter, "delta", fallback);
                saveModelSummary(modelId, fallback, "LOCAL");
                emitSse(emitter, "complete", new ModelSummaryResponse(fallback, "LOCAL"));
                emitter.complete();
                return;
            }
            String prompt = buildSummaryPrompt(context, prompts, request == null ? "" : request.currentSummary());
            streamDeepSeekText(
                    defaultModel,
                    "你是数据关系模型解读助手。请直接输出模型理解说明正文，不要 Markdown。",
                    prompt,
                    delta -> {
                        summary.append(delta);
                        emitSse(emitter, "delta", delta);
                    }
            );
            String content = summary.toString().trim();
            if (!hasText(content)) {
                content = buildLocalModelSummary(context);
                emitSse(emitter, "delta", content);
            }
            saveModelSummary(modelId, content, "DEEPSEEK");
            emitSse(emitter, "complete", new ModelSummaryResponse(content, "DEEPSEEK"));
            emitter.complete();
        } catch (Exception ex) {
            log.error("Streaming model summary failed: modelId={}", modelId, ex);
            try {
                emitSse(emitter, "error", ex.getMessage());
            } finally {
                emitter.complete();
            }
        }
    }

    private SqlPlan localPlan(AiQueryChatRequest request, ModelContext context) {
        TableNode main = pickMainTable(request.question(), context);
        if (!hasQueryIntent(request.question())) {
            List<String> steps = new ArrayList<>();
            steps.add("识别为模型理解类问题，不需要查询数据库。");
            steps.add("根据关系模型实体、字段、关系和模型提示词组织回答。");
            return new SqlPlan(false, steps, List.of(main.tableName()), "", List.of("本地规划：该问题不需要执行 SQL。"));
        }
        String keyColumn = pickKeyColumn(main);
        String keyValue = extractLikelyKeyValue(request.question());
        List<String> steps = new ArrayList<>();
        steps.add("识别用户问题中的核心实体：" + main.tableName());
        steps.add("使用字段 " + keyColumn + " 作为查询入口。");
        steps.add("按关系模型补充关联表，查询结果用于回答用户问题。");
        String sql = "select * from " + main.tableName()
                + " where " + keyColumn + " = " + sqlLiteral(keyValue)
                + " limit " + DEFAULT_RESULT_LIMIT;
        return new SqlPlan(true, steps, List.of(main.tableName()), sql, List.of("本地规划：未配置可用 AI 模型时生成单实体查询。"));
    }

    private String answerWithDeepSeek(
            String question,
            ModelContext context,
            List<ModelPrompt> prompts,
            SqlPlan plan,
            QueryExecution execution
    ) {
        String prompt = buildAnswerPrompt(question, context, prompts, plan, execution);
        String answer = callDeepSeekText(defaultModel, "你是数据问答助手。", prompt);
        return hasText(answer) ? answer.trim() : localAnswer(question, plan, execution);
    }

    private void streamAnswerWithDeepSeek(
            String question,
            ModelContext context,
            List<ModelPrompt> prompts,
            SqlPlan plan,
            QueryExecution execution,
            Consumer<String> onDelta
    ) {
        String prompt = buildAnswerPrompt(question, context, prompts, plan, execution);
        streamDeepSeekText(defaultModel, "你是数据问答助手。", prompt, onDelta);
    }

    private String buildAnswerPrompt(
            String question,
            ModelContext context,
            List<ModelPrompt> prompts,
            SqlPlan plan,
            QueryExecution execution
    ) {
        if (!plan.requiresQuery()) {
            return """
                    用户的问题不需要查询数据库。请基于关系模型、字段、关系和模型提示词直接回答。
                    回答要贴合当前模型，不要编造模型外的表、字段或数据记录。
                    可以使用简洁 Markdown，但不要说已经执行 SQL。
                    用户问题：%s
                    理解步骤：%s
                    识别实体：%s
                    模型提示词：%s
                    模型上下文摘要：%s
                    """.formatted(
                    question,
                    String.join("；", plan.steps()),
                    plan.entities(),
                    prompts.stream().map(ModelPrompt::content).collect(Collectors.joining("\n")),
                    compactContext(context)
            );
        }
        return """
                请基于 SQL 查询结果回答用户问题。回答必须忠于结果，不要编造不存在的数据。
                如果结果为空，要说明未查询到数据，并指出 SQL 和查询步骤。
                用户问题：%s
                查询步骤：%s
                SQL：%s
                模型提示词：%s
                结果列：%s
                结果数据（最多前 20 行）：%s
                模型上下文摘要：%s
                """.formatted(
                question,
                String.join("；", plan.steps()),
                plan.sql(),
                prompts.stream().map(ModelPrompt::content).collect(Collectors.joining("\n")),
                execution.columns(),
                toJson(execution.rows().stream().limit(20).toList()),
                compactContext(context)
        );
    }

    private String localAnswer(String question, SqlPlan plan, QueryExecution execution) {
        if (!plan.requiresQuery()) {
            return "这个问题不需要查询数据库。根据当前关系模型，可以围绕 "
                    + String.join("、", plan.entities())
                    + " 等实体解释模型关系、字段含义、依赖路径和可支持的问题类型。";
        }
        if (execution.rowCount() == 0) {
            return "没有查询到匹配数据。已执行 SQL：" + plan.sql();
        }
        return "已根据问题「" + question + "」执行查询，命中 " + execution.rowCount()
                + " 行数据。查询步骤：" + String.join("；", plan.steps());
    }

    private QueryExecution executeSql(String dataSourceId, String sql, int limit) {
        DataSourceRecord dataSource = findDataSource(dataSourceId);
        try (Connection connection = DriverManager.getConnection(dataSource.jdbcUrl(), dataSource.username(), dataSource.password())) {
            JdbcTemplate sourceJdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            sourceJdbcTemplate.setQueryTimeout(30);
            List<Map<String, Object>> rows = sourceJdbcTemplate.query(sql, (rs, rowNum) -> {
                ResultSetMetaData metaData = rs.getMetaData();
                Map<String, Object> row = new LinkedHashMap<>();
                for (int index = 1; index <= metaData.getColumnCount(); index++) {
                    row.put(metaData.getColumnLabel(index), rs.getObject(index));
                }
                return row;
            });
            List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());
            return new QueryExecution(columns, rows.stream().limit(limit).toList(), rows.size());
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 执行失败：" + ex.getMostSpecificCause().getMessage(), ex);
        } catch (SQLException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 执行失败：" + ex.getMessage(), ex);
        }
    }

    private String validateAndLimitSql(String sql, ModelContext context) {
        if (!hasText(sql)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI 未生成 SQL");
        }
        String cleaned = sql.trim().replaceAll("(?is)^```sql\\s*", "").replaceAll("(?is)^```\\s*", "").replaceAll("(?is)```$", "").trim();
        if (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        if (cleaned.contains(";")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只允许执行单条 SELECT SQL");
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("select") || lower.matches("(?s).*(\\binsert\\b|\\bupdate\\b|\\bdelete\\b|\\bdrop\\b|\\balter\\b|\\btruncate\\b|\\bmerge\\b|\\bcall\\b).*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只允许执行只读 SELECT SQL");
        }
        try {
            Statement statement = CCJSqlParserUtil.parse(cleaned);
            if (!(statement instanceof Select)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只允许执行 SELECT SQL");
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 解析失败：" + ex.getMessage(), ex);
        }
        Set<String> allowedTables = context.nodes().stream()
                .map(TableNode::tableName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> referencedTables = referencedTables(cleaned);
        if (referencedTables.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 未识别到查询表");
        }
        for (String table : referencedTables) {
            if (!allowedTables.contains(table.toLowerCase(Locale.ROOT))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 查询表不在当前关系模型中：" + table);
            }
        }
        if (!lower.matches("(?s).*\\blimit\\s+\\d+.*")) {
            return cleaned + " limit " + DEFAULT_RESULT_LIMIT;
        }
        return cleaned;
    }

    private Set<String> referencedTables(String sql) {
        Set<String> tables = new LinkedHashSet<>();
        Matcher matcher = TABLE_REFERENCE.matcher(sql);
        while (matcher.find()) {
            tables.add(matcher.group(2));
        }
        return tables;
    }

    private RelationModel findRelationModel(String id) {
        return jdbcTemplate.query("""
                        select m.id, m.name, m.data_source_id, coalesce(d.name, '-') as data_source_name,
                               coalesce(m.description, '') as description
                        from relation_model m
                        left join data_source d on d.id = m.data_source_id
                        where m.id = ?
                        """,
                (rs, rowNum) -> new RelationModel(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("data_source_id"),
                        rs.getString("data_source_name"),
                        rs.getString("description")
                ),
                id
        ).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Relation model not found: " + id));
    }

    private ModelContext loadModelContext(RelationModel model) {
        List<TableNode> nodes = jdbcTemplate.query("""
                        select n.table_name, coalesce(t.table_comment, '') as table_comment
                        from relation_model_node n
                        left join table_metadata t on t.data_source_id = ? and t.table_name = n.table_name
                        where n.relation_model_id = ?
                        order by n.created_at, n.table_name
                        """,
                (rs, rowNum) -> new TableNode(
                        rs.getString("table_name"),
                        rs.getString("table_comment"),
                        loadColumns(model.dataSourceId(), rs.getString("table_name"))
                ),
                model.dataSourceId(),
                model.id()
        );
        List<RelationEdge> edges = jdbcTemplate.query("""
                        select source_table, source_column, target_table, target_column,
                               relation_type, coalesce(confidence, 1) as confidence
                        from relation_model_edge
                        where relation_model_id = ? and coalesce(enabled_flag, true) = true
                        order by created_at
                        """,
                (rs, rowNum) -> new RelationEdge(
                        rs.getString("source_table"),
                        rs.getString("source_column"),
                        rs.getString("target_table"),
                        rs.getString("target_column"),
                        rs.getString("relation_type"),
                        rs.getDouble("confidence")
                ),
                model.id()
        );
        if (nodes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前关系模型尚未添加实体");
        }
        return new ModelContext(model, nodes, edges);
    }

    private List<ColumnItem> loadColumns(String dataSourceId, String tableName) {
        return jdbcTemplate.query("""
                        select c.column_name, c.data_type, coalesce(c.column_comment, '') as column_comment,
                               coalesce(c.primary_key_flag, false) as primary_key_flag,
                               coalesce(c.foreign_key_flag, false) as foreign_key_flag
                        from table_metadata t
                        join column_metadata c on c.table_metadata_id = t.id
                        where t.data_source_id = ? and t.table_name = ?
                        order by c.ordinal_position, c.column_name
                        """,
                (rs, rowNum) -> new ColumnItem(
                        rs.getString("column_name"),
                        rs.getString("data_type"),
                        rs.getString("column_comment"),
                        rs.getBoolean("primary_key_flag"),
                        rs.getBoolean("foreign_key_flag")
                ),
                dataSourceId,
                tableName
        );
    }

    private DataSourceRecord findDataSource(String id) {
        return jdbcTemplate.query("""
                        select jdbc_url, username, coalesce(password_cipher, '') as password_cipher
                        from data_source
                        where id = ?
                        """,
                (rs, rowNum) -> new DataSourceRecord(
                        rs.getString("jdbc_url"),
                        rs.getString("username"),
                        rs.getString("password_cipher")
                ),
                id
        ).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Data source not found: " + id));
    }

    private List<ModelPrompt> loadPromptsOrDefaults(String modelId) {
        ensureDefaultPrompts(modelId);
        return loadPrompts(modelId);
    }

    private void ensureDefaultPrompts(String modelId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from ai_model_prompt where relation_model_id = ?", Integer.class, modelId);
        if (count == null || count == 0) {
            insertDefaultPrompts(modelId);
        }
    }

    private void insertDefaultPrompts(String modelId) {
        List<String> defaults = List.of(
                "依赖关系优先使用主键和外键字段，字段名相同但语义不一致时需要标记为待确认。",
                "一对多关系应从主实体主键指向明细实体外键，不能反向作为主查询路径。",
                "跨实体查询需要保留关系路径，便于检查每一步依赖是否可追溯。"
        );
        for (int index = 0; index < defaults.size(); index++) {
            jdbcTemplate.update("""
                            insert into ai_model_prompt (
                                id, relation_model_id, content, sort_order, created_at, updated_at
                            ) values (?, ?, ?, ?, current_timestamp, current_timestamp)
                            """,
                    UUID.randomUUID().toString(),
                    modelId,
                    defaults.get(index),
                    index + 1
            );
        }
    }

    private List<ModelPrompt> loadPrompts(String modelId) {
        return jdbcTemplate.query("""
                        select id, content, coalesce(sort_order, 0) as sort_order
                        from ai_model_prompt
                        where relation_model_id = ?
                        order by sort_order, created_at
                        """,
                (rs, rowNum) -> new ModelPrompt(
                        rs.getString("id"),
                        rs.getString("content"),
                        rs.getInt("sort_order")
                ),
                modelId
        );
    }

    private ModelPrompt findPrompt(String modelId, String promptId) {
        return jdbcTemplate.query("""
                        select id, content, coalesce(sort_order, 0) as sort_order
                        from ai_model_prompt
                        where relation_model_id = ? and id = ?
                        """,
                (rs, rowNum) -> new ModelPrompt(
                        rs.getString("id"),
                        rs.getString("content"),
                        rs.getInt("sort_order")
                ),
                modelId,
                promptId
        ).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Model prompt not found: " + promptId));
    }

    private int nextPromptSortOrder(String modelId) {
        Integer max = jdbcTemplate.queryForObject(
                "select coalesce(max(sort_order), 0) from ai_model_prompt where relation_model_id = ?",
                Integer.class,
                modelId
        );
        return (max == null ? 0 : max) + 1;
    }

    private String callDeepSeekText(String logicalModel, String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> body = Map.of(
                    "model", deepSeekModelName(logicalModel),
                    "temperature", 0.1,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );
            String response = restClient.post()
                    .uri(normalizeBaseUrl(deepSeekBaseUrl) + "/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + deepSeekApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (RestClientResponseException ex) {
            log.error("DeepSeek AI query HTTP error: status={}, body={}", ex.getStatusCode(), abbreviate(ex.getResponseBodyAsString(), 2000), ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DeepSeek 调用失败：" + ex.getStatusCode(), ex);
        } catch (JsonProcessingException ex) {
            log.error("DeepSeek AI query response parse failed", ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DeepSeek 返回解析失败", ex);
        }
    }

    private void streamDeepSeekText(String logicalModel, String systemPrompt, String userPrompt, Consumer<String> onDelta) {
        try {
            Map<String, Object> body = Map.of(
                    "model", deepSeekModelName(logicalModel),
                    "temperature", 0.1,
                    "stream", true,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(deepSeekBaseUrl) + "/chat/completions"))
                    .timeout(Duration.ofMinutes(3))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + deepSeekApiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                String responseBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DeepSeek 流式调用失败：" + response.statusCode() + " " + abbreviate(responseBody, 1000));
            }
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
                    if (hasText(delta)) {
                        onDelta.accept(delta);
                    }
                }
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DeepSeek 流式调用异常：" + ex.getMessage(), ex);
        }
    }

    private String readStreamingContentDelta(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            return root.path("choices").path(0).path("delta").path("content").asText("");
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse DeepSeek streaming chunk: data={}", abbreviate(data, 1000), ex);
            return "";
        }
    }

    private Optional<ModelSummaryResponse> loadModelSummary(String modelId) {
        return jdbcTemplate.query("""
                        select summary, coalesce(provider, 'LOCAL') as provider
                        from ai_model_summary
                        where relation_model_id = ?
                        """,
                (rs, rowNum) -> new ModelSummaryResponse(
                        rs.getString("summary"),
                        rs.getString("provider")
                ),
                modelId
        ).stream().findFirst();
    }

    private void saveModelSummary(String modelId, String summary, String provider) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from ai_model_summary where relation_model_id = ?", Integer.class, modelId);
        if (count == null || count == 0) {
            jdbcTemplate.update("""
                            insert into ai_model_summary (
                                relation_model_id, summary, provider, created_at, updated_at
                            ) values (?, ?, ?, current_timestamp, current_timestamp)
                            """,
                    modelId,
                    summary,
                    provider
            );
            return;
        }
        jdbcTemplate.update("""
                        update ai_model_summary
                        set summary = ?, provider = ?, updated_at = current_timestamp
                        where relation_model_id = ?
                        """,
                summary,
                provider,
                modelId
        );
    }

    private void emitSse(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, data instanceof String ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SSE 发送失败：" + ex.getMessage(), ex);
        }
    }

    private void saveChatMessage(String sessionId, RelationModel model, String question, String answer, String sql) {
        if (!hasText(sessionId)) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject("select count(*) from ai_chat_session where id = ?", Integer.class, sessionId);
        if (count == null || count == 0) {
            jdbcTemplate.update("""
                            insert into ai_chat_session (
                                id, data_source_id, relation_model_id, title, created_at
                            ) values (?, ?, ?, ?, current_timestamp)
                            """,
                    sessionId,
                    model.dataSourceId(),
                    model.id(),
                    abbreviate(question, 120)
            );
        }
        jdbcTemplate.update("""
                        insert into ai_chat_message (
                            id, session_id, role, content, sql_text, created_at
                        ) values (?, ?, ?, ?, ?, current_timestamp)
                        """,
                UUID.randomUUID().toString(),
                sessionId,
                "user",
                question,
                null
        );
        jdbcTemplate.update("""
                        insert into ai_chat_message (
                            id, session_id, role, content, sql_text, created_at
                        ) values (?, ?, ?, ?, ?, current_timestamp)
                        """,
                UUID.randomUUID().toString(),
                sessionId,
                "assistant",
                answer,
                sql
        );
    }

    private String compactContext(ModelContext context) {
        String nodes = context.nodes().stream()
                .map(node -> node.tableName() + "(" + nullToBlank(node.comment()) + "): " + node.columns().stream()
                        .map(column -> column.name() + " " + nullToBlank(column.type())
                                + (column.primaryKey() ? " PK" : "")
                                + (column.foreignKey() ? " FK" : "")
                                + (hasText(column.comment()) ? " " + column.comment() : ""))
                        .collect(Collectors.joining("; ")))
                .collect(Collectors.joining("\n"));
        String edges = context.edges().stream()
                .map(edge -> edge.sourceTable() + "." + edge.sourceColumn() + " -> " + edge.targetTable() + "." + edge.targetColumn()
                        + " " + edge.relationType() + " confidence=" + edge.confidence())
                .collect(Collectors.joining("\n"));
        return "模型：" + context.model().name() + "\n实体：\n" + nodes + "\n关系：\n" + edges;
    }

    private String buildLocalModelSummary(ModelContext context) {
        String central = context.nodes().get(0).tableName();
        if (!context.edges().isEmpty()) {
            Map<String, Integer> scores = new LinkedHashMap<>();
            context.nodes().forEach(node -> scores.put(node.tableName(), 0));
            context.edges().forEach(edge -> {
                scores.computeIfPresent(edge.sourceTable(), (key, value) -> value + 1);
                scores.computeIfPresent(edge.targetTable(), (key, value) -> value + 1);
            });
            central = scores.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(central);
        }
        return "当前模型以 " + central + " 等业务实体为基础，包含 " + context.nodes().size()
                + " 个实体、" + context.edges().size()
                + " 条关系。AI 会先识别用户问题中的业务对象，再结合模型提示词规划查询 SQL、执行查询并基于结果回答。";
    }

    private TableNode pickMainTable(String question, ModelContext context) {
        String lowerQuestion = question.toLowerCase(Locale.ROOT);
        return context.nodes().stream()
                .filter(node -> lowerQuestion.contains(node.tableName().toLowerCase(Locale.ROOT))
                        || lowerQuestion.contains(nullToBlank(node.comment()).toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse(context.nodes().get(0));
    }

    private String pickKeyColumn(TableNode table) {
        return table.columns().stream()
                .filter(ColumnItem::primaryKey)
                .map(ColumnItem::name)
                .findFirst()
                .orElse(table.columns().isEmpty() ? "ID" : table.columns().get(0).name());
    }

    private String extractLikelyKeyValue(String question) {
        Matcher matcher = Pattern.compile("[A-Za-z0-9_-]{2,}").matcher(question);
        String candidate = "";
        while (matcher.find()) {
            candidate = matcher.group();
        }
        return hasText(candidate) ? candidate : question.trim();
    }

    private boolean hasQueryIntent(String question) {
        String normalized = nullToBlank(question).toLowerCase(Locale.ROOT);
        if (normalized.matches("(?s).*(什么关系|关系是什么|怎么关联|如何关联|字段含义|模型理解|模型说明|有哪些实体|哪些实体|依赖规则|查询能力|能问什么|怎么问|如何提问|口径|解释|说明|介绍).*")) {
            return false;
        }
        return normalized.matches("(?s).*(查询|查一下|查找|查出|获取|列出|明细|记录|数据|统计|数量|多少|几条|是否存在|校验|验证|sql|select|where|订单号|编号|id).*");
    }

    private boolean deepSeekAvailable() {
        return hasText(deepSeekApiKey);
    }

    private String deepSeekModelName(String model) {
        if (Objects.equals(model, "deepseek-v4-flash")) {
            return deepSeekFlashModel;
        }
        return deepSeekProModel;
    }

    private String normalizeBaseUrl(String value) {
        return value == null || value.isBlank() ? "https://api.deepseek.com" : value.replaceAll("/+$", "");
    }

    private int safeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_RESULT_LIMIT;
        }
        return Math.min(Math.max(limit, 1), 500);
    }

    private String sqlLiteral(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return "'" + String.valueOf(value).replace("'", "''") + "'";
    }

    private List<String> stringArray(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (hasText(item.asText())) {
                values.add(item.asText());
            }
        });
        return values;
    }

    private String extractJson(String content) {
        String trimmed = nullToBlank(content).trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record PromptRequest(@NotBlank String content) {
    }

    public record ModelPrompt(String id, String content, int sortOrder) {
    }

    public record SummaryRequest(String currentSummary) {
    }

    public record ModelSummaryResponse(String summary, String provider) {
    }

    public record AiQueryChatRequest(
            @NotBlank String modelId,
            @NotBlank String question,
            String sessionId,
            Integer limit
    ) {
    }

    public record AiQueryChatResponse(
            String answer,
            List<String> steps,
            String sql,
            List<String> columns,
            List<Map<String, Object>> rows,
            int rowCount,
            List<String> entities,
            List<String> notes,
            List<ModelPrompt> prompts
    ) {
    }

    public record ChatStep(String title, String detail) {
    }

    public record ChatSql(String sql) {
    }

    public record ChatExecutionResult(
            List<String> columns,
            List<Map<String, Object>> rows,
            int rowCount
    ) {
    }

    private record RelationModel(String id, String name, String dataSourceId, String dataSourceName, String description) {
    }

    private record ModelContext(RelationModel model, List<TableNode> nodes, List<RelationEdge> edges) {
    }

    private record TableNode(String tableName, String comment, List<ColumnItem> columns) {
    }

    private record ColumnItem(String name, String type, String comment, boolean primaryKey, boolean foreignKey) {
    }

    private record RelationEdge(
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            String relationType,
            double confidence
    ) {
    }

    private record SqlPlan(boolean requiresQuery, List<String> steps, List<String> entities, String sql, List<String> notes) {
    }

    private record QueryExecution(List<String> columns, List<Map<String, Object>> rows, int rowCount) {
        private static QueryExecution empty() {
            return new QueryExecution(List.of(), List.of(), 0);
        }
    }

    private record DataSourceRecord(String jdbcUrl, String username, String password) {
    }
}
