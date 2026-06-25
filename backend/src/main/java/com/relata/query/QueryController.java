package com.relata.query;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/query")
public class QueryController {

    @PostMapping("/associated-data")
    public AssociatedDataResponse queryAssociatedData(@Valid @RequestBody AssociatedDataRequest request) {
        return new AssociatedDataResponse(List.of(
                new QueryStep("ORDER_INFO", "select * from ORDER_INFO where " + request.keyColumn() + " = ?", 1, "DONE"),
                new QueryStep("ORDER_ITEM", "select * from ORDER_ITEM where ORDER_ID in (?)", 3, "DONE"),
                new QueryStep("PAYMENT_RECORD", "select * from PAYMENT_RECORD where ORDER_ID in (?)", 1, "DONE")
        ));
    }

    public record AssociatedDataRequest(
            @NotBlank String relationModelId,
            @NotBlank String mainTable,
            @NotBlank String keyColumn,
            @NotBlank String keyValue
    ) {
    }

    public record AssociatedDataResponse(List<QueryStep> steps) {
    }

    public record QueryStep(String tableName, String sqlPreview, int rowCount, String status) {
    }
}
