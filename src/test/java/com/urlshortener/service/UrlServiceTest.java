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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private ShortCodeGenerator shortCodeGenerator;

    @Mock
    private QrCodeService qrCodeService;

    @InjectMocks
    private UrlService urlService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(urlService, "baseUrl", "http://localhost:8080");
    }

    @Test
    @DisplayName("Create Short URL - Success with Valid URL")
    void createShortUrl_Success() {
        CreateUrlRequest request = CreateUrlRequest.builder()
                .originalUrl("https://example.com/test-page")
                .build();

        when(shortCodeGenerator.generateShortCode()).thenReturn("abc123");
        when(urlRepository.existsByShortCode("abc123")).thenReturn(false);

        Url savedEntity = Url.builder()
                .id(1L)
                .originalUrl("https://example.com/test-page")
                .shortCode("abc123")
                .clickCount(0L)
                .createdAt(LocalDateTime.now())
                .build();

        when(urlRepository.save(any(Url.class))).thenReturn(savedEntity);

        UrlResponse response = urlService.createShortUrl(request);

        assertNotNull(response);
        assertEquals("abc123", response.getShortCode());
        assertEquals("http://localhost:8080/abc123", response.getShortUrl());
        assertEquals("https://example.com/test-page", response.getOriginalUrl());
        assertEquals(0L, response.getClickCount());
    }

    @Test
    @DisplayName("Create Short URL - Invalid URL format throws InvalidUrlException")
    void createShortUrl_InvalidUrl_ThrowsException() {
        CreateUrlRequest request = CreateUrlRequest.builder()
                .originalUrl("not-a-valid-url")
                .build();

        assertThrows(InvalidUrlException.class, () -> urlService.createShortUrl(request));
    }

    @Test
    @DisplayName("Create Short URL - Expired Date in Past throws InvalidUrlException")
    void createShortUrl_PastExpiration_ThrowsException() {
        CreateUrlRequest request = CreateUrlRequest.builder()
                .originalUrl("https://example.com")
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        assertThrows(InvalidUrlException.class, () -> urlService.createShortUrl(request));
    }

    @Test
    @DisplayName("Redirect - Success and Click Counter Increment")
    void getOriginalUrlAndLogClick_Success() {
        Url url = Url.builder()
                .id(1L)
                .originalUrl("https://example.com")
                .shortCode("abc123")
                .clickCount(5L)
                .createdAt(LocalDateTime.now())
                .build();

        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(url));

        String redirectUrl = urlService.getOriginalUrlAndLogClick("abc123");

        assertEquals("https://example.com", redirectUrl);
        // The counter is bumped by an atomic UPDATE rather than a read-modify-write save,
        // so concurrent visits to the same code cannot lose clicks.
        verify(urlRepository, times(1)).incrementClickCount(1L);
        verify(urlRepository, never()).save(any(Url.class));
    }

    @Test
    @DisplayName("Redirect - Non Existing Code throws ShortUrlNotFoundException")
    void getOriginalUrlAndLogClick_NotFound_ThrowsException() {
        when(urlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThrows(ShortUrlNotFoundException.class, () -> urlService.getOriginalUrlAndLogClick("missing"));
    }

    @Test
    @DisplayName("Redirect - Expired URL throws ExpiredUrlException")
    void getOriginalUrlAndLogClick_Expired_ThrowsException() {
        Url expiredUrl = Url.builder()
                .id(1L)
                .originalUrl("https://example.com")
                .shortCode("exp123")
                .clickCount(2L)
                .createdAt(LocalDateTime.now().minusDays(10))
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        when(urlRepository.findByShortCode("exp123")).thenReturn(Optional.of(expiredUrl));

        assertThrows(ExpiredUrlException.class, () -> urlService.getOriginalUrlAndLogClick("exp123"));
    }

    @Test
    @DisplayName("Get Stats - Returns correct Active Stats")
    void getStats_Active() {
        Url url = Url.builder()
                .id(1L)
                .originalUrl("https://example.com")
                .shortCode("abc123")
                .clickCount(10L)
                .createdAt(LocalDateTime.now())
                .build();

        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(url));

        UrlStatsResponse stats = urlService.getStats("abc123");

        assertEquals("abc123", stats.getShortCode());
        assertEquals(10L, stats.getClickCount());
        assertEquals("ACTIVE", stats.getStatus());
    }

    @Test
    @DisplayName("Delete URL - Success")
    void deleteUrl_Success() {
        Url url = Url.builder()
                .id(1L)
                .shortCode("abc123")
                .build();

        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(url));

        urlService.deleteUrl("abc123");

        verify(urlRepository, times(1)).delete(url);
    }

    @Test
    @DisplayName("Delete URL - Non Existing Code throws ShortUrlNotFoundException")
    void deleteUrl_NotFound() {
        when(urlRepository.findByShortCode("nonexist")).thenReturn(Optional.empty());

        assertThrows(ShortUrlNotFoundException.class, () -> urlService.deleteUrl("nonexist"));
    }

    // ============================================================
    // NEW TESTS — previously uncovered branches
    // ============================================================

    @Test
    @DisplayName("Create Short URL - URL with FTP scheme throws InvalidUrlException")
    void createShortUrl_FtpScheme_ThrowsException() {
        CreateUrlRequest request = CreateUrlRequest.builder()
                .originalUrl("ftp://files.example.com/data.zip")
                .build();

        InvalidUrlException ex = assertThrows(InvalidUrlException.class,
                () -> urlService.createShortUrl(request));
        assertTrue(ex.getMessage().contains("http://") || ex.getMessage().contains("https://"));
    }

    @Test
    @DisplayName("Create Short URL - URL with no host throws InvalidUrlException")
    void createShortUrl_NoHost_ThrowsException() {
        CreateUrlRequest request = CreateUrlRequest.builder()
                .originalUrl("http://")
                .build();

        assertThrows(InvalidUrlException.class, () -> urlService.createShortUrl(request));
    }

    @Test
    @DisplayName("Create Short URL - HTTPS scheme is accepted")
    void createShortUrl_HttpsScheme_Success() {
        CreateUrlRequest request = CreateUrlRequest.builder()
                .originalUrl("https://secure.example.com/page")
                .build();

        when(shortCodeGenerator.generateShortCode()).thenReturn("sec456");
        when(urlRepository.existsByShortCode("sec456")).thenReturn(false);

        Url savedEntity = Url.builder()
                .id(2L)
                .originalUrl("https://secure.example.com/page")
                .shortCode("sec456")
                .clickCount(0L)
                .createdAt(LocalDateTime.now())
                .build();

        when(urlRepository.save(any(Url.class))).thenReturn(savedEntity);

        UrlResponse response = urlService.createShortUrl(request);

        assertEquals("sec456", response.getShortCode());
        assertEquals("https://secure.example.com/page", response.getOriginalUrl());
    }

    @Test
    @DisplayName("Create Short URL - With valid future expiration date succeeds")
    void createShortUrl_WithFutureExpiration_Success() {
        LocalDateTime futureDate = LocalDateTime.now().plusDays(30);
        CreateUrlRequest request = CreateUrlRequest.builder()
                .originalUrl("https://example.com")
                .expiresAt(futureDate)
                .build();

        when(shortCodeGenerator.generateShortCode()).thenReturn("fut789");
        when(urlRepository.existsByShortCode("fut789")).thenReturn(false);

        Url savedEntity = Url.builder()
                .id(3L)
                .originalUrl("https://example.com")
                .shortCode("fut789")
                .clickCount(0L)
                .createdAt(LocalDateTime.now())
                .expiresAt(futureDate)
                .build();

        when(urlRepository.save(any(Url.class))).thenReturn(savedEntity);

        UrlResponse response = urlService.createShortUrl(request);

        assertEquals("fut789", response.getShortCode());
        assertEquals(futureDate, response.getExpiresAt());
    }

    @Test
    @DisplayName("Create Short URL - Short code collision triggers retry")
    void createShortUrl_CollisionRetry_Success() {
        CreateUrlRequest request = CreateUrlRequest.builder()
                .originalUrl("https://example.com")
                .build();

        // First code collides, second succeeds
        when(shortCodeGenerator.generateShortCode())
                .thenReturn("col111")
                .thenReturn("col222");
        when(urlRepository.existsByShortCode("col111")).thenReturn(true);
        when(urlRepository.existsByShortCode("col222")).thenReturn(false);

        Url savedEntity = Url.builder()
                .id(4L)
                .originalUrl("https://example.com")
                .shortCode("col222")
                .clickCount(0L)
                .createdAt(LocalDateTime.now())
                .build();

        when(urlRepository.save(any(Url.class))).thenReturn(savedEntity);

        UrlResponse response = urlService.createShortUrl(request);

        assertEquals("col222", response.getShortCode());
        verify(shortCodeGenerator, times(2)).generateShortCode();
    }

    @Test
    @DisplayName("Create Short URL - Exhausted retries throws IllegalStateException")
    void createShortUrl_AllCollisions_ThrowsIllegalStateException() {
        CreateUrlRequest request = CreateUrlRequest.builder()
                .originalUrl("https://example.com")
                .build();

        // All 5 retries produce existing codes
        when(shortCodeGenerator.generateShortCode()).thenReturn("exist1");
        when(urlRepository.existsByShortCode("exist1")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> urlService.createShortUrl(request));
        verify(shortCodeGenerator, times(5)).generateShortCode();
    }

    @Test
    @DisplayName("Get Stats - Returns EXPIRED status for expired URL")
    void getStats_Expired_ReturnsExpiredStatus() {
        Url expiredUrl = Url.builder()
                .id(5L)
                .originalUrl("https://example.com")
                .shortCode("exp999")
                .clickCount(3L)
                .createdAt(LocalDateTime.now().minusDays(10))
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        when(urlRepository.findByShortCode("exp999")).thenReturn(Optional.of(expiredUrl));

        UrlStatsResponse stats = urlService.getStats("exp999");

        assertEquals("EXPIRED", stats.getStatus());
        assertEquals("exp999", stats.getShortCode());
        assertEquals(3L, stats.getClickCount());
    }

    @Test
    @DisplayName("Get Stats - Non existing code throws ShortUrlNotFoundException")
    void getStats_NotFound_ThrowsException() {
        when(urlRepository.findByShortCode("nocode")).thenReturn(Optional.empty());

        assertThrows(ShortUrlNotFoundException.class, () -> urlService.getStats("nocode"));
    }

    @Test
    @DisplayName("Get All URLs - Returns list sorted by createdAt descending")
    void getAllUrls_ReturnsSortedList() {
        LocalDateTime older = LocalDateTime.now().minusDays(2);
        LocalDateTime newer = LocalDateTime.now();

        Url url1 = Url.builder()
                .id(1L).originalUrl("https://old.com").shortCode("old111")
                .clickCount(0L).createdAt(older).build();
        Url url2 = Url.builder()
                .id(2L).originalUrl("https://new.com").shortCode("new222")
                .clickCount(0L).createdAt(newer).build();

        when(urlRepository.findAll()).thenReturn(java.util.List.of(url1, url2));

        java.util.List<UrlResponse> results = urlService.getAllUrls();

        assertEquals(2, results.size());
        // Newest first
        assertEquals("new222", results.get(0).getShortCode());
        assertEquals("old111", results.get(1).getShortCode());
    }

    @Test
    @DisplayName("Get All URLs - Empty database returns empty list")
    void getAllUrls_EmptyDatabase_ReturnsEmptyList() {
        when(urlRepository.findAll()).thenReturn(java.util.List.of());

        java.util.List<UrlResponse> results = urlService.getAllUrls();

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Create Short URL - Success with custom alias")
    void createShortUrl_WithCustomAlias_Success() {
        CreateUrlRequest request = CreateUrlRequest.builder()
                .originalUrl("https://example.com/custom-page")
                .customAlias("mybrand")
                .build();

        when(urlRepository.existsByShortCode("mybrand")).thenReturn(false);

        Url savedEntity = Url.builder()
                .id(99L)
                .originalUrl("https://example.com/custom-page")
                .shortCode("mybrand")
                .clickCount(0L)
                .createdAt(LocalDateTime.now())
                .build();

        when(urlRepository.save(any(Url.class))).thenReturn(savedEntity);

        UrlResponse response = urlService.createShortUrl(request);

        assertNotNull(response);
        assertEquals("mybrand", response.getShortCode());
        assertEquals("http://localhost:8080/mybrand", response.getShortUrl());
        verify(shortCodeGenerator, never()).generateShortCode();
    }

    @Test
    @DisplayName("Create Short URL - Duplicate custom alias throws CustomAliasAlreadyExistsException")
    void createShortUrl_DuplicateCustomAlias_ThrowsConflict() {
        CreateUrlRequest request = CreateUrlRequest.builder()
                .originalUrl("https://example.com")
                .customAlias("existing")
                .build();

        when(urlRepository.existsByShortCode("existing")).thenReturn(true);

        assertThrows(CustomAliasAlreadyExistsException.class, () -> urlService.createShortUrl(request));
        verify(urlRepository, never()).save(any(Url.class));
    }

    @Test
    @DisplayName("Create Short URL - Reserved keyword custom alias throws InvalidUrlException")
    void createShortUrl_ReservedKeyword_ThrowsInvalidUrlException() {
        CreateUrlRequest request = CreateUrlRequest.builder()
                .originalUrl("https://example.com")
                .customAlias("api")
                .build();

        InvalidUrlException ex = assertThrows(InvalidUrlException.class, () -> urlService.createShortUrl(request));
        assertTrue(ex.getMessage().contains("reserved"));
        verify(urlRepository, never()).save(any(Url.class));
    }

    @Test
    @DisplayName("Create Short URL - Custom alias with invalid characters throws InvalidUrlException")
    void createShortUrl_InvalidCharsCustomAlias_ThrowsInvalidUrlException() {
        CreateUrlRequest request = CreateUrlRequest.builder()
                .originalUrl("https://example.com")
                .customAlias("bad@alias!")
                .build();

        InvalidUrlException ex = assertThrows(InvalidUrlException.class, () -> urlService.createShortUrl(request));
        assertTrue(ex.getMessage().contains("alphanumeric"));
    }

    @Test
    @DisplayName("Create Short URL - Custom alias shorter than 3 chars throws InvalidUrlException")
    void createShortUrl_TooShortCustomAlias_ThrowsInvalidUrlException() {
        CreateUrlRequest request = CreateUrlRequest.builder()
                .originalUrl("https://example.com")
                .customAlias("ab")
                .build();

        InvalidUrlException ex = assertThrows(InvalidUrlException.class, () -> urlService.createShortUrl(request));
        assertTrue(ex.getMessage().contains("between 3 and 10"));
    }

    @Test
    @DisplayName("Get Analytics Summary - Computes correct aggregate metrics")
    void getAnalyticsSummary_WithLinks_ReturnsAccurateMetrics() {
        Url activeUrl1 = Url.builder()
                .id(1L)
                .originalUrl("https://example.com/one")
                .shortCode("one111")
                .clickCount(10L)
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();

        Url activeUrl2 = Url.builder()
                .id(2L)
                .originalUrl("https://example.com/two")
                .shortCode("two222")
                .clickCount(30L)
                .createdAt(LocalDateTime.now())
                .build();

        Url expiredUrl = Url.builder()
                .id(3L)
                .originalUrl("https://example.com/expired")
                .shortCode("exp333")
                .clickCount(5L)
                .createdAt(LocalDateTime.now().minusDays(5))
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        when(urlRepository.findAll()).thenReturn(java.util.List.of(activeUrl1, activeUrl2, expiredUrl));

        UrlSummaryResponse summary = urlService.getAnalyticsSummary();

        assertNotNull(summary);
        assertEquals(3L, summary.getTotalUrls());
        assertEquals(45L, summary.getTotalClicks());
        assertEquals(2L, summary.getActiveUrls());
        assertEquals(1L, summary.getExpiredUrls());
        assertEquals(15.0, summary.getAverageClicksPerUrl());
        assertNotNull(summary.getMostVisitedUrl());
        assertEquals("two222", summary.getMostVisitedUrl().getShortCode());
    }

    @Test
    @DisplayName("Get Analytics Summary - Empty database returns zeros and null mostVisited")
    void getAnalyticsSummary_EmptyDatabase_ReturnsZeros() {
        when(urlRepository.findAll()).thenReturn(java.util.List.of());

        UrlSummaryResponse summary = urlService.getAnalyticsSummary();

        assertNotNull(summary);
        assertEquals(0L, summary.getTotalUrls());
        assertEquals(0L, summary.getTotalClicks());
        assertEquals(0L, summary.getActiveUrls());
        assertEquals(0L, summary.getExpiredUrls());
        assertEquals(0.0, summary.getAverageClicksPerUrl());
        assertNull(summary.getMostVisitedUrl());
    }

    @Test
    @DisplayName("Delete Expired URLs - Successfully calls repository and returns deleted count")
    void deleteExpiredUrls_Success() {
        when(urlRepository.deleteExpiredUrls(any(LocalDateTime.class))).thenReturn(4);

        int count = urlService.deleteExpiredUrls();

        assertEquals(4, count);
        verify(urlRepository, times(1)).deleteExpiredUrls(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Create Short URL - Reserved keyword 'expired' as custom alias throws InvalidUrlException")
    void createShortUrl_ReservedKeywordExpired_ThrowsException() {
        CreateUrlRequest request = CreateUrlRequest.builder()
                .originalUrl("https://example.com/test")
                .customAlias("expired")
                .build();

        InvalidUrlException ex = assertThrows(InvalidUrlException.class,
                () -> urlService.createShortUrl(request));
        assertTrue(ex.getMessage().contains("reserved keyword"));
    }

    @Test
    @DisplayName("Get QR Code - Success returns byte array")
    void getQrCodeForShortCode_Success() {
        Url url = Url.builder()
                .id(1L)
                .shortCode("abc123")
                .originalUrl("https://example.com")
                .createdAt(LocalDateTime.now())
                .build();
        byte[] dummyPng = new byte[]{1, 2, 3};

        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(url));
        when(qrCodeService.generateQrCode("http://localhost:8080/abc123", 300, 300)).thenReturn(dummyPng);

        byte[] result = urlService.getQrCodeForShortCode("abc123", 300);

        assertArrayEquals(dummyPng, result);
        verify(qrCodeService, times(1)).generateQrCode("http://localhost:8080/abc123", 300, 300);
    }

    @Test
    @DisplayName("Get QR Code - Clamps size between 64 and 1024")
    void getQrCodeForShortCode_ClampsSize() {
        Url url = Url.builder()
                .id(1L)
                .shortCode("clamp1")
                .originalUrl("https://example.com")
                .createdAt(LocalDateTime.now())
                .build();
        byte[] dummyPng = new byte[]{1, 2, 3};

        when(urlRepository.findByShortCode("clamp1")).thenReturn(Optional.of(url));
        when(qrCodeService.generateQrCode(anyString(), eq(64), eq(64))).thenReturn(dummyPng);

        byte[] result = urlService.getQrCodeForShortCode("clamp1", 10); // Underflow to 64
        assertNotNull(result);
        verify(qrCodeService).generateQrCode("http://localhost:8080/clamp1", 64, 64);
    }

    @Test
    @DisplayName("Get QR Code - Non-existent short code throws ShortUrlNotFoundException")
    void getQrCodeForShortCode_NotFound_ThrowsException() {
        when(urlRepository.findByShortCode("ghost9")).thenReturn(Optional.empty());

        assertThrows(ShortUrlNotFoundException.class,
                () -> urlService.getQrCodeForShortCode("ghost9", 300));
    }

    @Test
    @DisplayName("Get QR Code - Expired short code throws ExpiredUrlException")
    void getQrCodeForShortCode_Expired_ThrowsException() {
        Url expiredUrl = Url.builder()
                .id(1L)
                .shortCode("exp123")
                .originalUrl("https://example.com")
                .createdAt(LocalDateTime.now().minusDays(2))
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        when(urlRepository.findByShortCode("exp123")).thenReturn(Optional.of(expiredUrl));

        assertThrows(ExpiredUrlException.class,
                () -> urlService.getQrCodeForShortCode("exp123", 300));
    }

    @Test
    @DisplayName("Get All URLs - Filter by query matches originalUrl or shortCode case-insensitively")
    void getAllUrls_WithQueryFilter_Success() {
        Url u1 = Url.builder().id(1L).shortCode("github1").originalUrl("https://github.com/spring-projects").createdAt(LocalDateTime.now()).build();
        Url u2 = Url.builder().id(2L).shortCode("docker2").originalUrl("https://hub.docker.com/r/library").createdAt(LocalDateTime.now().minusMinutes(1)).build();
        when(urlRepository.findAll()).thenReturn(List.of(u1, u2));

        List<UrlResponse> resultsCode = urlService.getAllUrls("github", "ALL");
        assertEquals(1, resultsCode.size());
        assertEquals("github1", resultsCode.get(0).getShortCode());

        List<UrlResponse> resultsUrl = urlService.getAllUrls("DOCKER", "ALL");
        assertEquals(1, resultsUrl.size());
        assertEquals("docker2", resultsUrl.get(0).getShortCode());
    }

    @Test
    @DisplayName("Get All URLs - Filter by status returns only active or expired links")
    void getAllUrls_WithStatusFilter_Success() {
        Url activeUrl = Url.builder().id(1L).shortCode("act1").originalUrl("https://example.com/act")
                .createdAt(LocalDateTime.now()).expiresAt(LocalDateTime.now().plusDays(1)).build();
        Url expiredUrl = Url.builder().id(2L).shortCode("exp1").originalUrl("https://example.com/exp")
                .createdAt(LocalDateTime.now().minusDays(2)).expiresAt(LocalDateTime.now().minusDays(1)).build();
        when(urlRepository.findAll()).thenReturn(List.of(activeUrl, expiredUrl));

        List<UrlResponse> activeResults = urlService.getAllUrls(null, "ACTIVE");
        assertEquals(1, activeResults.size());
        assertEquals("act1", activeResults.get(0).getShortCode());

        List<UrlResponse> expiredResults = urlService.getAllUrls(null, "EXPIRED");
        assertEquals(1, expiredResults.size());
        assertEquals("exp1", expiredResults.get(0).getShortCode());
    }

    @Test
    @DisplayName("Get All URLs - Invalid status filter throws InvalidUrlException")
    void getAllUrls_InvalidStatus_ThrowsException() {
        InvalidUrlException ex = assertThrows(InvalidUrlException.class,
                () -> urlService.getAllUrls(null, "UNKNOWN_STATUS"));
        assertTrue(ex.getMessage().contains("Invalid status filter"));
    }
}

