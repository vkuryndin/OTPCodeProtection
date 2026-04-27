package org.example.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.example.model.OtpConfig;
import org.springframework.stereotype.Repository;

@Repository
public class OtpConfigRepository {

  private static final String GET_CONFIG_SQL =
      """
            SELECT id, code_length, ttl_seconds, updated_at
            FROM otp_config
            WHERE id = 1
            """;

  private static final String UPDATE_CONFIG_SQL =
      """
            UPDATE otp_config
            SET code_length = ?, ttl_seconds = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = 1
            """;

  public OtpConfig getConfig() {
    try (Connection connection = ConnectionFactory.getConnection();
        PreparedStatement statement = connection.prepareStatement(GET_CONFIG_SQL);
        ResultSet rs = statement.executeQuery()) {

      if (!rs.next()) {
        return null;
      }

      return mapRow(rs);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get OTP config", e);
    }
  }

  public boolean updateConfig(int codeLength, int ttlSeconds) {
    try (Connection connection = ConnectionFactory.getConnection();
        PreparedStatement statement = connection.prepareStatement(UPDATE_CONFIG_SQL)) {

      statement.setInt(1, codeLength);
      statement.setInt(2, ttlSeconds);

      return statement.executeUpdate() > 0;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update OTP config", e);
    }
  }

  private OtpConfig mapRow(ResultSet rs) throws SQLException {
    OtpConfig config = new OtpConfig();
    config.setId(rs.getInt("id"));
    config.setCodeLength(rs.getInt("code_length"));
    config.setTtlSeconds(rs.getInt("ttl_seconds"));

    return config;
  }
}
