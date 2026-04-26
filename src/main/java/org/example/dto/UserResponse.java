package org.example.dto;

import java.time.LocalDateTime;
import org.example.model.Role;

public record UserResponse(
    Long id,
    String login,
    Role role,
    String email,
    String phone,
    String telegramChatId,
    LocalDateTime createdAt) {}
