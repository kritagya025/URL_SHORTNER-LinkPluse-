package com.urlshortener.service;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.entity.Url;
import com.urlshortener.exception.ExpiredUrlException;
import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.exception.ShortUrlNotFoundException;
import com.urlshortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service implementation containing core business logic for URL shortening,
 * redirection resolution, metrics tracking, and link validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrlService {

    private final UrlRepository urlRepository;
    private final ShortCodeGenerator shortCodeGenerator;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional
    public UrlResponse createShortUrl(CreateUrlRequest request) {
        String originalUrl = request.getOriginalUrl().trim();
        validateUrlFormat(originalUrl);

        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidUrlException("Expiration date cannot be in the past");
        }

        String shortCode = generateUniqueShortCode();

        Url url = Url.builder()
                .originalUrl(originalUrl)
                .shortCode(shortCode)
                .clickCount(0L)
                .createdAt(LocalDateTime.now())
                .expiresAt(request.getExpiresAt())
                .build();

        Url savedUrl = urlRepository.save(url);
        log.info("Created short URL code '{}' for original URL '{}'", shortCode, originalUrl);
        return mapToUrlResponse(savedUrl);
    }

    @Transactional
    public String getOriginalUrlAndLogClick(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortUrlNotFoundException("Short URL code '" + shortCode + "' not found"));

        if (isExpired(url)) {
            log.warn("Attempted access to expired short URL code '{}'", shortCode);
            throw new ExpiredUrlException("Short URL code '" + shortCode + "' has expired");
        }

        url.setClickCount(url.getClickCount() + 1);
        urlRepository.save(url);
        log.debug("Incremented click count for short code '{}' to {}", shortCode, url.getClickCount());

        return url.getOriginalUrl();
    }

    @Transactional(readOnly = true)
    public UrlStatsResponse getStats(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortUrlNotFoundException("Short URL code '" + shortCode + "' not found"));

        String status = isExpired(url) ? "EXPIRED" : "ACTIVE";

        return UrlStatsResponse.builder()
                .originalUrl(url.getOriginalUrl())
                .shortCode(url.getShortCode())
                .shortUrl(buildShortUrl(url.getShortCode()))
                .clickCount(url.getClickCount())
                .createdAt(url.getCreatedAt())
                .expiresAt(url.getExpiresAt())
                .status(status)
                .build();
    }

    @Transactional(readOnly = true)
    public List<UrlResponse> getAllUrls() {
        return urlRepository.findAll().stream()
                .sorted((u1, u2) -> u2.getCreatedAt().compareTo(u1.getCreatedAt()))
                .map(this::mapToUrlResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteUrl(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortUrlNotFoundException("Short URL code '" + shortCode + "' not found"));
        urlRepository.delete(url);
        log.info("Deleted short URL code '{}'", shortCode);
    }

    private String generateUniqueShortCode() {
        int maxRetries = 5;
        for (int i = 0; i < maxRetries; i++) {
            String candidate = shortCodeGenerator.generateShortCode();
            if (!urlRepository.existsByShortCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate a unique short code after retries");
    }

    private void validateUrlFormat(String urlString) {
        try {
            URI uri = new URI(urlString);
            if (uri.getScheme() == null || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https"))) {
                throw new InvalidUrlException("URL must start with http:// or https://");
            }
            if (uri.getHost() == null) {
                throw new InvalidUrlException("Invalid URL host structure");
            }
        } catch (Exception e) {
            throw new InvalidUrlException("Invalid URL format: " + e.getMessage());
        }
    }

    private boolean isExpired(Url url) {
        return url.getExpiresAt() != null && LocalDateTime.now().isAfter(url.getExpiresAt());
    }

    private UrlResponse mapToUrlResponse(Url url) {
        return UrlResponse.builder()
                .id(url.getId())
                .originalUrl(url.getOriginalUrl())
                .shortCode(url.getShortCode())
                .shortUrl(buildShortUrl(url.getShortCode()))
                .clickCount(url.getClickCount())
                .createdAt(url.getCreatedAt())
                .expiresAt(url.getExpiresAt())
                .build();
    }

    private String buildShortUrl(String shortCode) {
        return baseUrl + "/" + shortCode;
    }
}
