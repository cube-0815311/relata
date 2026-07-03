package com.relata.relation;

import java.util.ArrayList;
import java.util.UUID;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {

    private final JdbcTemplate jdbcTemplate;

    public WorkbenchController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/overview")
    public WorkbenchOverview overview() {
        List<TableNode> nodes = List.of(
                new TableNode("ORDER_INFO", "订单主表", "confirmed", 520, 110, List.of(
                        new ColumnItem("ID", "bigint", "PK", "订单ID"),
                        new ColumnItem("USER_ID", "bigint", "FK", "用户ID"),
                        new ColumnItem("ORDER_NO", "varchar", "", "订单编号"),
                        new ColumnItem("CREATE_TIME", "datetime", "", "创建时间")
                )),
                new TableNode("ORDER_ITEM", "订单明细", "recommended", 520, 390, List.of(
                        new ColumnItem("ID", "bigint", "PK", "明细ID"),
                        new ColumnItem("ORDER_ID", "bigint", "FK", "订单ID"),
                        new ColumnItem("SKU_ID", "bigint", "FK", "商品SKU"),
                        new ColumnItem("QUANTITY", "int", "", "数量")
                )),
                new TableNode("PAYMENT_RECORD", "支付记录", "recommended", 180, 365, List.of(
                        new ColumnItem("ID", "bigint", "PK", "支付ID"),
                        new ColumnItem("ORDER_ID", "bigint", "FK", "订单ID"),
                        new ColumnItem("PAY_AMOUNT", "decimal", "", "支付金额"),
                        new ColumnItem("STATUS", "varchar", "", "支付状态")
                )),
                new TableNode("USER_INFO", "用户信息", "confirmed", 875, 350, List.of(
                        new ColumnItem("ID", "bigint", "PK", "用户ID"),
                        new ColumnItem("USER_NAME", "varchar", "", "用户名"),
                        new ColumnItem("MOBILE", "varchar", "", "手机号")
                )),
                new TableNode("PRODUCT_SKU", "商品规格", "candidate", 875, 600, List.of(
                        new ColumnItem("ID", "bigint", "PK", "SKU ID"),
                        new ColumnItem("PRODUCT_ID", "bigint", "FK", "商品ID"),
                        new ColumnItem("SKU_NAME", "varchar", "", "规格名称")
                ))
        );

        List<RelationEdge> edges = List.of(
                new RelationEdge("ORDER_INFO", "ORDER_ITEM", "ID", "ORDER_ID", "ORDER_INFO.ID -> ORDER_ITEM.ORDER_ID", "ONE_TO_MANY", 0.93, "AI推荐", true, ""),
                new RelationEdge("ORDER_INFO", "PAYMENT_RECORD", "ID", "ORDER_ID", "ORDER_INFO.ID -> PAYMENT_RECORD.ORDER_ID", "ONE_TO_MANY", 0.89, "AI推荐", false, ""),
                new RelationEdge("USER_INFO", "ORDER_INFO", "ID", "USER_ID", "USER_INFO.ID -> ORDER_INFO.USER_ID", "ONE_TO_MANY", 0.91, "已确认", true, ""),
                new RelationEdge("ORDER_ITEM", "PRODUCT_SKU", "SKU_ID", "ID", "ORDER_ITEM.SKU_ID -> PRODUCT_SKU.ID", "MANY_TO_ONE", 0.82, "AI推荐", false, "")
        );

        return new WorkbenchOverview(
                "mysql-dev",
                "订单关系模型",
                List.of("ORDER_INFO", "ORDER_ITEM", "PAYMENT_RECORD", "USER_INFO", "PRODUCT_SKU", "SHIPMENT_INFO"),
                nodes,
                edges,
                new RelationInspector("ORDER_INFO", "ID", "ORDER_ITEM", "ORDER_ID", "ONE_TO_MANY", 0.93,
                        "ORDER_ITEM.ORDER_ID 与 ORDER_INFO.ID 类型一致，字段注释均表示订单ID，适合作为关联查询路径。")
        );
    }

    @GetMapping("/relation-models")
    public RelationModelPage relationModels(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String likeKeyword = "%" + normalizedKeyword.toLowerCase() + "%";

        Integer total = jdbcTemplate.queryForObject("""
                        select count(*)
                        from relation_model m
                        left join data_source d on d.id = m.data_source_id
                        where ? = ''
                           or lower(m.name) like ?
                           or lower(coalesce(m.description, '')) like ?
                           or lower(coalesce(d.name, '')) like ?
                        """,
                Integer.class,
                normalizedKeyword,
                likeKeyword,
                likeKeyword,
                likeKeyword
        );

        List<RelationModelListItem> models = jdbcTemplate.query("""
                        select m.id, m.name, m.data_source_id, coalesce(d.name, '-') as data_source_name,
                               coalesce(m.description, '') as description,
                               coalesce(n.node_count, 0) as node_count,
                               coalesce(e.edge_count, 0) as edge_count
                        from relation_model m
                        left join data_source d on d.id = m.data_source_id
                        left join (
                            select relation_model_id, count(*) as node_count
                            from relation_model_node
                            group by relation_model_id
                        ) n on n.relation_model_id = m.id
                        left join (
                            select relation_model_id, count(*) as edge_count
                            from relation_model_edge
                            where coalesce(enabled_flag, true) = true
                            group by relation_model_id
                        ) e on e.relation_model_id = m.id
                        where ? = ''
                           or lower(m.name) like ?
                           or lower(coalesce(m.description, '')) like ?
                           or lower(coalesce(d.name, '')) like ?
                        order by m.updated_at desc, m.created_at desc
                        limit ? offset ?
                        """,
                (rs, rowNum) -> new RelationModelListItem(
                        rs.getString("id"),
                        rs.getString("name"),
                        parseLongOrZero(rs.getString("data_source_id")),
                        rs.getString("data_source_name"),
                        rs.getString("description"),
                        relationModelTableNames(rs.getString("id")),
                        rs.getInt("node_count"),
                        rs.getInt("edge_count")
                ),
                normalizedKeyword,
                likeKeyword,
                likeKeyword,
                likeKeyword,
                safeSize,
                offset
        );

        return new RelationModelPage(safePage, safeSize, total == null ? 0 : total, models);
    }

    @GetMapping("/relation-models/{id}")
    public RelationModelDetail relationModelDetail(@PathVariable String id) {
        RelationModelRecord model = findRelationModel(id);
        return toDetail(model);
    }

    @PostMapping("/relation-models")
    @ResponseStatus(HttpStatus.CREATED)
    public RelationModelDetail saveRelationModel(@Valid @RequestBody SaveRelationModelRequest request) {
        String id = request.id() == null || request.id().isBlank() ? UUID.randomUUID().toString() : request.id();
        Integer existing = jdbcTemplate.queryForObject("select count(*) from relation_model where id = ?", Integer.class, id);
        String dataSourceId = String.valueOf(request.dataSourceId());
        String mainTable = request.nodes().isEmpty() ? null : request.nodes().get(0).tableName();
        if (existing == null || existing == 0) {
            jdbcTemplate.update("""
                            insert into relation_model (
                                id, data_source_id, name, main_table, description, created_at, updated_at
                            ) values (?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                            """,
                    id,
                    dataSourceId,
                    request.name(),
                    mainTable,
                    request.description()
            );
        } else {
            jdbcTemplate.update("""
                            update relation_model
                            set data_source_id = ?, name = ?, main_table = ?, description = ?, updated_at = current_timestamp
                            where id = ?
                            """,
                    dataSourceId,
                    request.name(),
                    mainTable,
                    request.description(),
                    id
            );
        }

        jdbcTemplate.update("delete from relation_model_edge where relation_model_id = ?", id);
        jdbcTemplate.update("delete from relation_model_node where relation_model_id = ?", id);

        for (TableNode node : request.nodes()) {
            jdbcTemplate.update("""
                            insert into relation_model_node (
                                id, relation_model_id, table_name, x, y, status, created_at
                            ) values (?, ?, ?, ?, ?, ?, current_timestamp)
                            """,
                    UUID.randomUUID().toString(),
                    id,
                    node.tableName(),
                    node.x(),
                    node.y(),
                    node.status()
            );
        }
        for (RelationEdge edge : request.edges()) {
            jdbcTemplate.update("""
                            insert into relation_model_edge (
                                id, relation_model_id, source_table, source_column, target_table, target_column,
                                relation_type, confidence, confirmed_flag, enabled_flag, ai_reason, created_at
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, true, ?, current_timestamp)
                            """,
                    UUID.randomUUID().toString(),
                    id,
                    edge.source(),
                    edge.sourceColumn(),
                    edge.target(),
                    edge.targetColumn(),
                    edge.relationType(),
                    edge.confidence(),
                    edge.confirmed(),
                    edge.reason()
            );
        }

        return toDetail(findRelationModel(id));
    }

    @DeleteMapping("/relation-models/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRelationModel(@PathVariable String id) {
        findRelationModel(id);
        jdbcTemplate.update("""
                        delete from sync_task
                        where relation_query_record_id in (
                            select id from relation_query_record where relation_model_id = ?
                        )
                        """,
                id
        );
        jdbcTemplate.update("delete from relation_query_record where relation_model_id = ?", id);
        jdbcTemplate.update("delete from ai_model_prompt where relation_model_id = ?", id);
        jdbcTemplate.update("delete from ai_model_summary where relation_model_id = ?", id);
        jdbcTemplate.update("delete from relation_model_edge where relation_model_id = ?", id);
        jdbcTemplate.update("delete from relation_model_node where relation_model_id = ?", id);
        jdbcTemplate.update("delete from relation_model where id = ?", id);
    }

    public record WorkbenchOverview(
            String selectedDataSource,
            String selectedModel,
            List<String> tables,
            List<TableNode> nodes,
            List<RelationEdge> edges,
            RelationInspector inspector
    ) {
    }

    public record TableNode(
            String tableName,
            String comment,
            String status,
            int x,
            int y,
            List<ColumnItem> columns
    ) {
    }

    public record ColumnItem(String name, String type, String badge, String comment) {
    }

    public record RelationEdge(
            String source,
            String target,
            String sourceColumn,
            String targetColumn,
            String label,
            String relationType,
            double confidence,
            String sourceType,
            boolean confirmed,
            String reason
    ) {
    }

    public record RelationInspector(
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            String relationType,
            double confidence,
            String reason
    ) {
    }

    public record RelationModelPage(int page, int size, int total, List<RelationModelListItem> models) {
    }

    public record RelationModelListItem(
            String id,
            String name,
            long dataSourceId,
            String dataSourceName,
            String description,
            List<String> tableNames,
            int nodeCount,
            int edgeCount
    ) {
    }

    public record RelationModelDetail(
            String id,
            String name,
            long dataSourceId,
            String dataSourceName,
            String description,
            List<TableNode> nodes,
            List<RelationEdge> edges
    ) {
    }

    public record SaveRelationModelRequest(
            String id,
            @NotBlank String name,
            long dataSourceId,
            String description,
            List<TableNode> nodes,
            List<RelationEdge> edges
    ) {
        public SaveRelationModelRequest {
            nodes = nodes == null ? List.of() : nodes;
            edges = edges == null ? List.of() : edges;
        }
    }

    private RelationModelDetail toDetail(RelationModelRecord model) {
        List<TableNode> nodes = jdbcTemplate.query("""
                        select table_name, x, y, coalesce(status, 'candidate') as status
                        from relation_model_node
                        where relation_model_id = ?
                        order by created_at, table_name
                        """,
                (rs, rowNum) -> new TableNode(
                        rs.getString("table_name"),
                        tableComment(model.dataSourceId(), rs.getString("table_name")),
                        rs.getString("status"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        columns(model.dataSourceId(), rs.getString("table_name"))
                ),
                model.id()
        );
        List<RelationEdge> edges = jdbcTemplate.query("""
                        select source_table, source_column, target_table, target_column, relation_type,
                               coalesce(confidence, 1) as confidence,
                               coalesce(confirmed_flag, false) as confirmed_flag,
                               ai_reason
                        from relation_model_edge
                        where relation_model_id = ? and coalesce(enabled_flag, true) = true
                        order by created_at
                        """,
                (rs, rowNum) -> {
                    String source = rs.getString("source_table");
                    String sourceColumn = rs.getString("source_column");
                    String target = rs.getString("target_table");
                    String targetColumn = rs.getString("target_column");
                    boolean confirmed = rs.getBoolean("confirmed_flag");
                    return new RelationEdge(
                            source,
                            target,
                            sourceColumn,
                            targetColumn,
                            source + "." + sourceColumn + " -> " + target + "." + targetColumn,
                            rs.getString("relation_type"),
                            rs.getDouble("confidence"),
                            confirmed ? "手动配置" : "AI分析",
                            confirmed,
                            rs.getString("ai_reason")
                    );
                },
                model.id()
        );
        return new RelationModelDetail(
                model.id(),
                model.name(),
                parseLongOrZero(model.dataSourceId()),
                model.dataSourceName(),
                model.description(),
                nodes,
                edges
        );
    }

    private RelationModelRecord findRelationModel(String id) {
        List<RelationModelRecord> models = jdbcTemplate.query("""
                        select m.id, m.name, m.data_source_id, coalesce(d.name, '-') as data_source_name,
                               coalesce(m.description, '') as description
                        from relation_model m
                        left join data_source d on d.id = m.data_source_id
                        where m.id = ?
                        """,
                (rs, rowNum) -> new RelationModelRecord(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("data_source_id"),
                        rs.getString("data_source_name"),
                        rs.getString("description")
                ),
                id
        );
        if (models.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Relation model not found: " + id);
        }
        return models.get(0);
    }

    private String tableComment(String dataSourceId, String tableName) {
        return jdbcTemplate.query("""
                        select coalesce(table_comment, '')
                        from table_metadata
                        where data_source_id = ? and table_name = ?
                        limit 1
                        """,
                (rs, rowNum) -> rs.getString(1),
                dataSourceId,
                tableName
        ).stream().findFirst().orElse("数据表");
    }

    private List<String> relationModelTableNames(String relationModelId) {
        return jdbcTemplate.query("""
                        select table_name
                        from relation_model_node
                        where relation_model_id = ?
                        order by created_at, table_name
                        """,
                (rs, rowNum) -> rs.getString("table_name"),
                relationModelId
        );
    }

    private List<ColumnItem> columns(String dataSourceId, String tableName) {
        List<String> tableIds = jdbcTemplate.query("""
                        select id
                        from table_metadata
                        where data_source_id = ? and table_name = ?
                        limit 1
                        """,
                (rs, rowNum) -> rs.getString("id"),
                dataSourceId,
                tableName
        );
        if (tableIds.isEmpty()) {
            return new ArrayList<>();
        }
        return jdbcTemplate.query("""
                        select column_name, data_type, column_comment,
                               coalesce(primary_key_flag, false) as primary_key_flag,
                               coalesce(foreign_key_flag, false) as foreign_key_flag
                        from column_metadata
                        where table_metadata_id = ?
                        order by ordinal_position, column_name
                        """,
                (rs, rowNum) -> new ColumnItem(
                        rs.getString("column_name"),
                        rs.getString("data_type"),
                        rs.getBoolean("primary_key_flag") ? "PK" : rs.getBoolean("foreign_key_flag") ? "FK" : "",
                        rs.getString("column_comment") == null ? "" : rs.getString("column_comment")
                ),
                tableIds.get(0)
        );
    }

    private static long parseLongOrZero(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private record RelationModelRecord(String id, String name, String dataSourceId, String dataSourceName, String description) {
    }
}
