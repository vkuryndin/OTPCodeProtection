package org.example.dto;

public record UpdateOtpConfigResponse(
        String message,
        Integer codeLength,
        Integer ttlSeconds
) {
}