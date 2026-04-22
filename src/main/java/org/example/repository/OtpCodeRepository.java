package org.example.repository;

import org.example.model.DeliveryChannel;
import org.example.model.OtpCode;
import org.example.model.OtpStatus;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Repository
public class OtpCodeRepository {

    private static final String CREATE_OTP_CODE_SQL = """
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

    private static final String CONSUME_ACTIVE_CODE_SQL = """
            WITH candidate AS (
                SELECT id
                FROM otp_codes
                WHERE user_id = ?
                  AND operation_id = ?
                  AND code = ?
                  AND status = 'ACTIVE'
                  AND expires_at >= CURRENT_TIMESTAMP
                ORDER BY id DESC
                LIMIT 1
            )
            UPDATE otp_codes o
            SET status = 'USED',
                used_at = CURRENT_TIMESTAMP
            FROM candidate
            WHERE o.id = candidate.id
            RETURNING o.id,
                      o.user_id,
                      o.operation_id,
                      o.code,
                      o.status,
                      o.delivery_channel,
                      o.delivery_target,
                      o.created_at,
                      o.expires_at,
                      o.sent_at,
                      o.used_at
            """;

    private static final String EXPIRE_ACTIVE_CODES_SQL = """
            UPDATE otp_codes
            SET status = 'EXPIRED'
            WHERE status = 'ACTIVE'
              AND expires_at < CURRENT_TIMESTAMP
            """;

    private static final String DELETE_BY_ID_SQL = """
            DELETE FROM otp_codes
            WHERE id = ?
            """;

    public Long createOtpCode(OtpCode otpCode) {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(CREATE_OTP_CODE_SQL)) {

            statement.setLong(1, otpCode.getUserId());
            statement.setString(2, otpCode.getOperationId());
            statement.setString(3, otpCode.getCode());
            statement.setString(4, otpCode.getStatus().name());
            statement.setString(5, otpCode.getDeliveryChannel().name());
            statement.setString(6, otpCode.getDeliveryTarget());
            statement.setTimestamp(7, Timestamp.valueOf(otpCode.getExpiresAt()));

            if (otpCode.getSentAt() == null) {
                statement.setTimestamp(8, null);
            } else {
                statement.setTimestamp(8, Timestamp.valueOf(otpCode.getSentAt()));
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

    public OtpCode consumeActiveCode(Long userId, String operationId, String code) {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(CONSUME_ACTIVE_CODE_SQL)) {

            statement.setLong(1, userId);
            statement.setString(2, operationId);
            statement.setString(3, code);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to consume active OTP code", e);
        }
    }

    public int expireActiveCodes() {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(EXPIRE_ACTIVE_CODES_SQL)) {

            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to expire OTP codes", e);
        }
    }

    public boolean deleteById(Long otpCodeId) {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_BY_ID_SQL)) {

            statement.setLong(1, otpCodeId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete OTP code", e);
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