package com.urlshortener.controller;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.dto.UrlSummaryResponse;
import com.urlshortener.service.UrlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for managing short URLs, metrics, and listing active links.
 */
@Slf4j
@RestController
@RequestMapping("/api/urls")
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;

    /**
     * Creates a new short URL link.
     *
     * @param request Payload containing the original target URL and optional expiry
     * @return Created UrlResponse details with 201 HTTP status
     */
    @PostMapping
    public ResponseEntity<UrlResponse> createShortUrl(@Valid @RequestBody CreateUrlRequest request) {
        log.debug("Received request to shorten URL: {}", request.getOriginalUrl());
        UrlResponse response = urlService.createShortUrl(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Retrieves all created URL mappings.
     *
     * @return List of all UrlResponse items
     */
    @GetMapping
    public ResponseEntity<List<UrlResponse>> getAllUrls() {
        List<UrlResponse> urls = urlService.getAllUrls();
        return ResponseEntity.ok(urls);
    }

    /**
     * Retrieves overall system and link analytics summary.
     *
     * @return UrlSummaryResponse containing aggregate system metrics
     */
    @GetMapping("/analytics/summary")
    public ResponseEntity<UrlSummaryResponse> getAnalyticsSummary() {
        UrlSummaryResponse summary = urlService.getAnalyticsSummary();
        return ResponseEntity.ok(summary);
    }

    /**
     * Retrieves usage statistics for a given short code.
     *
     * @param shortCode Unique identifier code of the short link
     * @return UrlStatsResponse containing statistics and status
     */
    @GetMapping("/{shortCode}/stats")
    public ResponseEntity<UrlStatsResponse> getUrlStats(@PathVariable String shortCode) {
        UrlStatsResponse stats = urlService.getStats(shortCode);
        return ResponseEntity.ok(stats);
    }

    /**
     * Purges all expired URL records from the database.
     *
     * @return 200 OK with count of deleted URLs and status message
     */
    @DeleteMapping("/expired")
    public ResponseEntity<Map<String, Object>> deleteExpiredUrls() {
        int deletedCount = urlService.deleteExpiredUrls();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("deletedCount", deletedCount);
        response.put("message", "Successfully purged " + deletedCount + " expired URL" + (deletedCount == 1 ? "" : "s"));
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a short URL link by its short code.
     *
     * @param shortCode Unique identifier code of the short link
     * @return 204 No Content response
     */
    @DeleteMapping("/{shortCode}")
    public ResponseEntity<Void> deleteUrl(@PathVariable String shortCode) {
        urlService.deleteUrl(shortCode);
        return ResponseEntity.noContent().build();
    }
}
