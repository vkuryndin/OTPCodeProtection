package org.example.dto;

import org.example.model.Role;

public class RegisterRequest {
  private String login;
  private String password;
  private Role role;
  private String email;
  private String phone;
  private String telegramChatId;

  public RegisterRequest() {}

  public String getLogin() {
    return login;
  }

  public void setLogin(String login) {
    this.login = login;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
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
}
