package com.urlshortener.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator generator = new ShortCodeGenerator();

    @Test
    @DisplayName("Generated code is exactly 6 characters long")
    void generateShortCode_HasCorrectLength() {
        String code = generator.generateShortCode();
        assertNotNull(code);
        assertEquals(6, code.length());
    }

    @Test
    @DisplayName("Generated code contains only Base62 characters [a-zA-Z0-9]")
    void generateShortCode_ContainsOnlyBase62Chars() {
        for (int i = 0; i < 100; i++) {
            String code = generator.generateShortCode();
            assertTrue(code.matches("^[a-zA-Z0-9]{6}$"),
                    "Code '" + code + "' contains invalid characters");
        }
    }

    @Test
    @DisplayName("Multiple generated codes are not all identical (randomness check)")
    void generateShortCode_ProducesDifferentCodes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            codes.add(generator.generateShortCode());
        }
        // With 62^6 possible codes, 50 calls should produce at least 2 unique codes
        assertTrue(codes.size() > 1,
                "Expected multiple unique codes but got only " + codes.size());
    }
}
