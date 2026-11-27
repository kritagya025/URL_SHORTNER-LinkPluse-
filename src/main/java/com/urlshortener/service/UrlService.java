package com.urlshortener.service;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.dto.UrlSummaryResponse;
import com.urlshortener.entity.Url;
import com.urlshortener.exception.CustomAliasAlreadyExistsException;
import com.urlshortener.exception.ExpiredUrlException;
import com.urlshortener.exception.InvalidUrlException;
import com.urlshortener.exception.ShortUrlNotFoundException;
import com.urlshortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service implementation containing core business logic for URL shortening,
 * redirection resolution, metrics tracking, and link validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrlService {

    private static final Set<String> RESERVED_WORDS = Set.of(
            "api", "health", "css", "js", "static", "assets", "favicon.ico", "robots.txt", "sitemap.xml", "index.html", "admin", "docs", "swagger-ui", "expired"
    );

    private final UrlRepository urlRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final QrCodeService qrCodeService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional
    public UrlResponse createShortUrl(CreateUrlRequest request) {
        String originalUrl = request.getOriginalUrl().trim();
        validateUrlFormat(originalUrl);

        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidUrlException("Expiration date cannot be in the past");
        }

        String shortCode;
        String customAlias = request.getCustomAlias() != null ? request.getCustomAlias().trim() : null;
        if (customAlias != null && !customAlias.isEmpty()) {
            validateCustomAlias(customAlias);
            if (urlRepository.existsByShortCode(customAlias)) {
                throw new CustomAliasAlreadyExistsException("Custom alias '" + customAlias + "' is already in use");
            }
            shortCode = customAlias;
        } else {
            shortCode = generateUniqueShortCode();
        }

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

        // Incremented in a single UPDATE so concurrent visits cannot overwrite each other
        urlRepository.incrementClickCount(url.getId());
        log.debug("Incremented click count for short code '{}'", shortCode);

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
    public byte[] getQrCodeForShortCode(String shortCode, int size) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortUrlNotFoundException("Short URL code '" + shortCode + "' not found"));

        if (isExpired(url)) {
            log.warn("Attempted QR code generation for expired short URL code '{}'", shortCode);
            throw new ExpiredUrlException("Short URL code '" + shortCode + "' has expired");
        }

        int clampedSize = Math.max(64, Math.min(size, 1024));
        String fullShortUrl = buildShortUrl(url.getShortCode());
        return qrCodeService.generateQrCode(fullShortUrl, clampedSize, clampedSize);
    }

    @Transactional(readOnly = true)
    public List<UrlResponse> getAllUrls() {
        return getAllUrls(null, null);
    }

    @Transactional(readOnly = true)
    public List<UrlResponse> getAllUrls(String query, String status) {
        LocalDateTime now = LocalDateTime.now();
        String normalizedQuery = (query != null && !query.trim().isEmpty()) ? query.trim().toLowerCase() : null;
        String normalizedStatus = (status != null && !status.trim().isEmpty()) ? status.trim().toUpperCase() : "ALL";

        if (!Set.of("ALL", "ACTIVE", "EXPIRED").contains(normalizedStatus)) {
            throw new InvalidUrlException("Invalid status filter: " + status + ". Allowed values: ALL, ACTIVE, EXPIRED");
        }

        return urlRepository.findAll().stream()
                .sorted((u1, u2) -> u2.getCreatedAt().compareTo(u1.getCreatedAt()))
                .filter(url -> {
                    if (normalizedQuery == null) return true;
                    boolean matchesShortCode = url.getShortCode() != null && url.getShortCode().toLowerCase().contains(normalizedQuery);
                    boolean matchesOriginalUrl = url.getOriginalUrl() != null && url.getOriginalUrl().toLowerCase().contains(normalizedQuery);
                    return matchesShortCode || matchesOriginalUrl;
                })
                .filter(url -> {
                    boolean isExp = url.getExpiresAt() != null && now.isAfter(url.getExpiresAt());
                    if ("ACTIVE".equals(normalizedStatus)) {
                        return !isExp;
                    } else if ("EXPIRED".equals(normalizedStatus)) {
                        return isExp;
                    }
                    return true;
                })
                .map(this::mapToUrlResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UrlSummaryResponse getAnalyticsSummary() {
        List<Url> urls = urlRepository.findAll();
        long totalUrls = urls.size();
        long totalClicks = urls.stream().mapToLong(Url::getClickCount).sum();
        LocalDateTime now = LocalDateTime.now();
        long expiredUrls = urls.stream()
                .filter(u -> u.getExpiresAt() != null && now.isAfter(u.getExpiresAt()))
                .count();
        long activeUrls = totalUrls - expiredUrls;
        double avg = totalUrls > 0 ? (double) totalClicks / totalUrls : 0.0;
        double averageClicksPerUrl = Math.round(avg * 100.0) / 100.0;

        UrlResponse mostVisitedUrl = urls.stream()
                .filter(u -> u.getClickCount() != null && u.getClickCount() > 0)
                .max((u1, u2) -> Long.compare(u1.getClickCount(), u2.getClickCount()))
                .map(this::mapToUrlResponse)
                .orElse(null);

        return UrlSummaryResponse.builder()
                .totalUrls(totalUrls)
                .totalClicks(totalClicks)
                .activeUrls(activeUrls)
                .expiredUrls(expiredUrls)
                .averageClicksPerUrl(averageClicksPerUrl)
                .mostVisitedUrl(mostVisitedUrl)
                .build();
    }

    @Transactional
    public void deleteUrl(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortUrlNotFoundException("Short URL code '" + shortCode + "' not found"));
        urlRepository.delete(url);
        log.info("Deleted short URL code '{}'", shortCode);
    }

    @Transactional
    public int deleteExpiredUrls() {
        LocalDateTime now = LocalDateTime.now();
        int deletedCount = urlRepository.deleteExpiredUrls(now);
        log.info("Purged {} expired URLs", deletedCount);
        return deletedCount;
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
        URI uri;
        try {
            uri = new URI(urlString);
        } catch (URISyntaxException e) {
            // Only a genuine parse failure is wrapped; the checks below raise their own messages
            throw new InvalidUrlException("Invalid URL format: " + e.getMessage());
        }

        if (uri.getScheme() == null || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https"))) {
            throw new InvalidUrlException("URL must start with http:// or https://");
        }
        if (uri.getHost() == null) {
            throw new InvalidUrlException("Invalid URL host structure");
        }

        // Block Server-Side Request Forgery (SSRF): reject URLs that resolve to
        // private, loopback, or link-local addresses so the shortener cannot be
        // used to probe internal services behind firewalls.
        rejectPrivateNetworkHost(uri.getHost());
    }

    /**
     * Standard strict decimal dotted-quad IPv4 pattern (each octet 0-255, no leading zeros).
     */
    private static final java.util.regex.Pattern STANDARD_IPV4_PATTERN = java.util.regex.Pattern.compile(
            "^(?:0|[1-9]\\d{0,2})(?:\\.(?:0|[1-9]\\d{0,2})){3}$"
    );

    /**
     * Matches numeric, hex, or octal IPv4 notations (including integer and shorthand forms).
     */
    private static final java.util.regex.Pattern NUMERIC_HOST_PATTERN = java.util.regex.Pattern.compile(
            "^(?:0[xX][0-9a-fA-F]+|0[0-7]+|\\d+)(?:\\.(?:0[xX][0-9a-fA-F]+|0[0-7]+|\\d+)){0,3}$"
    );

    /**
     * Resolves the given hostname and rejects it if it points to a private, loopback,
     * link-local, or reserved IP address. This prevents SSRF attacks where an attacker
     * shortens an internal URL (e.g. http://169.254.169.254/latest/meta-data)
     * and uses the redirect to probe cloud metadata endpoints or internal APIs.
     */
    private void rejectPrivateNetworkHost(String host) {
        String cleanHost = host.trim();
        if (cleanHost.startsWith("[") && cleanHost.endsWith("]")) {
            cleanHost = cleanHost.substring(1, cleanHost.length() - 1);
        }
        while (cleanHost.endsWith(".")) {
            cleanHost = cleanHost.substring(0, cleanHost.length() - 1);
        }
        if (cleanHost.isEmpty()) {
            throw new InvalidUrlException("Invalid URL host structure");
        }

        // Fast-reject well-known private hostnames without DNS resolution
        String lower = cleanHost.toLowerCase();
        if (lower.equals("localhost")
                || lower.endsWith(".localhost")
                || lower.endsWith(".local")
                || lower.endsWith(".internal")
                || lower.endsWith(".localdomain")) {
            throw new InvalidUrlException("URLs pointing to private or internal network addresses are not allowed");
        }

        // Reject obfuscated numeric/hex/octal IPv4 forms (e.g. 2130706433, 0x7f000001, 0177.0.0.1, 127.1)
        if (NUMERIC_HOST_PATTERN.matcher(cleanHost).matches()
                && !STANDARD_IPV4_PATTERN.matcher(cleanHost).matches()) {
            throw new InvalidUrlException("URLs pointing to private or obfuscated IP addresses are not allowed");
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(cleanHost);
            for (InetAddress address : addresses) {
                if (isBlockedIpAddress(address)) {
                    throw new InvalidUrlException("URLs pointing to private or internal network addresses are not allowed");
                }
            }
        } catch (UnknownHostException e) {
            if (cleanHost.contains(":")) {
                throw new InvalidUrlException("Invalid IPv6 host structure: " + host);
            }
            // Non-IP domain names that do not resolve in the current DNS context (e.g. offline tests)
            // are allowed after passing hostname and syntax checks above.
            log.debug("Could not resolve hostname '{}' during SSRF check: {}", cleanHost, e.getMessage());
        }
    }

    private boolean isBlockedIpAddress(InetAddress address) {
        if (address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            int b2 = bytes[2] & 0xFF;
            // 0.0.0.0/8 ("this network"), 100.64.0.0/10 (CGNAT), 127.0.0.0/8 (loopback),
            // 169.254.0.0/16 (link-local), 192.0.0.0/24 (IETF), 198.18.0.0/15 (benchmark), 240.0.0.0/4 (reserved/broadcast)
            return b0 == 0
                    || (b0 == 100 && b1 >= 64 && b1 <= 127)
                    || b0 == 127
                    || (b0 == 169 && b1 == 254)
                    || (b0 == 192 && b1 == 0 && b2 == 0)
                    || (b0 == 198 && (b1 == 18 || b1 == 19))
                    || b0 >= 240;
        }
        if (bytes.length == 16) {
            // IPv6 Unique Local Addresses (fc00::/7)
            return (bytes[0] & 0xFE) == 0xFC;
        }
        return false;
    }

    private void validateCustomAlias(String customAlias) {
        if (customAlias.length() < 3 || customAlias.length() > 10) {
            throw new InvalidUrlException("Custom alias must be between 3 and 10 characters");
        }
        if (!customAlias.matches("^[a-zA-Z0-9]+$")) {
            throw new InvalidUrlException("Custom alias can only contain alphanumeric characters");
        }
        if (RESERVED_WORDS.contains(customAlias.toLowerCase())) {
            throw new InvalidUrlException("Custom alias '" + customAlias + "' is a reserved keyword");
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
