package org.example.service;

import org.example.model.Role;
import org.example.model.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {

    private final TokenService tokenService = new TokenService(
            "my_very_secure_otp_service_secret_key_2026_123456",
            60
    );

    @Test
    void generateToken_shouldCreateValidToken() {
        User user = new User();
        user.setId(10L);
        user.setLogin("user1");
        user.setRole(Role.USER);

        String token = tokenService.generateToken(user);

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void extractUserId_shouldReturnCorrectUserId() {
        User user = new User();
        user.setId(15L);
        user.setLogin("admin");
        user.setRole(Role.ADMIN);

        String token = tokenService.generateToken(user);

        Long userId = tokenService.extractUserId(token);

        assertEquals(15L, userId);
    }

    @Test
    void extractRole_shouldReturnCorrectRole() {
        User user = new User();
        user.setId(20L);
        user.setLogin("user2");
        user.setRole(Role.USER);

        String token = tokenService.generateToken(user);

        String role = tokenService.extractRole(token);

        assertEquals("USER", role);
    }

    @Test
    void revokedToken_shouldBecomeInvalid() {
        User user = new User();
        user.setId(25L);
        user.setLogin("user3");
        user.setRole(Role.USER);

        String token = tokenService.generateToken(user);
        tokenService.revokeToken(token);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tokenService.extractUserId(token)
        );

        assertEquals("Invalid or expired token", exception.getMessage());
    }
}