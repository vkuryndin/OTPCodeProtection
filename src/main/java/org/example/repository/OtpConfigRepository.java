package org.example.repository;

import org.example.model.OtpConfig;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

@Repository
public class OtpConfigRepository {

    public OtpConfig getConfig() {
        String sql = """
                SELECT id, code_length, ttl_seconds, updated_at
                FROM otp_config
                WHERE id = 1
                """;

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {

            if (rs.next()) {
                return mapRow(rs);
            }

            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get OTP config", e);
        }
    }

    public boolean updateConfig(int codeLength, int ttlSeconds) {
        String sql = """
                UPDATE otp_config
                SET code_length = ?, ttl_seconds = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = 1
                """;

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

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

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            config.setUpdatedAt(updatedAt.toLocalDateTime());
        }

        return config;
    }
}