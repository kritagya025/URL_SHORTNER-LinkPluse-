package com.urlshortener.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    @Test
    @DisplayName("Filter applies standard OWASP security headers and CSP to HTTP response")
    void doFilter_AppliesSecurityHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/urls");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("SAMEORIGIN", response.getHeader("X-Frame-Options"));
        assertEquals("1; mode=block", response.getHeader("X-XSS-Protection"));
        assertEquals("strict-origin-when-cross-origin", response.getHeader("Referrer-Policy"));
        assertEquals("camera=(), microphone=(), geolocation=()", response.getHeader("Permissions-Policy"));

        String csp = response.getHeader("Content-Security-Policy");
        assertNotNull(csp, "Content-Security-Policy header should be present");
        assertTrue(csp.contains("default-src 'self'"));
        assertTrue(csp.contains("frame-ancestors 'self'"));

        verify(filterChain, times(1)).doFilter(request, response);
    }
}
