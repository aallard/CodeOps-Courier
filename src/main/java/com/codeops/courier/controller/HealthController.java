package com.codeops.courier.controller;

import com.codeops.courier.config.AppConstants;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Public health check endpoint for CodeOps-Courier.
 *
 * <p>Accessible without authentication. Returns the service status, name, and current timestamp.</p>
 */
@RestController
@RequestMapping(AppConstants.API_PREFIX)
@Tag(name = "Health", description = "Public health check endpoint")
public class HealthController {

    /**
     * Returns the current health status of the CodeOps-Courier service.
     *
     * @return a 200 response with status, service name, and timestamp
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", AppConstants.SERVICE_NAME,
                "timestamp", Instant.now().toString()
        ));
    }
}
