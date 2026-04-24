package org.example.dto;

import org.example.model.Role;

import java.time.LocalDateTime;

public class LoggedInUserResponse {
    private Long userId;
    private String login;
    private Role role;
    private LocalDateTime loggedInAt;
    private LocalDateTime expiresAt;


    public LoggedInUserResponse(Long userId, String login, Role role,
                                LocalDateTime loggedInAt, LocalDateTime expiresAt) {
        this.userId = userId;
        this.login = login;
        this.role = role;
        this.loggedInAt = loggedInAt;
        this.expiresAt = expiresAt;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}