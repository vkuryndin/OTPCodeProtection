package org.example.dto;

public record DeleteUserResponse(
        String message,
        Long userId
) {
}