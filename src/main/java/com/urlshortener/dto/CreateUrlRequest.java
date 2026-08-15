package com.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object (DTO) for URL creation requests.
 * Contains the original URL string and an optional expiration timestamp.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUrlRequest {

    /**
     * The original target URL to shorten.
     * Must not be blank.
     */
    @NotBlank(message = "Original URL cannot be empty or null")
    private String originalUrl;

    /**
     * Optional expiration date-time for the shortened link.
     */
    private LocalDateTime expiresAt;
}
