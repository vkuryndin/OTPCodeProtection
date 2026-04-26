package org.example.util;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class AuthValidationUtil {

  private static final Pattern LOGIN_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");

  private static final int MIN_LOGIN_LENGTH = 3;
  private static final int MAX_LOGIN_LENGTH = 50;
  private static final int MIN_PASSWORD_LENGTH = 8;
  private static final int MAX_BCRYPT_UTF8_BYTES = 72;

  private AuthValidationUtil() {}

  public static String validateLogin(String login) {
    if (login == null) {
      return "Login is required";
    }

    String normalizedLogin = login.trim();

    if (normalizedLogin.isEmpty()) {
      return "Login is required";
    }

    if (normalizedLogin.length() < MIN_LOGIN_LENGTH) {
      return "Login must be at least 3 characters long";
    }

    if (normalizedLogin.length() > MAX_LOGIN_LENGTH) {
      return "Login must not be longer than 50 characters";
    }

    if (!LOGIN_PATTERN.matcher(normalizedLogin).matches()) {
      return "Login may contain only letters, digits, dot, underscore and hyphen";
    }

    return null;
  }

  public static String validatePassword(String password) {
    if (password == null) {
      return "Password is required";
    }

    if (password.isBlank()) {
      return "Password is required";
    }

    if (password.length() < MIN_PASSWORD_LENGTH) {
      return "Password must be at least 8 characters long";
    }

    byte[] utf8 = password.getBytes(StandardCharsets.UTF_8);
    if (utf8.length > MAX_BCRYPT_UTF8_BYTES) {
      return "Password is too long for bcrypt (max 72 UTF-8 bytes)";
    }

    return null;
  }
}
