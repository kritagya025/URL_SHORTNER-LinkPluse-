package com.urlshortener.controller;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.service.UrlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/urls")
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;

    @PostMapping
    public ResponseEntity<UrlResponse> createShortUrl(@Valid @RequestBody CreateUrlRequest request) {
        UrlResponse response = urlService.createShortUrl(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<UrlResponse>> getAllUrls() {
        List<UrlResponse> urls = urlService.getAllUrls();
        return ResponseEntity.ok(urls);
    }

    @GetMapping("/{shortCode}/stats")
    public ResponseEntity<UrlStatsResponse> getUrlStats(@PathVariable String shortCode) {
        UrlStatsResponse stats = urlService.getStats(shortCode);
        return ResponseEntity.ok(stats);
    }

    @DeleteMapping("/{shortCode}")
    public ResponseEntity<Void> deleteUrl(@PathVariable String shortCode) {
        urlService.deleteUrl(shortCode);
        return ResponseEntity.noContent().build();
    }
}
