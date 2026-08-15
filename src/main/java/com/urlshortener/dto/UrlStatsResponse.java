package com.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object (DTO) for link analytics and statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlStatsResponse {

    /** Original target URL */
    private String originalUrl;

    /** Unique short code identifier */
    private String shortCode;

    /** Full short URL link */
    private String shortUrl;

    /** Total number of clicks */
    private Long clickCount;

    /** Creation date-time */
    private LocalDateTime createdAt;

    /** Optional expiration date-time */
    private LocalDateTime expiresAt;

    /** Link status identifier ("ACTIVE" or "EXPIRED") */
    private String status;
}
