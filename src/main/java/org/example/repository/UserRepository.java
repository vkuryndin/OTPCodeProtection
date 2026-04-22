package org.example.repository;

import org.example.model.Role;
import org.example.model.User;
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
public class UserRepository {

    private static final String CREATE_USER_SQL = """
            INSERT INTO users (login, password_hash, role, email, phone, telegram_chat_id)
            VALUES (?, ?, ?::user_role, ?, ?, ?)
            RETURNING id
            """;

    private static final String FIND_BY_LOGIN_SQL = """
            SELECT id, login, password_hash, role, email, phone, telegram_chat_id,
                   created_at, telegram_bind_token, telegram_bind_expires_at
            FROM users
            WHERE login = ?
            """;

    private static final String ADMIN_EXISTS_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM users
                WHERE role = 'ADMIN'
            )
            """;

    private static final String FIND_ALL_NON_ADMINS_SQL = """
            SELECT id, login, password_hash, role, email, phone, telegram_chat_id,
                   created_at, telegram_bind_token, telegram_bind_expires_at
            FROM users
            WHERE role <> 'ADMIN'
            ORDER BY id
            """;

    private static final String DELETE_BY_ID_SQL = """
            DELETE FROM users
            WHERE id = ?
            """;

    private static final String FIND_BY_ID_SQL = """
            SELECT id, login, password_hash, role, email, phone, telegram_chat_id,
                   created_at, telegram_bind_token, telegram_bind_expires_at
            FROM users
            WHERE id = ?
            """;

    private static final String UPDATE_TELEGRAM_BIND_TOKEN_SQL = """
            UPDATE users
            SET telegram_bind_token = ?, telegram_bind_expires_at = ?
            WHERE id = ?
            """;
    
    private static final String BIND_TELEGRAM_CHAT_ID_SQL = """
            UPDATE users
            SET telegram_chat_id = ?, telegram_bind_token = NULL, telegram_bind_expires_at = NULL
            WHERE id = ?
            """;

    public Long createUser(User user) {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(CREATE_USER_SQL)) {

            statement.setString(1, user.getLogin());
            statement.setString(2, user.getPasswordHash());
            statement.setString(3, user.getRole().name());
            statement.setString(4, user.getEmail());
            statement.setString(5, user.getPhone());
            statement.setString(6, user.getTelegramChatId());

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }

            throw new RuntimeException("Failed to create user: no id returned");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create user", e);
        }
    }

    public User findByLogin(String login) {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_LOGIN_SQL)) {

            statement.setString(1, login);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find user by login", e);
        }
    }

    public boolean adminExists() {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(ADMIN_EXISTS_SQL);
             ResultSet rs = statement.executeQuery()) {

            if (rs.next()) {
                return rs.getBoolean(1);
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check admin existence", e);
        }
    }

    public List<User> findAllNonAdmins() {
        List<User> users = new ArrayList<>();

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ALL_NON_ADMINS_SQL);
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                users.add(mapRow(rs));
            }

            return users;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get users", e);
        }
    }

    public boolean deleteById(Long userId) {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_BY_ID_SQL)) {

            statement.setLong(1, userId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete user", e);
        }
    }

    public User findById(Long id) {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_ID_SQL)) {

            statement.setLong(1, id);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find user by id", e);
        }
    }

    public void updateTelegramBindToken(Long userId, String bindToken, LocalDateTime expiresAt) {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_TELEGRAM_BIND_TOKEN_SQL)) {

            statement.setString(1, bindToken);
            statement.setTimestamp(2, Timestamp.valueOf(expiresAt));
            statement.setLong(3, userId);

            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update telegram bind token", e);
        }
    }

    public void bindTelegramChatId(Long userId, String chatId) {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(BIND_TELEGRAM_CHAT_ID_SQL)) {

            statement.setString(1, chatId);
            statement.setLong(2, userId);

            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to bind telegram chat id", e);
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setLogin(rs.getString("login"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setRole(Role.valueOf(rs.getString("role")));
        user.setEmail(rs.getString("email"));
        user.setPhone(rs.getString("phone"));
        user.setTelegramChatId(rs.getString("telegram_chat_id"));
        user.setTelegramBindToken(rs.getString("telegram_bind_token"));

        Timestamp telegramBindExpiresAt = rs.getTimestamp("telegram_bind_expires_at");
        if (telegramBindExpiresAt != null) {
            user.setTelegramBindExpiresAt(telegramBindExpiresAt.toLocalDateTime());
        }

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            user.setCreatedAt(createdAt.toLocalDateTime());
        }

        return user;
    }
}