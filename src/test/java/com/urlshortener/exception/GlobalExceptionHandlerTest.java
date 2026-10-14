package com.urlshortener.exception;

import com.urlshortener.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleNotFound returns 404 with exception message")
    void handleNotFound_Returns404() {
        ShortUrlNotFoundException ex = new ShortUrlNotFoundException("Code 'abc' not found");
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("Code 'abc' not found", response.getBody().getMessage());
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    @DisplayName("handleExpired returns 410 with expiration message")
    void handleExpired_Returns410() {
        ExpiredUrlException ex = new ExpiredUrlException("Code 'xyz' has expired");
        ResponseEntity<ErrorResponse> response = handler.handleExpired(ex);

        assertEquals(HttpStatus.GONE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(410, response.getBody().getStatus());
        assertEquals("Code 'xyz' has expired", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleInvalidUrl returns 400 with invalid format message")
    void handleInvalidUrl_Returns400() {
        InvalidUrlException ex = new InvalidUrlException("URL must start with http:// or https://");
        ResponseEntity<ErrorResponse> response = handler.handleInvalidUrl(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("URL must start with http:// or https://", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleValidationErrors extracts first field error message")
    void handleValidationErrors_WithFieldError_Returns400() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("createUrlRequest", "originalUrl", "cannot be empty");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ErrorResponse> response = handler.handleValidationErrors(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("originalUrl: cannot be empty", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleValidationErrors falls back to default message when no field errors")
    void handleValidationErrors_EmptyFieldErrors_ReturnsDefaultMessage() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(Collections.emptyList());

        ResponseEntity<ErrorResponse> response = handler.handleValidationErrors(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Validation failed", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleNoHandler returns 404 without leaking internal exception trace")
    void handleNoHandler_Returns404() {
        NoHandlerFoundException ex = new NoHandlerFoundException("GET", "/unknown-route", null);
        ResponseEntity<ErrorResponse> response = handler.handleNoHandler(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("The requested resource was not found", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleGeneralException returns 500 with generic message for unanticipated errors")
    void handleGeneralException_Returns500GenericMessage() {
        Exception ex = new NullPointerException("Secret internal database connection string failed");
        ResponseEntity<ErrorResponse> response = handler.handleGeneralException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().getStatus());
        assertEquals("An unexpected error occurred", response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("Secret"), "Internal details must not leak");
    }
}
