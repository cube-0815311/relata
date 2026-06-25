package com.relata.ai;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiRelationController {

    @PostMapping("/relations/analyze")
    public AnalyzeRelationsResponse analyzeRelations(@Valid @RequestBody AnalyzeRelationsRequest request) {
        return new AnalyzeRelationsResponse(List.of(
                new CandidateRelation(request.mainTable(), "ID", "ORDER_ITEM", "ORDER_ID", "ONE_TO_MANY", 0.93,
                        "字段名称、注释和类型匹配，适合作为订单主表到明细表的查询路径。"),
                new CandidateRelation(request.mainTable(), "ID", "PAYMENT_RECORD", "ORDER_ID", "ONE_TO_MANY", 0.89,
                        "支付记录表包含订单ID字段，可关联订单主表。")
        ));
    }

    public record AnalyzeRelationsRequest(
            @NotBlank String dataSourceId,
            @NotBlank String mainTable
    ) {
    }

    public record AnalyzeRelationsResponse(List<CandidateRelation> relations) {
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
}
