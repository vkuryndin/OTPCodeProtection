package org.example.dto;

import org.example.model.Role;

import java.time.LocalDateTime;

public class UserResponse {
    private Long id;
    private String login;
    private Role role;
    private String email;
    private String phone;
    private String telegramChatId;
    private LocalDateTime createdAt;

    public UserResponse() {
    }

    public UserResponse(Long id, String login, Role role, String email,
                        String phone, String telegramChatId, LocalDateTime createdAt) {
        this.id = id;
        this.login = login;
        this.role = role;
        this.email = email;
        this.phone = phone;
        this.telegramChatId = telegramChatId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getTelegramChatId() {
        return telegramChatId;
    }

    public void setTelegramChatId(String telegramChatId) {
        this.telegramChatId = telegramChatId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}