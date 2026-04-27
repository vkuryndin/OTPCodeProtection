package org.example.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.example.model.DeliveryChannel;
import org.example.model.OtpCode;
import org.example.model.OtpStatus;
import org.springframework.stereotype.Repository;

@Repository
public class OtpCodeRepository {

  private static final String CREATE_OTP_CODE_SQL =
      """
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

  private static final String CONSUME_ACTIVE_CODE_SQL =
      """
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
              AND o.status = 'ACTIVE'
              AND o.expires_at >= CURRENT_TIMESTAMP
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

  private static final String EXPIRE_ACTIVE_CODES_SQL =
      """
            UPDATE otp_codes
            SET status = 'EXPIRED'
            WHERE status = 'ACTIVE'
              AND expires_at < CURRENT_TIMESTAMP
            """;

  private static final String EXPIRE_ACTIVE_CODES_FOR_USER_OPERATION_SQL =
      """
            UPDATE otp_codes
            SET status = 'EXPIRED'
            WHERE user_id = ?
              AND operation_id = ?
              AND status = 'ACTIVE'
            """;

  private static final String DELETE_BY_ID_SQL =
      """
            DELETE FROM otp_codes
            WHERE id = ?
            """;

  private static final String GENERATE_LOCK_SQL =
      """
            SELECT pg_advisory_xact_lock(?, ?)
            """;

  // Replaces the currently active OTP for the same userId + operationId atomically:
  // first expires old ACTIVE codes, then inserts the new one in the same transaction.
  public Long createOtpCodeReplacingActive(OtpCode otpCode) {
    try (Connection connection = ConnectionFactory.getConnection()) {
      boolean originalAutoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);

      try {
        acquireGenerationLock(connection, otpCode.getUserId(), otpCode.getOperationId());
        expireActiveCodesForUserOperation(
            connection, otpCode.getUserId(), otpCode.getOperationId());

        Long otpId = insertOtpCode(connection, otpCode);
        connection.commit();
        return otpId;
      } catch (SQLException | RuntimeException e) {
        rollbackQuietly(connection);

        if (e instanceof RuntimeException runtimeException) {
          throw runtimeException;
        }

        throw new RuntimeException("Failed to create OTP code atomically", e);
      } finally {
        restoreAutoCommit(connection, originalAutoCommit);
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to create OTP code atomically", e);
    }
  }

  // Consumes OTP atomically in one SQL statement.
  // This prevents two concurrent validations from successfully using the same code twice.
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

  private Long insertOtpCode(Connection connection, OtpCode otpCode) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(CREATE_OTP_CODE_SQL)) {

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
    }
  }

  private int expireActiveCodesForUserOperation(
      Connection connection, Long userId, String operationId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(EXPIRE_ACTIVE_CODES_FOR_USER_OPERATION_SQL)) {
      statement.setLong(1, userId);
      statement.setString(2, operationId);
      return statement.executeUpdate();
    }
  }

  // PostgreSQL advisory lock serializes OTP generation for the same userId + operationId pair
  // inside one transaction and prevents two concurrent requests from creating two ACTIVE codes.

  private void acquireGenerationLock(Connection connection, Long userId, String operationId)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(GENERATE_LOCK_SQL)) {
      statement.setInt(1, Long.hashCode(userId));
      statement.setInt(2, operationId.hashCode());

      try (ResultSet rs = statement.executeQuery()) {
        rs.next();
      }
    }
  }

  private void rollbackQuietly(Connection connection) {
    try {
      connection.rollback();
    } catch (SQLException ignored) {
    }
  }

  private void restoreAutoCommit(Connection connection, boolean originalAutoCommit) {
    try {
      connection.setAutoCommit(originalAutoCommit);
    } catch (SQLException ignored) {
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

    otpCode.setExpiresAt(toLocalDateTime(rs.getTimestamp("expires_at")));
    otpCode.setSentAt(toLocalDateTime(rs.getTimestamp("sent_at")));

    return otpCode;
  }

  private LocalDateTime toLocalDateTime(Timestamp timestamp) {
    return timestamp != null ? timestamp.toLocalDateTime() : null;
  }
}
