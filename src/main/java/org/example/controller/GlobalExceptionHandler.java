package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e,
                                                                HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", e.getMessage());

        String message = e.getMessage();

        if ("Authorization header is required".equals(message)
                || "Invalid authorization format".equals(message)
                || "Token is required".equals(message)
                || "Invalid or expired token".equals(message)) {

            log.warn("HTTP 401: {} {} -> {}", request.getMethod(), request.getRequestURI(), message);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        if ("User not found".equals(message)
                || "OTP config not found".equals(message)) {

            log.warn("HTTP 404: {} {} -> {}", request.getMethod(), request.getRequestURI(), message);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        log.warn("HTTP 400: {} {} -> {}", request.getMethod(), request.getRequestURI(), message);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException e,
                                                              HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", e.getMessage());

        log.warn("HTTP 409: {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(SecurityException e,
                                                               HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", e.getMessage());

        log.warn("HTTP 403: {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException e,
                                                             HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", e.getMessage());

        log.error("HTTP 500: {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}