package org.example.util;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class AuthValidationUtil {

    private static final Pattern LOGIN_PATTERN =
            Pattern.compile("^[A-Za-z0-9._-]+$");

    private AuthValidationUtil() {
    }

    public static String validateLogin(String login) {
        if (login == null) {
            return "Login is required";
        }

        String normalizedLogin = login.trim();

        if (normalizedLogin.isEmpty()) {
            return "Login is required";
        }

        if (normalizedLogin.length() < 3) {
            return "Login must be at least 3 characters long";
        }

        if (normalizedLogin.length() > 50) {
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

        if (password.length() < 8) {
            return "Password must be at least 8 characters long";
        }

        byte[] utf8 = password.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > 72) {
            return "Password is too long for bcrypt (max 72 UTF-8 bytes)";
        }

        return null;
    }
}