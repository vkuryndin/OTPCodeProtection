package org.example.model;

import java.time.LocalDateTime;

public class User {
  private Long id;
  private String login;
  private String passwordHash;
  private Role role;
  private String email;
  private String phone;
  private String telegramChatId;
  private LocalDateTime createdAt;
  private String telegramBindToken;
  private LocalDateTime telegramBindExpiresAt;

  public User() {}

  public User(
      Long id,
      String login,
      String passwordHash,
      Role role,
      String email,
      String phone,
      String telegramChatId,
      LocalDateTime createdAt) {
    this.id = id;
    this.login = login;
    this.passwordHash = passwordHash;
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

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
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

  public String getTelegramBindToken() {
    return telegramBindToken;
  }

  public void setTelegramBindToken(String telegramBindToken) {
    this.telegramBindToken = telegramBindToken;
  }

  public LocalDateTime getTelegramBindExpiresAt() {
    return telegramBindExpiresAt;
  }

  public void setTelegramBindExpiresAt(LocalDateTime telegramBindExpiresAt) {
    this.telegramBindExpiresAt = telegramBindExpiresAt;
  }
}
