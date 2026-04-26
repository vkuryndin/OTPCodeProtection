package org.example.integration.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.example.repository.ConnectionFactory;

public final class TestDbHelper {

  private TestDbHelper() {}

  public static void deleteUserByLogin(String login) {
    String sql = "DELETE FROM users WHERE LOWER(login) = LOWER(?)";

    try (Connection connection = ConnectionFactory.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {

      statement.setString(1, login);
      statement.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to delete test user", e);
    }
  }

  public static void resetOtpConfig() {
    setOtpConfig(6, 300);
  }

  public static void setOtpConfig(int codeLength, int ttlSeconds) {
    String sql =
        """
                INSERT INTO otp_config (id, code_length, ttl_seconds, updated_at)
                VALUES (1, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (id)
                DO UPDATE SET
                    code_length = EXCLUDED.code_length,
                    ttl_seconds = EXCLUDED.ttl_seconds,
                    updated_at = CURRENT_TIMESTAMP
                """;

    try (Connection connection = ConnectionFactory.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {

      statement.setInt(1, codeLength);
      statement.setInt(2, ttlSeconds);
      statement.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update OTP config", e);
    }
  }

  public static long countOtpCodesByStatus(Long userId, String operationId, String status) {
    String sql =
        """
                SELECT COUNT(*)
                FROM otp_codes
                WHERE user_id = ?
                  AND operation_id = ?
                  AND status = ?::otp_status
                """;

    try (Connection connection = ConnectionFactory.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {

      statement.setLong(1, userId);
      statement.setString(2, operationId);
      statement.setString(3, status);

      try (ResultSet rs = statement.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to count OTP codes by status", e);
    }
  }

  public static long countOtpCodesByUserId(Long userId) {
    String sql = "SELECT COUNT(*) FROM otp_codes WHERE user_id = ?";

    try (Connection connection = ConnectionFactory.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {

      statement.setLong(1, userId);

      try (ResultSet rs = statement.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to count otp_codes by user_id", e);
    }
  }

  public static boolean userExistsById(Long userId) {
    String sql = "SELECT COUNT(*) FROM users WHERE id = ?";

    try (Connection connection = ConnectionFactory.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {

      statement.setLong(1, userId);

      try (ResultSet rs = statement.executeQuery()) {
        rs.next();
        return rs.getLong(1) > 0;
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to check user existence", e);
    }
  }

  public static boolean userExistsByLoginIgnoreCase(String login) {
    String sql = "SELECT COUNT(*) FROM users WHERE LOWER(login) = LOWER(?)";

    try (Connection connection = ConnectionFactory.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {

      statement.setString(1, login);

      try (ResultSet rs = statement.executeQuery()) {
        rs.next();
        return rs.getLong(1) > 0;
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to check user existence by login", e);
    }
  }

  public static boolean userExistsByLoginExact(String login) {
    String sql = "SELECT COUNT(*) FROM users WHERE login = ?";

    try (Connection connection = ConnectionFactory.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {

      statement.setString(1, login);

      try (ResultSet rs = statement.executeQuery()) {
        rs.next();
        return rs.getLong(1) > 0;
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to check user existence by exact login", e);
    }
  }

  public static void updateTelegramBindState(
      Long userId, String bindToken, LocalDateTime expiresAt) {
    String sql =
        """
                UPDATE users
                SET telegram_bind_token = ?, telegram_bind_expires_at = ?
                WHERE id = ?
                """;

    try (Connection connection = ConnectionFactory.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {

      statement.setString(1, bindToken);
      statement.setTimestamp(2, Timestamp.valueOf(expiresAt));
      statement.setLong(3, userId);
      statement.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update telegram bind state", e);
    }
  }
}
