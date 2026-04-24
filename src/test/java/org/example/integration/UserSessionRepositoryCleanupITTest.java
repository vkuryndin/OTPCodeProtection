package org.example.integration;

import org.example.model.User;
import org.example.repository.ConnectionFactory;
import org.example.repository.UserSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserSessionRepositoryCleanupITTest extends BaseIntegrationTest {

    @Autowired
    private UserSessionRepository userSessionRepository;

    private String testLogin;
    private Long userId;

    @BeforeEach
    void setUp() {
        testLogin = "it_session_cleanup_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

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
    void cleanupExpiredSessions_shouldDeleteExpiredAndRevokedSessions_butKeepActiveOnes() {
        String expiredTokenId = "expired-" + UUID.randomUUID();
        String revokedTokenId = "revoked-" + UUID.randomUUID();
        String activeTokenId = "active-" + UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now();

        userSessionRepository.createSession(
                userId,
                expiredTokenId,
                now.minusMinutes(20),
                now.minusMinutes(5)
        );

        userSessionRepository.createSession(
                userId,
                revokedTokenId,
                now.minusMinutes(10),
                now.plusMinutes(30)
        );
        userSessionRepository.revokeSession(revokedTokenId);

        userSessionRepository.createSession(
                userId,
                activeTokenId,
                now.minusMinutes(1),
                now.plusMinutes(30)
        );

        assertEquals(3, countSessionsByUserId(userId));

        int removed = userSessionRepository.cleanupExpiredSessions();

        assertEquals(2, removed);
        assertEquals(1, countSessionsByUserId(userId));
        assertTrue(sessionExists(activeTokenId));
    }

    private int countSessionsByUserId(Long userId) {
        String sql = "SELECT COUNT(*) FROM user_sessions WHERE user_id = ?";

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, userId);

            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count user sessions", e);
        }
    }

    private boolean sessionExists(String tokenId) {
        String sql = "SELECT COUNT(*) FROM user_sessions WHERE token_id = ?";

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, tokenId);

            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check user session existence", e);
        }
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