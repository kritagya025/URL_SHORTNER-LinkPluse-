package com.urlshortener.exception;

/**
 * Custom runtime exception thrown when a requested short code cannot be found in the database.
 */
public class ShortUrlNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ShortUrlNotFoundException(String message) {
        super(message);
    }
}
