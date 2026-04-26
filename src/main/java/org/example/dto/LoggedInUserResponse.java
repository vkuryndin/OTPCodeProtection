package org.example.dto;

import org.example.model.Role;

import java.time.LocalDateTime;

public record LoggedInUserResponse(
        Long userId,
        String login,
        Role role,
        LocalDateTime loggedInAt,
        LocalDateTime expiresAt
) {
}