package org.example.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.example.model.User;
import org.example.repository.ConnectionFactory;
import org.example.repository.UserSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserSessionRepositoryActiveSessionITTest extends BaseIntegrationTest {

  @Autowired private UserSessionRepository userSessionRepository;

  private String testLogin;
  private Long userId;

  @BeforeEach
  void setUp() {
    testLogin =
        "it_session_active_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    deleteUserByLogin(testLogin);

    User user = createUser(testLogin);
    userId = userRepository.createUser(user);

    deleteSessionsByUserId(userId);
  }

  @AfterEach
  void tearDown() {
    if (userId != null) {
      deleteSessionsByUserId(userId);
    }
    deleteUserByLogin(testLogin);
  }

  @Test
  void isSessionActive_shouldReturnTrue_forActiveSession() {
    String tokenId = "active-" + UUID.randomUUID();
    LocalDateTime now = LocalDateTime.now();

    userSessionRepository.createSession(userId, tokenId, now.minusMinutes(1), now.plusMinutes(30));

    assertTrue(userSessionRepository.isSessionActive(tokenId));
  }

  @Test
  void isSessionActive_shouldReturnFalse_forRevokedSession() {
    String tokenId = "revoked-" + UUID.randomUUID();
    LocalDateTime now = LocalDateTime.now();

    userSessionRepository.createSession(userId, tokenId, now.minusMinutes(1), now.plusMinutes(30));
    userSessionRepository.revokeSession(tokenId);

    assertFalse(userSessionRepository.isSessionActive(tokenId));
  }

  @Test
  void isSessionActive_shouldReturnFalse_forExpiredSession() {
    String tokenId = "expired-" + UUID.randomUUID();
    LocalDateTime now = LocalDateTime.now();

    userSessionRepository.createSession(userId, tokenId, now.minusMinutes(30), now.minusMinutes(1));

    assertFalse(userSessionRepository.isSessionActive(tokenId));
  }

  private void deleteSessionsByUserId(Long userId) {
    String sql = "DELETE FROM user_sessions WHERE user_id = ?";

    try (Connection connection = ConnectionFactory.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {

      statement.setLong(1, userId);
      statement.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to delete user sessions", e);
    }
  }
}
