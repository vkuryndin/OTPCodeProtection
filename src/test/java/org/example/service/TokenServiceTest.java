package org.example.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.example.dto.LoggedInUserResponse;
import org.example.exception.UnauthorizedException;
import org.example.model.Role;
import org.example.model.User;
import org.example.repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenServiceTest {

  private UserSessionRepository userSessionRepository;
  private TokenService tokenService;

  @BeforeEach
  void setUp() {
    userSessionRepository = mock(UserSessionRepository.class);
    tokenService =
        new TokenService(
            "my_very_secure_otp_service_secret_key_2026_123456", 60, userSessionRepository);
  }

  @Test
  void generateToken_shouldCreateValidToken() {
    User user = new User();
    user.setId(10L);
    user.setLogin("user1");
    user.setRole(Role.USER);
    user.setPasswordHash("hash");

    String token = tokenService.generateToken(user);

    assertNotNull(token);
    assertFalse(token.isBlank());
    verify(userSessionRepository).createSession(eq(10L), anyString(), any(), any());
  }

  @Test
  void extractUserId_shouldReturnCorrectUserId() {
    User user = new User();
    user.setId(15L);
    user.setLogin("admin");
    user.setRole(Role.ADMIN);
    user.setPasswordHash("hash");

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
    user.setPasswordHash("hash");

    String token = tokenService.generateToken(user);

    String role = tokenService.extractRole(token);

    assertEquals("USER", role);
  }

  @Test
  void revokeToken_shouldMarkSessionAsRevoked() {
    User user = new User();
    user.setId(1L);
    user.setLogin("testuser");
    user.setRole(Role.USER);
    user.setPasswordHash("hash");

    String token = tokenService.generateToken(user);
    tokenService.revokeToken(token);

    verify(userSessionRepository).revokeSession(anyString());
  }

  @Test
  void getLoggedInUsers_shouldCleanupExpiredSessions_beforeReturningRepositoryResult() {
    LoggedInUserResponse response = new LoggedInUserResponse(1L, "user1", Role.USER, null, null);

    when(userSessionRepository.cleanupExpiredSessions()).thenReturn(2);
    when(userSessionRepository.findLoggedInUsers()).thenReturn(List.of(response));

    List<LoggedInUserResponse> users = tokenService.getLoggedInUsers();

    assertEquals(1, users.size());
    assertEquals("user1", users.get(0).login());

    verify(userSessionRepository).cleanupExpiredSessions();
    verify(userSessionRepository).findLoggedInUsers();
  }

  @Test
  void extractUserId_shouldThrowUnauthorized_forInvalidToken() {
    UnauthorizedException ex =
        assertThrows(UnauthorizedException.class, () -> tokenService.extractUserId("bad-token"));

    assertEquals("Invalid or expired token", ex.getMessage());
  }
}
