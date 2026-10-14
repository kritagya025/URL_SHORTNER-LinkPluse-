package com.urlshortener.controller;

import com.urlshortener.exception.ExpiredUrlException;
import com.urlshortener.exception.ShortUrlNotFoundException;
import com.urlshortener.service.UrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RedirectController.class)
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlService urlService;

    @Test
    @DisplayName("GET /{shortCode} - Successful redirect returns 302 Found with Location header")
    void redirectToOriginalUrl_Success() throws Exception {
        String shortCode = "valid123";
        String destination = "https://example.org/destination/page";

        when(urlService.getOriginalUrlAndLogClick(shortCode)).thenReturn(destination);

        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", destination));
    }

    @Test
    @DisplayName("GET /{shortCode} - Expired link returns 410 Gone")
    void redirectToOriginalUrl_Expired_Returns410() throws Exception {
        String shortCode = "expired99";

        when(urlService.getOriginalUrlAndLogClick(shortCode))
                .thenThrow(new ExpiredUrlException("Short URL code 'expired99' has expired"));

        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.message").value("Short URL code 'expired99' has expired"));
    }

    @Test
    @DisplayName("GET /{shortCode} - Non-existent short code returns 404 Not Found")
    void redirectToOriginalUrl_NotFound_Returns404() throws Exception {
        String shortCode = "missing1";

        when(urlService.getOriginalUrlAndLogClick(shortCode))
                .thenThrow(new ShortUrlNotFoundException("Short URL code 'missing1' not found"));

        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Short URL code 'missing1' not found"));
    }

    @Test
    @DisplayName("GET /{shortCode} - Code exceeding 10 characters does not match redirect pattern")
    void redirectToOriginalUrl_CodeTooLong_DoesNotMatchRoute() throws Exception {
        String longCode = "abcdefghijk"; // 11 characters

        mockMvc.perform(get("/" + longCode))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
