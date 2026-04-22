package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.exception.NotFoundException;
import org.example.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String SMPP_UNAVAILABLE_MESSAGE =
            "SMPP simulator is not available. Start the SMPP server and try again.";

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException e,
                                                                  HttpServletRequest request) {
        log.warn("HTTP 401: {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException e,
                                                              HttpServletRequest request) {
        log.warn("HTTP 404: {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e,
                                                                HttpServletRequest request) {
        log.warn("HTTP 400: {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException e,
                                                              HttpServletRequest request) {
        log.warn("HTTP 409: {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(SecurityException e,
                                                               HttpServletRequest request) {
        log.warn("HTTP 403: {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException e,
                                                             HttpServletRequest request) {
        String message = e.getMessage();

        if (isSmppUnavailable(message)) {
            log.warn("HTTP 503: {} {} -> {}", request.getMethod(), request.getRequestURI(), message);
            return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, message);
        }

        log.error("HTTP 500: {} {} -> {}", request.getMethod(), request.getRequestURI(), message, e);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    private boolean isSmppUnavailable(String message) {
        return SMPP_UNAVAILABLE_MESSAGE.equals(message)
                || (message != null && message.startsWith("Cannot connect to SMPP simulator"));
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", message);
        return ResponseEntity.status(status).body(response);
    }
}