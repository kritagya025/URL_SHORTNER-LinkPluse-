package com.urlshortener.exception;

/**
 * Custom runtime exception thrown when a submitted URL format is malformed or invalid.
 */
public class InvalidUrlException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidUrlException(String message) {
        super(message);
    }
}
