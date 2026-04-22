package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.exception.NotFoundException;
import org.example.exception.RateLimitExceededException;
import org.example.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String SMPP_UNAVAILABLE_MESSAGE =
            "SMPP simulator is not available. Start the SMPP server and try again.";

    private static final String TELEGRAM_UNAVAILABLE_MESSAGE =
            "Telegram API is unavailable. Try again later.";

    private static final String EMAIL_UNAVAILABLE_MESSAGE =
            "Email service is unavailable. Try again later.";

    private static final String SMS_UNAVAILABLE_MESSAGE =
            "SMS service is unavailable. Try again later.";

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

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitExceeded(RateLimitExceededException e,
                                                                       HttpServletRequest request) {
        log.warn("HTTP 429: {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableMessage(HttpMessageNotReadableException e,
                                                                       HttpServletRequest request) {
        String message = resolveUnreadableMessage(e);

        log.warn("HTTP 400: {} {} -> {}", request.getMethod(), request.getRequestURI(), message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException e,
                                                             HttpServletRequest request) {
        String message = e.getMessage();

        if (isExternalServiceUnavailable(message)) {
            log.warn("HTTP 503: {} {} -> {}", request.getMethod(), request.getRequestURI(), message);
            return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, message);
        }

        log.error("HTTP 500: {} {} -> {}", request.getMethod(), request.getRequestURI(), message, e);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    private boolean isExternalServiceUnavailable(String message) {
        return isSmppUnavailable(message)
                || isTelegramUnavailable(message)
                || isEmailUnavailable(message)
                || isSmsUnavailable(message);
    }

    private boolean isSmppUnavailable(String message) {
        return SMPP_UNAVAILABLE_MESSAGE.equals(message)
                || (message != null && message.startsWith("Cannot connect to SMPP simulator"));
    }

    private boolean isTelegramUnavailable(String message) {
        return TELEGRAM_UNAVAILABLE_MESSAGE.equals(message);
    }

    private boolean isEmailUnavailable(String message) {
        return EMAIL_UNAVAILABLE_MESSAGE.equals(message);
    }

    private boolean isSmsUnavailable(String message) {
        return SMS_UNAVAILABLE_MESSAGE.equals(message);
    }

    private String resolveUnreadableMessage(HttpMessageNotReadableException e) {
        String message = e.getMessage();

        if (message == null || message.isBlank()) {
            return "Request body is invalid";
        }

        String lower = message.toLowerCase();

        if (lower.contains("required request body is missing")) {
            return "Request body is required";
        }

        return "Request body is invalid";
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", message);
        return ResponseEntity.status(status).body(response);
    }
}