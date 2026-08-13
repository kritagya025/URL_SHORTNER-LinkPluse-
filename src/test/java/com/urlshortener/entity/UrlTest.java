package com.urlshortener.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UrlTest {

    @Test
    @DisplayName("prePersist sets createdAt when it is null")
    void prePersist_SetsCreatedAtWhenNull() {
        Url url = new Url();
        assertNull(url.getCreatedAt());
        url.prePersist();
        assertNotNull(url.getCreatedAt());
    }

    @Test
    @DisplayName("prePersist does NOT override existing createdAt")
    void prePersist_DoesNotOverrideExistingCreatedAt() {
        LocalDateTime existingTime = LocalDateTime.of(2020, 1, 1, 12, 0);
        Url url = new Url();
        url.setCreatedAt(existingTime);
        url.prePersist();
        assertEquals(existingTime, url.getCreatedAt());
    }

    @Test
    @DisplayName("prePersist defaults clickCount to 0 when null")
    void prePersist_DefaultsClickCountWhenNull() {
        Url url = new Url();
        url.setClickCount(null);
        url.prePersist();
        assertEquals(0L, url.getClickCount());
    }

    @Test
    @DisplayName("prePersist does NOT override existing clickCount")
    void prePersist_DoesNotOverrideExistingClickCount() {
        Url url = new Url();
        url.setClickCount(42L);
        url.prePersist();
        assertEquals(42L, url.getClickCount());
    }

    @Test
    @DisplayName("Builder creates entity with all fields set")
    void builder_CreatesEntityWithAllFields() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = now.plusDays(7);
        Url url = Url.builder()
                .id(1L)
                .originalUrl("https://example.com")
                .shortCode("abc123")
                .clickCount(5L)
                .createdAt(now)
                .expiresAt(expires)
                .build();

        assertEquals(1L, url.getId());
        assertEquals("https://example.com", url.getOriginalUrl());
        assertEquals("abc123", url.getShortCode());
        assertEquals(5L, url.getClickCount());
        assertEquals(now, url.getCreatedAt());
        assertEquals(expires, url.getExpiresAt());
    }
}
