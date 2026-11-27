package com.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
     * Must not be blank and cannot exceed 2048 characters.
     */
    @NotBlank(message = "Original URL cannot be empty or null")
    @Size(max = 2048, message = "Original URL must not exceed 2048 characters")
    private String originalUrl;

    /**
     * Optional expiration date-time for the shortened link.
     */
    private LocalDateTime expiresAt;

    /**
     * Optional custom alias for the shortened URL.
     * Must be between 3 and 10 alphanumeric characters.
     */
    private String customAlias;
}
