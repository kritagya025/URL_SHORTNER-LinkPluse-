package com.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) for aggregate URL metrics and analytics summary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlSummaryResponse {

    /**
     * Total number of shortened links generated.
     */
    private long totalUrls;

    /**
     * Cumulative total number of redirect clicks recorded across all links.
     */
    private long totalClicks;

    /**
     * Number of non-expired, currently active short URLs.
     */
    private long activeUrls;

    /**
     * Number of expired short URLs.
     */
    private long expiredUrls;

    /**
     * Average number of clicks per shortened URL rounded to 2 decimal places.
     */
    private double averageClicksPerUrl;

    /**
     * The highest performing URL by click count, or null if no links exist or have clicks.
     */
    private UrlResponse mostVisitedUrl;
}
