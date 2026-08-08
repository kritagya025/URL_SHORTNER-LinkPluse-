package com.urlshortener.service;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.entity.Url;
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
        when(urlRepository.save(any(Url.class))).thenAnswer(i -> i.getArgument(0));

        String redirectUrl = urlService.getOriginalUrlAndLogClick("abc123");

        assertEquals("https://example.com", redirectUrl);
        assertEquals(6L, url.getClickCount());
        verify(urlRepository, times(1)).save(url);
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
}
