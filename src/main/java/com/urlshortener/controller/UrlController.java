package com.urlshortener.controller;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.service.UrlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
