package com.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlResponse {

    private Long id;
    private String originalUrl;
    private String shortCode;
    private String shortUrl;
    private Long clickCount;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
