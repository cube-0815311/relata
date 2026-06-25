package com.relata.config;

import java.util.Map;

import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    private final ResourceLoader resourceLoader;

    public SpaForwardController(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @GetMapping(value = {"/", "/workbench"})
    public Object forward() {
        if (!resourceLoader.getResource("classpath:/static/index.html").exists()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "message", "Frontend assets are not available in this backend runtime.",
                    "hint", "Run `sh scripts/run-full.sh` or start the Vite dev server with `cd frontend && npm run dev` and open http://localhost:5173."
            ));
        }

        return "forward:/index.html";
    }
}
