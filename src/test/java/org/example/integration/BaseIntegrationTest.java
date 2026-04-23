package org.example.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.LoginRequest;
import org.example.model.Role;
import org.example.model.User;
import org.example.repository.ConnectionFactory;
import org.example.repository.UserRepository;
import org.example.security.PasswordHasher;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class BaseIntegrationTest {

    protected static final String PASSWORD = "12345678";

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordHasher passwordHasher;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String loginAndGetToken(String login) throws Exception {
        return loginAndGetToken(login, PASSWORD);
    }

    protected String loginAndGetToken(String login, String password) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setLogin(login);
        request.setPassword(password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<LoginRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/auth/login", entity, String.class);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        return body.get("token").asText();
    }

    protected ResponseEntity<String> postAuthorized(String url, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity(url, entity, String.class);
    }

    protected ResponseEntity<String> postAuthorizedJson(String url, String token, String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        return restTemplate.postForEntity(url, entity, String.class);
    }

    protected ResponseEntity<String> exchangeAuthorized(String url, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        if (body instanceof String) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(url, method, entity, String.class);
    }

    protected void deleteUserByLogin(String login) {
        String sql = "DELETE FROM users WHERE LOWER(login) = LOWER(?)";

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, login);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete test user", e);
        }
    }

    protected void resetOtpConfig() {
        setOtpConfig(6, 300);
    }

    protected void setOtpConfig(int codeLength, int ttlSeconds) {
        String sql = """
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

    protected User createUser(String login) {
        User user = new User();
        user.setLogin(login);
        user.setPasswordHash(passwordHasher.hash(PASSWORD));
        user.setRole(Role.USER);
        user.setEmail(login + "@test.com");
        user.setPhone("+37400112233");
        user.setTelegramChatId("123456789");
        return user;
    }

    protected User createAdmin(String login) {
        User admin = new User();
        admin.setLogin(login);
        admin.setPasswordHash(passwordHasher.hash(PASSWORD));
        admin.setRole(Role.ADMIN);
        admin.setEmail(login + "@test.com");
        admin.setPhone("+37400110000");
        return admin;
    }

    protected Path createOtpFile(String prefix, String login) throws IOException {
        Path otpFile = Path.of("build", "test-otp", prefix + "-" + login + ".txt");
        Files.createDirectories(otpFile.getParent());
        Files.deleteIfExists(otpFile);
        return otpFile;
    }

    protected String readLastCodeFromFile(Path file) throws IOException {
        String lastLine = Files.readAllLines(file).stream()
                .filter(line -> !line.isBlank())
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException("OTP file is empty"));

        String marker = "code=";
        int start = lastLine.indexOf(marker);
        if (start < 0) {
            throw new IllegalStateException("OTP code not found in file");
        }

        int valueStart = start + marker.length();
        int valueEnd = lastLine.indexOf(" |", valueStart);
        if (valueEnd < 0) {
            valueEnd = lastLine.length();
        }

        return lastLine.substring(valueStart, valueEnd).trim();
    }

    protected String escapeBackslashes(String value) {
        return value.replace("\\", "\\\\");
    }

    protected long countOtpCodesByStatus(Long userId, String operationId, String status) {
        String sql = """
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

    protected long countOtpCodesByUserId(Long userId) {
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

    protected boolean userExistsById(Long userId) {
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
}