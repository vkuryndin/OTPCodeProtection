package org.example.dto;

import org.example.model.Role;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String login,
        Role role,
        String email,
        String phone,
        String telegramChatId,
        LocalDateTime createdAt
) {
}