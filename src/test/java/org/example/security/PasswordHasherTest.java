package org.example.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    private final PasswordHasher passwordHasher = new PasswordHasher();

    @Test
    void hash_shouldCreateHashDifferentFromRawPassword() {
        String rawPassword = "12345678";

        String hash = passwordHasher.hash(rawPassword);

        assertNotNull(hash);
        assertNotEquals(rawPassword, hash);
    }

    @Test
    void matches_shouldReturnTrue_forCorrectPassword() {
        String rawPassword = "12345678";
        String hash = passwordHasher.hash(rawPassword);

        boolean result = passwordHasher.matches(rawPassword, hash);

        assertTrue(result);
    }

    @Test
    void matches_shouldReturnFalse_forWrongPassword() {
        String hash = passwordHasher.hash("12345678");

        boolean result = passwordHasher.matches("wrongpass", hash);

        assertFalse(result);
    }

    @Test
    void matches_shouldReturnFalse_whenHashIsNull() {
        boolean result = passwordHasher.matches("12345678", null);

        assertFalse(result);
    }
}