package com.relata.relation;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {

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
                new RelationEdge("ORDER_INFO", "ORDER_ITEM", "ORDER_INFO.ID -> ORDER_ITEM.ORDER_ID", "ONE_TO_MANY", 0.93, "AI推荐", true),
                new RelationEdge("ORDER_INFO", "PAYMENT_RECORD", "ORDER_INFO.ID -> PAYMENT_RECORD.ORDER_ID", "ONE_TO_MANY", 0.89, "AI推荐", false),
                new RelationEdge("USER_INFO", "ORDER_INFO", "USER_INFO.ID -> ORDER_INFO.USER_ID", "ONE_TO_MANY", 0.91, "已确认", true),
                new RelationEdge("ORDER_ITEM", "PRODUCT_SKU", "ORDER_ITEM.SKU_ID -> PRODUCT_SKU.ID", "MANY_TO_ONE", 0.82, "AI推荐", false)
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
            String label,
            String relationType,
            double confidence,
            String sourceType,
            boolean confirmed
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
}
