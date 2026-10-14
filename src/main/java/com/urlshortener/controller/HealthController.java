package com.urlshortener.controller;

import com.urlshortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check controller providing operational readiness and liveness status.
 * Intended for container orchestration, Docker health checks, and uptime monitoring.
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final UrlRepository urlRepository;

    /**
     * Checks service health and database connectivity.
     *
     * @return 200 OK with status "UP" when healthy, or 503 SERVICE_UNAVAILABLE with "DEGRADED"
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "LinkPulse URL Shortener");
        response.put("timestamp", LocalDateTime.now());

        try {
            urlRepository.count();
            response.put("status", "UP");
            response.put("database", "CONNECTED");
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("Health check failed: database connectivity issue", ex);
            response.put("status", "DEGRADED");
            response.put("database", "DISCONNECTED");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
    }
}
