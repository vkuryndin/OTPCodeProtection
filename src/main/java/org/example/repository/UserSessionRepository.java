package org.example.repository;

import org.example.dto.LoggedInUserResponse;
import org.example.model.Role;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class UserSessionRepository {

    private static final String CREATE_SESSION_SQL = """
            INSERT INTO user_sessions (user_id, token_id, logged_in_at, expires_at)
            VALUES (?, ?, ?, ?)
            """;

    private static final String REVOKE_SESSION_SQL = """
            UPDATE user_sessions
            SET revoked_at = CURRENT_TIMESTAMP
            WHERE token_id = ?
              AND revoked_at IS NULL
            """;

    private static final String IS_SESSION_ACTIVE_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM user_sessions
                WHERE token_id = ?
                  AND revoked_at IS NULL
                  AND expires_at > CURRENT_TIMESTAMP
            )
            """;

    private static final String FIND_LOGGED_IN_USERS_SQL = """
            SELECT DISTINCT ON (u.id)
                   u.id AS user_id,
                   u.login,
                   u.role,
                   s.logged_in_at,
                   s.expires_at
            FROM user_sessions s
            JOIN users u ON u.id = s.user_id
            WHERE s.revoked_at IS NULL
              AND s.expires_at > CURRENT_TIMESTAMP
            ORDER BY u.id, s.logged_in_at DESC
            """;

    private static final String CLEANUP_EXPIRED_SESSIONS_SQL = """
            DELETE FROM user_sessions
            WHERE expires_at <= CURRENT_TIMESTAMP
               OR revoked_at IS NOT NULL
            """;

    public void createSession(Long userId, String tokenId, LocalDateTime loggedInAt, LocalDateTime expiresAt) {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(CREATE_SESSION_SQL)) {

            statement.setLong(1, userId);
            statement.setString(2, tokenId);
            statement.setTimestamp(3, Timestamp.valueOf(loggedInAt));
            statement.setTimestamp(4, Timestamp.valueOf(expiresAt));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create user session", e);
        }
    }

    public void revokeSession(String tokenId) {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(REVOKE_SESSION_SQL)) {

            statement.setString(1, tokenId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to revoke user session", e);
        }
    }

    public boolean isSessionActive(String tokenId) {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(IS_SESSION_ACTIVE_SQL)) {

            statement.setString(1, tokenId);

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check session state", e);
        }
    }

    public List<LoggedInUserResponse> findLoggedInUsers() {
        List<LoggedInUserResponse> result = new ArrayList<>();

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_LOGGED_IN_USERS_SQL);
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                result.add(new LoggedInUserResponse(
                        rs.getLong("user_id"),
                        rs.getString("login"),
                        Role.valueOf(rs.getString("role")),
                        rs.getTimestamp("logged_in_at").toLocalDateTime(),
                        rs.getTimestamp("expires_at").toLocalDateTime()
                ));
            }

            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get logged-in users", e);
        }
    }

    public int cleanupExpiredSessions() {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(CLEANUP_EXPIRED_SESSIONS_SQL)) {
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to cleanup user sessions", e);
        }
    }
}