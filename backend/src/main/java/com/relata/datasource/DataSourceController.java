package com.relata.datasource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data-sources")
public class DataSourceController {

    @PostMapping("/test")
    public TestConnectionResponse testConnection(@Valid @RequestBody TestConnectionRequest request) {
        return new TestConnectionResponse(true, "Connection configuration accepted for " + request.name());
    }

    public record TestConnectionRequest(
            @NotBlank String name,
            @NotBlank String databaseType,
            @NotBlank String jdbcUrl,
            @NotBlank String username,
            String password
    ) {
    }

    public record TestConnectionResponse(boolean success, String message) {
    }
}
