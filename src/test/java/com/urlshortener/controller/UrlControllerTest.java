package com.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.exception.ExpiredUrlException;
import com.urlshortener.exception.ShortUrlNotFoundException;
import com.urlshortener.service.UrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {UrlController.class, RedirectController.class})
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UrlService urlService;

    @Test
    @DisplayName("POST /api/urls - Success returns 201 Created")
    void createShortUrl_Success() throws Exception {
        CreateUrlRequest request = CreateUrlRequest.builder()
                .originalUrl("https://example.com/test")
                .build();

        UrlResponse response = UrlResponse.builder()
                .id(1L)
                .originalUrl("https://example.com/test")
                .shortCode("aB72x")
                .shortUrl("http://localhost:8080/aB72x")
                .clickCount(0L)
                .createdAt(LocalDateTime.now())
                .build();

        when(urlService.createShortUrl(any(CreateUrlRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("aB72x"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/aB72x"))
                .andExpect(jsonPath("$.clickCount").value(0));
    }

    @Test
    @DisplayName("POST /api/urls - Blank URL returns 400 Bad Request")
    void createShortUrl_BlankUrl_Returns400() throws Exception {
        CreateUrlRequest request = CreateUrlRequest.builder()
                .originalUrl("")
                .build();

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("GET /{shortCode} - Success Redirects with 302 Found")
    void redirect_Success() throws Exception {
        when(urlService.getOriginalUrlAndLogClick("aB72x")).thenReturn("https://example.com/target");

        mockMvc.perform(get("/aB72x"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/target"));
    }

    @Test
    @DisplayName("GET /{shortCode} - Expired URL returns 410 Gone")
    void redirect_Expired_Returns410() throws Exception {
        when(urlService.getOriginalUrlAndLogClick("exp123"))
                .thenThrow(new ExpiredUrlException("Short URL code 'exp123' has expired"));

        mockMvc.perform(get("/exp123"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.message").value("Short URL code 'exp123' has expired"));
    }

    @Test
    @DisplayName("GET /api/urls/{shortCode}/stats - Success returns stats")
    void getStats_Success() throws Exception {
        UrlStatsResponse stats = UrlStatsResponse.builder()
                .originalUrl("https://example.com")
                .shortCode("aB72x")
                .shortUrl("http://localhost:8080/aB72x")
                .clickCount(15L)
                .createdAt(LocalDateTime.now())
                .status("ACTIVE")
                .build();

        when(urlService.getStats("aB72x")).thenReturn(stats);

        mockMvc.perform(get("/api/urls/aB72x/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(15))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("DELETE /api/urls/{shortCode} - Success returns 204 No Content")
    void deleteUrl_Success() throws Exception {
        doNothing().when(urlService).deleteUrl("aB72x");

        mockMvc.perform(delete("/api/urls/aB72x"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/urls/{shortCode} - Non-existent returns 404 Not Found")
    void deleteUrl_NotFound() throws Exception {
        doThrow(new ShortUrlNotFoundException("Short URL code 'missing' not found"))
                .when(urlService).deleteUrl("missing");

        mockMvc.perform(delete("/api/urls/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ============================================================
    // NEW TESTS — previously uncovered controller paths
    // ============================================================

    @Test
    @DisplayName("GET /api/urls - Success returns list of URLs")
    void getAllUrls_Success() throws Exception {
        UrlResponse url1 = UrlResponse.builder()
                .id(1L).originalUrl("https://example.com").shortCode("abc11")
                .shortUrl("http://localhost:8080/abc11").clickCount(5L)
                .createdAt(LocalDateTime.now()).build();

        when(urlService.getAllUrls()).thenReturn(java.util.List.of(url1));

        mockMvc.perform(get("/api/urls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shortCode").value("abc11"))
                .andExpect(jsonPath("$[0].clickCount").value(5));
    }

    @Test
    @DisplayName("POST /api/urls - Invalid URL format returns 400 via InvalidUrlException")
    void createShortUrl_InvalidUrl_Returns400() throws Exception {
        CreateUrlRequest request = CreateUrlRequest.builder()
                .originalUrl("ftp://not-http.com")
                .build();

        when(urlService.createShortUrl(any(CreateUrlRequest.class)))
                .thenThrow(new com.urlshortener.exception.InvalidUrlException(
                        "URL must start with http:// or https://"));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("URL must start with http:// or https://"));
    }

    @Test
    @DisplayName("GET /{shortCode} - Non-existent code returns 404 Not Found")
    void redirect_NotFound_Returns404() throws Exception {
        when(urlService.getOriginalUrlAndLogClick("nocode"))
                .thenThrow(new ShortUrlNotFoundException("Short URL code 'nocode' not found"));

        mockMvc.perform(get("/nocode"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Short URL code 'nocode' not found"));
    }

    @Test
    @DisplayName("GET /api/urls/{shortCode}/stats - Non-existent returns 404")
    void getStats_NotFound_Returns404() throws Exception {
        when(urlService.getStats("nocode"))
                .thenThrow(new ShortUrlNotFoundException("Short URL code 'nocode' not found"));

        mockMvc.perform(get("/api/urls/nocode/stats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /api/urls - General exception returns 500")
    void createShortUrl_UnexpectedError_Returns500() throws Exception {
        CreateUrlRequest request = CreateUrlRequest.builder()
                .originalUrl("https://example.com")
                .build();

        when(urlService.createShortUrl(any(CreateUrlRequest.class)))
                .thenThrow(new RuntimeException("Database connection failed"));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").exists());
    }
}

