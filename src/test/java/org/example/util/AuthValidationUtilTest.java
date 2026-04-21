package org.example.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthValidationUtilTest {

    @Test
    void validateLogin_shouldReturnNull_whenLoginIsValid() {
        String result = AuthValidationUtil.validateLogin("user_123");

        assertNull(result);
    }

    @Test
    void validateLogin_shouldReturnError_whenLoginIsTooShort() {
        String result = AuthValidationUtil.validateLogin("ab");

        assertEquals("Login must be at least 3 characters long", result);
    }

    @Test
    void validateLogin_shouldReturnError_whenLoginHasInvalidCharacters() {
        String result = AuthValidationUtil.validateLogin("user!*");

        assertEquals("Login may contain only letters, digits, dot, underscore and hyphen", result);
    }

    @Test
    void validatePassword_shouldReturnNull_whenPasswordIsValid() {
        String result = AuthValidationUtil.validatePassword("12345678");

        assertNull(result);
    }

    @Test
    void validatePassword_shouldReturnError_whenPasswordIsTooShort() {
        String result = AuthValidationUtil.validatePassword("1234567");

        assertEquals("Password must be at least 8 characters long", result);
    }

    @Test
    void validatePassword_shouldReturnError_whenPasswordIsBlank() {
        String result = AuthValidationUtil.validatePassword("   ");

        assertEquals("Password is required", result);
    }
}