package com.urlshortener.exception;

/**
 * Custom runtime exception thrown when an accessed short URL link has reached its expiration date.
 */
public class ExpiredUrlException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ExpiredUrlException(String message) {
        super(message);
    }
}
