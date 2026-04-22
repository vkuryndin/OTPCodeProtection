package org.example.dto;

import org.example.model.Role;

public record LoginResponse(
        String message,
        String token,
        Long userId,
        String login,
        Role role
) {
}