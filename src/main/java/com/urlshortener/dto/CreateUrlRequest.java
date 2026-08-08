package com.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUrlRequest {

    @NotBlank(message = "Original URL cannot be empty or null")
    private String originalUrl;

    private LocalDateTime expiresAt;
}
