package org.example.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.LoginRequest;
import org.example.model.Role;
import org.example.model.User;
import org.example.repository.ConnectionFactory;
import org.example.repository.UserRepository;
import org.example.security.PasswordHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OtpAuthApiITTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private ObjectMapper objectMapper;

    private String testLogin;
    private final String testPassword = "12345678";
    private Path otpFile;

    @BeforeEach
    void setUp() throws Exception {
        testLogin = "it_otp_auth_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        otpFile = Path.of("build", "test-otp", "otp-auth-" + testLogin + ".txt");

        Files.createDirectories(otpFile.getParent());
        Files.deleteIfExists(otpFile);

        deleteUserByLogin(testLogin);

        User user = new User();
        user.setLogin(testLogin);
        user.setPasswordHash(passwordHasher.hash(testPassword));
        user.setRole(Role.USER);
        user.setEmail(testLogin + "@test.com");
        user.setPhone("+37400112233");

        userRepository.createUser(user);

        upsertOtpConfig(6, 300);
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteUserByLogin(testLogin);
        Files.deleteIfExists(otpFile);
        upsertOtpConfig(6, 300);
    }

    @Test
    void generateOtp_shouldReturnUnauthorized_whenTokenIsMissing() throws Exception {
        String requestBody = """
                {
                  "operationId": "payment-auth-missing-token-001",
                  "deliveryChannel": "FILE",
                  "deliveryTarget": "%s"
                }
                """.formatted(escapeBackslashes(otpFile.toString()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/otp/generate", entity, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Authorization header is required", body.get("error").asText());
    }

    @Test
    void validateOtp_shouldReturnUnauthorized_whenTokenIsMissing() throws Exception {
        String requestBody = """
                {
                  "operationId": "payment-auth-validate-001",
                  "code": "123456"
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/otp/validate", entity, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Authorization header is required", body.get("error").asText());
    }

    @Test
    void generateOtp_shouldReturnUnauthorized_afterLogout() throws Exception {
        String token = loginAndGetToken();

        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.setBearerAuth(token);

        HttpEntity<Void> logoutEntity = new HttpEntity<>(logoutHeaders);

        ResponseEntity<String> logoutResponse =
                restTemplate.postForEntity("/auth/logout", logoutEntity, String.class);

        assertEquals(HttpStatus.OK, logoutResponse.getStatusCode());

        String requestBody = """
                {
                  "operationId": "payment-after-logout-001",
                  "deliveryChannel": "FILE",
                  "deliveryTarget": "%s"
                }
                """.formatted(escapeBackslashes(otpFile.toString()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/otp/generate", entity, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Invalid or expired token", body.get("error").asText());
    }

    private String loginAndGetToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setLogin(testLogin);
        request.setPassword(testPassword);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<LoginRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/auth/login", entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        return body.get("token").asText();
    }

    private void deleteUserByLogin(String login) {
        String sql = "DELETE FROM users WHERE login = ?";

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, login);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete test user", e);
        }
    }

    private void upsertOtpConfig(int codeLength, int ttlSeconds) {
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
            throw new RuntimeException("Failed to upsert OTP config", e);
        }
    }

    private String escapeBackslashes(String value) {
        return value.replace("\\", "\\\\");
    }
}