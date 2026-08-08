package com.urlshortener.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Service component responsible for generating randomized 6-character Base62 short codes.
 * Character set: 62 characters [a-z, A-Z, 0-9]
 * Total combinations for 6 characters: 62^6 = ~56.8 billion unique short codes.
 */
@Component
public class ShortCodeGenerator {

    private static final String BASE62_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;
    private final SecureRandom random = new SecureRandom();

    public String generateShortCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int randomIndex = random.nextInt(BASE62_CHARS.length());
            sb.append(BASE62_CHARS.charAt(randomIndex));
        }
        return sb.toString();
    }
}
