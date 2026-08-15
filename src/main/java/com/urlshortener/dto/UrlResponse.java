package com.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object (DTO) returned upon successful URL shortening.
 * Contains short link metadata and metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlResponse {

    /** Database primary key identifier */
    private Long id;

    /** Original destination URL */
    private String originalUrl;

    /** Generated unique short code */
    private String shortCode;

    /** Fully qualified shortened URL link */
    private String shortUrl;

    /** Total click count tracked for this link */
    private Long clickCount;

    /** Timestamp when short URL was generated */
    private LocalDateTime createdAt;

    /** Optional timestamp when short link expires */
    private LocalDateTime expiresAt;
}
