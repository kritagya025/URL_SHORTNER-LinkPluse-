package com.urlshortener.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fixed-window rate limiter that throttles URL creation requests
 * per client IP address to prevent abuse and database flooding.
 *
 * <p>Each IP is allowed a configurable number of POST requests to {@code /api/urls}
 * within a rolling time window. Every evaluated request receives standard
 * {@code X-RateLimit-Limit}, {@code X-RateLimit-Remaining}, and {@code X-RateLimit-Reset}
 * headers. Exceeding the limit returns HTTP 429 Too Many Requests with a standard
 * JSON error payload and a {@code Retry-After} header.
 *
 * <p>Expired IP buckets are lazily evicted when the tracked map grows past
 * {@value #MAX_TRACKED_CLIENTS} entries so spoofed {@code X-Forwarded-For} headers
 * cannot trigger unbounded heap growth.
 *
 * <p><strong>Limitation:</strong> This implementation stores counters in JVM memory
 * and is suitable for single-instance deployments. For horizontally scaled setups,
 * replace with a Redis-backed rate limiter.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final int maxRequests;
    private final long windowMs;
    private final Clock clock;
    private final Map<String, ClientBucket> buckets = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitFilter(
            @Value("${app.rate-limit.max-requests:30}") int maxRequests,
            @Value("${app.rate-limit.window-ms:60000}") long windowMs) {
        this(maxRequests, windowMs, Clock.systemUTC());
    }

    RateLimitFilter(int maxRequests, long windowMs, Clock clock) {
        this.maxRequests = Math.max(1, maxRequests);
        this.windowMs = Math.max(1000L, windowMs);
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (!"POST".equalsIgnoreCase(request.getMethod())
                || (!"/api/urls".equals(uri) && !"/api/urls/".equals(uri))) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = clock.millis();
        evictExpiredBucketsIfNeeded(now);

        String clientIp = resolveClientIp(request);
        ClientBucket bucket = buckets.computeIfAbsent(clientIp, k -> new ClientBucket(now));
        ConsumeResult result = bucket.tryConsume(now, maxRequests, windowMs);

        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetEpochSeconds()));

        if (!result.allowed()) {
            long retryAfterSeconds = Math.max(1L, (result.resetMillis() - now + 999L) / 1000L);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.getWriter().write(
                    "{\"status\":429,\"message\":\"Rate limit exceeded. Maximum "
                            + maxRequests + " link creations per minute. Try again shortly.\","
                            + "\"timestamp\":\"" + LocalDateTime.now(clock) + "\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void evictExpiredBucketsIfNeeded(long now) {
        if (buckets.size() >= MAX_TRACKED_CLIENTS) {
            buckets.entrySet().removeIf(entry -> entry.getValue().isExpired(now, windowMs));
        }
    }

    /**
     * Extracts the client IP, respecting X-Forwarded-For when running behind a reverse proxy.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "unknown";
    }

    int trackedClientCount() {
        return buckets.size();
    }

    private record ConsumeResult(boolean allowed, int remaining, long resetMillis) {
        long resetEpochSeconds() {
            return (resetMillis + 999L) / 1000L;
        }
    }

    private static final class ClientBucket {
        private long windowStart;
        private int count;

        ClientBucket(long now) {
            this.windowStart = now;
            this.count = 0;
        }

        synchronized ConsumeResult tryConsume(long now, int maxRequests, long windowMs) {
            if (now - windowStart >= windowMs) {
                windowStart = now;
                count = 0;
            }
            long resetMillis = windowStart + windowMs;
            if (count < maxRequests) {
                count++;
                return new ConsumeResult(true, maxRequests - count, resetMillis);
            }
            return new ConsumeResult(false, 0, resetMillis);
        }

        synchronized boolean isExpired(long now, long windowMs) {
            return now - windowStart >= windowMs;
        }
    }
}
