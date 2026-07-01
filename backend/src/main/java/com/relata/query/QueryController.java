package com.relata.query;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/query")
public class QueryController {

    private static final Logger log = LoggerFactory.getLogger(QueryController.class);

    private final JdbcTemplate jdbcTemplate;

    public QueryController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/associated-data")
    public AssociatedDataResponse queryAssociatedData(@Valid @RequestBody AssociatedDataRequest request) {
        RelationModel model = findRelationModel(request.relationModelId());
        List<TableNode> nodes = findModelNodes(model.id());
        List<RelationEdge> edges = findModelEdges(model.id());
        validateQueryScope(request, nodes);

        DataSourceRecord dataSource = findDataSource(model.dataSourceId());
        try (Connection connection = DriverManager.getConnection(dataSource.jdbcUrl(), dataSource.username(), dataSource.password())) {
            JdbcTemplate sourceJdbcTemplate = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true));
            String quote = identifierQuote(connection);
            Map<String, QueryTableResult> results = queryModelGraph(sourceJdbcTemplate, quote, request, nodes, edges);
            return new AssociatedDataResponse(new ArrayList<>(results.values()));
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "关联数据查询失败：" + ex.getMostSpecificCause().getMessage(), ex);
        } catch (SQLException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "关联数据查询失败：" + ex.getMessage(), ex);
        }
    }

    private Map<String, QueryTableResult> queryModelGraph(
            JdbcTemplate sourceJdbcTemplate,
            String quote,
            AssociatedDataRequest request,
            List<TableNode> nodes,
            List<RelationEdge> edges
    ) {
        Map<String, TableNode> nodeMap = new HashMap<>();
        nodes.forEach(node -> nodeMap.put(node.tableName(), node));

        Map<String, QueryTableResult> results = new LinkedHashMap<>();
        Map<String, Map<String, Set<Object>>> pendingValues = new HashMap<>();
        Set<String> visitedQueries = new LinkedHashSet<>();
        Queue<QuerySeed> queue = new ArrayDeque<>();
        queue.add(new QuerySeed(request.mainTable(), request.keyColumn(), List.<Object>of(request.keyValue()), "起点查询"));

        while (!queue.isEmpty()) {
            QuerySeed seed = queue.poll();
            TableNode table = nodeMap.get(seed.tableName());
            if (table == null || !hasColumn(table, seed.columnName()) || seed.values().isEmpty()) {
                continue;
            }
            String visitKey = seed.tableName() + "." + seed.columnName() + "=" + seed.values();
            if (!visitedQueries.add(visitKey)) {
                continue;
            }

            List<Map<String, Object>> rows = queryRows(sourceJdbcTemplate, quote, seed.tableName(), seed.columnName(), seed.values());
            QueryTableResult existing = results.get(seed.tableName());
            if (existing == null) {
                results.put(seed.tableName(), new QueryTableResult(
                        seed.tableName(),
                        table.comment(),
                        seed.path(),
                        columnsFromRows(rows, table.columns()),
                        rows.size(),
                        rows
                ));
            } else if (!rows.isEmpty()) {
                List<Map<String, Object>> mergedRows = mergeRows(existing.rows(), rows);
                results.put(seed.tableName(), new QueryTableResult(
                        existing.tableName(),
                        existing.comment(),
                        existing.relationPath(),
                        columnsFromRows(mergedRows, table.columns()),
                        mergedRows.size(),
                        mergedRows
                ));
            }

            if (rows.isEmpty()) {
                continue;
            }
            for (RelationEdge edge : edges) {
                if (Objects.equals(edge.sourceTable(), seed.tableName())) {
                    Set<Object> values = collectColumnValues(rows, edge.sourceColumn());
                    enqueueIfNew(queue, pendingValues, edge.targetTable(), edge.targetColumn(), values,
                            edge.sourceTable() + "." + edge.sourceColumn() + " -> " + edge.targetTable() + "." + edge.targetColumn());
                }
                if (Objects.equals(edge.targetTable(), seed.tableName())) {
                    Set<Object> values = collectColumnValues(rows, edge.targetColumn());
                    enqueueIfNew(queue, pendingValues, edge.sourceTable(), edge.sourceColumn(), values,
                            edge.targetTable() + "." + edge.targetColumn() + " -> " + edge.sourceTable() + "." + edge.sourceColumn());
                }
            }
        }
        return results;
    }

    private List<Map<String, Object>> queryRows(JdbcTemplate sourceJdbcTemplate, String quote, String tableName, String columnName, List<Object> values) {
        String placeholders = String.join(", ", Collections.nCopies(values.size(), "?"));
        String sql = "select * from " + quoted(quote, tableName) + " where " + quoted(quote, columnName) + " in (" + placeholders + ") limit 100";
        log.info("执行关联模型查询 SQL: {}; params={}", sql, values);
        log.info("执行关联模型查询 SQL 展开: {}", renderSql(sql, values));
        return sourceJdbcTemplate.query(sql, ps -> {
            for (int index = 0; index < values.size(); index++) {
                ps.setObject(index + 1, values.get(index));
            }
        }, (rs, rowNum) -> {
            ResultSetMetaData metaData = rs.getMetaData();
            Map<String, Object> row = new LinkedHashMap<>();
            for (int columnIndex = 1; columnIndex <= metaData.getColumnCount(); columnIndex++) {
                row.put(metaData.getColumnLabel(columnIndex), rs.getObject(columnIndex));
            }
            return row;
        });
    }

    private String renderSql(String sql, List<Object> values) {
        String rendered = sql;
        for (Object value : values) {
            rendered = rendered.replaceFirst("\\?", java.util.regex.Matcher.quoteReplacement(sqlLiteral(value)));
        }
        return rendered;
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

    private void enqueueIfNew(
            Queue<QuerySeed> queue,
            Map<String, Map<String, Set<Object>>> pendingValues,
            String tableName,
            String columnName,
            Set<Object> values,
            String path
    ) {
        if (values.isEmpty()) {
            return;
        }
        Set<Object> knownValues = pendingValues
                .computeIfAbsent(tableName, key -> new HashMap<>())
                .computeIfAbsent(columnName, key -> new LinkedHashSet<>());
        List<Object> nextValues = values.stream().filter(value -> !knownValues.contains(value)).toList();
        if (nextValues.isEmpty()) {
            return;
        }
        knownValues.addAll(nextValues);
        queue.add(new QuerySeed(tableName, columnName, nextValues, path));
    }

    private List<Map<String, Object>> mergeRows(List<Map<String, Object>> currentRows, List<Map<String, Object>> nextRows) {
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> row : currentRows) {
            if (seen.add(row.toString())) {
                merged.add(row);
            }
        }
        for (Map<String, Object> row : nextRows) {
            if (seen.add(row.toString())) {
                merged.add(row);
            }
        }
        return merged;
    }

    private Set<Object> collectColumnValues(List<Map<String, Object>> rows, String columnName) {
        Set<Object> values = new LinkedHashSet<>();
        rows.forEach(row -> {
            Object value = row.get(columnName);
            if (value != null) {
                values.add(value);
            }
        });
        return values;
    }

    private List<String> columnsFromRows(List<Map<String, Object>> rows, List<String> fallbackColumns) {
        if (!rows.isEmpty()) {
            return new ArrayList<>(rows.get(0).keySet());
        }
        return fallbackColumns;
    }

    private void validateQueryScope(AssociatedDataRequest request, List<TableNode> nodes) {
        TableNode table = nodes.stream()
                .filter(node -> Objects.equals(node.tableName(), request.mainTable()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "查询表不在关系模型中"));
        if (!hasColumn(table, request.keyColumn())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "查询字段不在关系模型表中");
        }
    }

    private boolean hasColumn(TableNode table, String columnName) {
        return table.columns().stream().anyMatch(column -> Objects.equals(column, columnName));
    }

    private RelationModel findRelationModel(String id) {
        return jdbcTemplate.query("""
                        select id, data_source_id, name
                        from relation_model
                        where id = ?
                        """,
                (rs, rowNum) -> new RelationModel(rs.getString("id"), rs.getString("data_source_id"), rs.getString("name")),
                id
        ).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Relation model not found: " + id));
    }

    private List<TableNode> findModelNodes(String relationModelId) {
        return jdbcTemplate.query("""
                        select n.table_name, coalesce(t.table_comment, '') as table_comment
                        from relation_model_node n
                        left join relation_model m on m.id = n.relation_model_id
                        left join table_metadata t on t.data_source_id = m.data_source_id and t.table_name = n.table_name
                        where n.relation_model_id = ?
                        order by n.created_at, n.table_name
                        """,
                (rs, rowNum) -> new TableNode(
                        rs.getString("table_name"),
                        rs.getString("table_comment"),
                        findColumns(relationModelId, rs.getString("table_name"))
                ),
                relationModelId
        );
    }

    private List<String> findColumns(String relationModelId, String tableName) {
        return jdbcTemplate.queryForList("""
                        select c.column_name
                        from relation_model m
                        join table_metadata t on t.data_source_id = m.data_source_id and t.table_name = ?
                        join column_metadata c on c.table_metadata_id = t.id
                        where m.id = ?
                        order by c.ordinal_position, c.column_name
                        """,
                String.class,
                tableName,
                relationModelId
        );
    }

    private List<RelationEdge> findModelEdges(String relationModelId) {
        return jdbcTemplate.query("""
                        select source_table, source_column, target_table, target_column
                        from relation_model_edge
                        where relation_model_id = ? and coalesce(enabled_flag, true) = true
                        order by created_at
                        """,
                (rs, rowNum) -> new RelationEdge(
                        rs.getString("source_table"),
                        rs.getString("source_column"),
                        rs.getString("target_table"),
                        rs.getString("target_column")
                ),
                relationModelId
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

    private String identifierQuote(Connection connection) throws SQLException {
        String quote = connection.getMetaData().getIdentifierQuoteString();
        return quote == null || quote.isBlank() ? "\"" : quote.trim();
    }

    private String quoted(String quote, String identifier) {
        return quote + identifier.replace(quote, quote + quote) + quote;
    }

    public record AssociatedDataRequest(
            @NotBlank String relationModelId,
            @NotBlank String mainTable,
            @NotBlank String keyColumn,
            @NotBlank String keyValue
    ) {
    }

    public record AssociatedDataResponse(List<QueryTableResult> tables) {
    }

    public record QueryTableResult(
            String tableName,
            String comment,
            String relationPath,
            List<String> columns,
            int rowCount,
            List<Map<String, Object>> rows
    ) {
    }

    private record RelationModel(String id, String dataSourceId, String name) {
    }

    private record TableNode(String tableName, String comment, List<String> columns) {
    }

    private record RelationEdge(String sourceTable, String sourceColumn, String targetTable, String targetColumn) {
    }

    private record DataSourceRecord(String jdbcUrl, String username, String password) {
    }

    private record QuerySeed(String tableName, String columnName, List<Object> values, String path) {
    }
}
