package org.example.dto;

import java.time.LocalDateTime;
import org.example.model.Role;

public record LoggedInUserResponse(
    Long userId, String login, Role role, LocalDateTime loggedInAt, LocalDateTime expiresAt) {}
