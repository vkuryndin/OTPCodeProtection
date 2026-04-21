package org.example.repository;

import org.example.model.DeliveryChannel;
import org.example.model.OtpCode;
import org.example.model.OtpStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class OtpCodeRepository {

    public Long createOtpCode(OtpCode otpCode) {
        String sql = """
                INSERT INTO otp_codes (
                    user_id,
                    operation_id,
                    code,
                    status,
                    delivery_channel,
                    delivery_target,
                    expires_at,
                    sent_at
                )
                VALUES (?, ?, ?, ?::otp_status, ?::delivery_channel, ?, ?, ?)
                RETURNING id
                """;

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, otpCode.getUserId());
            statement.setString(2, otpCode.getOperationId());
            statement.setString(3, otpCode.getCode());
            statement.setString(4, otpCode.getStatus().name());
            statement.setString(5, otpCode.getDeliveryChannel().name());
            statement.setString(6, otpCode.getDeliveryTarget());
            statement.setTimestamp(7, Timestamp.valueOf(otpCode.getExpiresAt()));

            if (otpCode.getSentAt() != null) {
                statement.setTimestamp(8, Timestamp.valueOf(otpCode.getSentAt()));
            } else {
                statement.setTimestamp(8, null);
            }

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }

            throw new RuntimeException("Failed to create OTP code: no id returned");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create OTP code", e);
        }
    }

    public OtpCode findActiveCode(Long userId, String operationId, String code) {
        String sql = """
                SELECT id, user_id, operation_id, code, status, delivery_channel, delivery_target,
                       created_at, expires_at, sent_at, used_at
                FROM otp_codes
                WHERE user_id = ?
                  AND operation_id = ?
                  AND code = ?
                  AND status = 'ACTIVE'
                ORDER BY id DESC
                LIMIT 1
                """;

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, userId);
            statement.setString(2, operationId);
            statement.setString(3, code);

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find active OTP code", e);
        }
    }

    public boolean markAsUsed(Long otpCodeId) {
        String sql = """
                UPDATE otp_codes
                SET status = 'USED',
                    used_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'ACTIVE'
                """;

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, otpCodeId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to mark OTP code as used", e);
        }
    }

    public int expireActiveCodes() {
        String sql = """
                UPDATE otp_codes
                SET status = 'EXPIRED'
                WHERE status = 'ACTIVE'
                  AND expires_at < CURRENT_TIMESTAMP
                """;

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to expire OTP codes", e);
        }
    }

    private OtpCode mapRow(ResultSet rs) throws SQLException {
        OtpCode otpCode = new OtpCode();
        otpCode.setId(rs.getLong("id"));
        otpCode.setUserId(rs.getLong("user_id"));
        otpCode.setOperationId(rs.getString("operation_id"));
        otpCode.setCode(rs.getString("code"));
        otpCode.setStatus(OtpStatus.valueOf(rs.getString("status")));
        otpCode.setDeliveryChannel(DeliveryChannel.valueOf(rs.getString("delivery_channel")));
        otpCode.setDeliveryTarget(rs.getString("delivery_target"));
        otpCode.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        otpCode.setExpiresAt(toLocalDateTime(rs.getTimestamp("expires_at")));
        otpCode.setSentAt(toLocalDateTime(rs.getTimestamp("sent_at")));
        otpCode.setUsedAt(toLocalDateTime(rs.getTimestamp("used_at")));
        return otpCode;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }
}