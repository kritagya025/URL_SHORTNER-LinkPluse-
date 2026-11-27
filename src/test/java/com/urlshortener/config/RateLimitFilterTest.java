package com.urlshortener.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    @Test
    @DisplayName("Allows POST /api/urls requests up to limit and sets X-RateLimit headers")
    void doFilter_WithinLimit_AllowsAndSetsHeaders() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(3, 60_000L, Clock.fixed(Instant.ofEpochMilli(1_000_000L), ZoneOffset.UTC));
        FilterChain chain = mock(FilterChain.class);

        for (int i = 1; i <= 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/urls");
            request.setRemoteAddr("203.0.113.10");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus());
            assertEquals("3", response.getHeader("X-RateLimit-Limit"));
            assertEquals(String.valueOf(3 - i), response.getHeader("X-RateLimit-Remaining"));
            assertNotNull(response.getHeader("X-RateLimit-Reset"));
        }

        verify(chain, times(3)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Blocks POST /api/urls beyond limit with 429 Too Many Requests and Retry-After")
    void doFilter_ExceedsLimit_Returns429() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(2, 60_000L, Clock.fixed(Instant.ofEpochMilli(1_000_000L), ZoneOffset.UTC));
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/urls");
            req.setRemoteAddr("203.0.113.20");
            filter.doFilter(req, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/urls");
        blockedReq.setRemoteAddr("203.0.113.20");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();

        filter.doFilter(blockedReq, blockedRes, chain);

        assertEquals(429, blockedRes.getStatus());
        assertEquals("0", blockedRes.getHeader("X-RateLimit-Remaining"));
        assertNotNull(blockedRes.getHeader("Retry-After"));
        assertTrue(blockedRes.getContentAsString().contains("\"status\":429"));
        assertTrue(blockedRes.getContentAsString().contains("Rate limit exceeded"));
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Resets quota after time window elapses")
    void doFilter_WindowExpires_ResetsQuota() throws ServletException, IOException {
        AtomicLong nowMillis = new AtomicLong(1_000_000L);
        Clock mutableClock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(nowMillis.get());
            }
        };

        RateLimitFilter filter = new RateLimitFilter(1, 60_000L, mutableClock);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/urls");
        req1.setRemoteAddr("198.51.100.5");
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filter.doFilter(req1, res1, chain);
        assertEquals(200, res1.getStatus());

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/urls");
        req2.setRemoteAddr("198.51.100.5");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilter(req2, res2, chain);
        assertEquals(429, res2.getStatus());

        // Advance past the 60s window
        nowMillis.addAndGet(60_001L);

        MockHttpServletRequest req3 = new MockHttpServletRequest("POST", "/api/urls");
        req3.setRemoteAddr("198.51.100.5");
        MockHttpServletResponse res3 = new MockHttpServletResponse();
        filter.doFilter(req3, res3, chain);
        assertEquals(200, res3.getStatus());

        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Does not throttle GET, DELETE, or non-creation endpoints")
    void doFilter_NonCreationRequests_AreNotThrottled() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(1, 60_000L, Clock.systemUTC());
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest getUrls = new MockHttpServletRequest("GET", "/api/urls");
        MockHttpServletRequest redirect = new MockHttpServletRequest("GET", "/abc123");
        MockHttpServletRequest delete = new MockHttpServletRequest("DELETE", "/api/urls/abc123");

        filter.doFilter(getUrls, new MockHttpServletResponse(), chain);
        filter.doFilter(redirect, new MockHttpServletResponse(), chain);
        filter.doFilter(delete, new MockHttpServletResponse(), chain);

        verify(chain, times(3)).doFilter(any(), any());
        assertEquals(0, filter.trackedClientCount());
    }

    @Test
    @DisplayName("Tracks distinct clients independently via X-Forwarded-For")
    void doFilter_XForwardedFor_IsolatesClients() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(1, 60_000L, Clock.systemUTC());
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest clientA = new MockHttpServletRequest("POST", "/api/urls");
        clientA.setRemoteAddr("10.0.0.1");
        clientA.addHeader("X-Forwarded-For", "198.51.100.1, 10.0.0.1");

        MockHttpServletRequest clientB = new MockHttpServletRequest("POST", "/api/urls");
        clientB.setRemoteAddr("10.0.0.1");
        clientB.addHeader("X-Forwarded-For", "198.51.100.2, 10.0.0.1");

        MockHttpServletResponse resA = new MockHttpServletResponse();
        MockHttpServletResponse resB = new MockHttpServletResponse();

        filter.doFilter(clientA, resA, chain);
        filter.doFilter(clientB, resB, chain);

        assertEquals(200, resA.getStatus());
        assertEquals(200, resB.getStatus());
        assertEquals(2, filter.trackedClientCount());
    }
}
