package com.urlshortener.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import static org.junit.jupiter.api.Assertions.*;

class WebConfigTest {

    private final WebConfig webConfig = new WebConfig();

    @Test
    @DisplayName("CorsFilter bean is correctly created and configured")
    void corsFilter_IsConfiguredProperly() {
        CorsFilter corsFilter = webConfig.corsFilter();
        assertNotNull(corsFilter, "CorsFilter bean should not be null");
    }

    @Test
    @DisplayName("CORS preflight request receives allowed headers and methods")
    void corsFilter_HandlesPreflightRequest() throws Exception {
        CorsFilter corsFilter = webConfig.corsFilter();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("OPTIONS");
        request.setRequestURI("/api/urls");
        request.addHeader("Origin", "http://localhost:3000");
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "content-type");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        corsFilter.doFilter(request, response, filterChain);

        assertEquals("http://localhost:3000", response.getHeader("Access-Control-Allow-Origin"));
        assertNotNull(response.getHeader("Access-Control-Allow-Methods"));
        assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("POST"));
        assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("GET"));
        assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("DELETE"));
        assertNull(response.getHeader("Access-Control-Allow-Credentials"), "Credentials should not be allowed");
    }
}
