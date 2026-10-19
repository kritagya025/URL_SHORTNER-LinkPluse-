package com.urlshortener.exception;

/**
 * Exception thrown when a user requests a custom short code alias that is already in use.
 */
public class CustomAliasAlreadyExistsException extends RuntimeException {
    public CustomAliasAlreadyExistsException(String message) {
        super(message);
    }
}
