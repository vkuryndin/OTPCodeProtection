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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OtpRequestValidationApiITTest {

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

    @BeforeEach
    void setUp() {
        testLogin = "it_otp_req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        deleteUserByLogin(testLogin);

        User user = new User();
        user.setLogin(testLogin);
        user.setPasswordHash(passwordHasher.hash(testPassword));
        user.setRole(Role.USER);
        user.setEmail(testLogin + "@test.com");
        user.setPhone("+37400112233");
        user.setTelegramChatId("123456789");

        userRepository.createUser(user);

        upsertOtpConfig(6, 300);
    }

    @AfterEach
    void tearDown() {
        deleteUserByLogin(testLogin);
        upsertOtpConfig(6, 300);
    }

    @Test
    void generateOtp_shouldReturnBadRequest_whenOperationIdIsMissing() throws Exception {
        String token = loginAndGetToken();

        String requestBody = """
                {
                  "deliveryChannel": "FILE",
                  "deliveryTarget": "otp-codes.txt"
                }
                """;

        ResponseEntity<String> response = postAuthorizedJson("/otp/generate", token, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Operation ID is required", body.get("error").asText());
    }

    @Test
    void generateOtp_shouldReturnBadRequest_whenDeliveryChannelIsMissing() throws Exception {
        String token = loginAndGetToken();

        String requestBody = """
                {
                  "operationId": "payment-missing-channel-001",
                  "deliveryTarget": "otp-codes.txt"
                }
                """;

        ResponseEntity<String> response = postAuthorizedJson("/otp/generate", token, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Delivery channel is required", body.get("error").asText());
    }

    @Test
    void validateOtp_shouldReturnBadRequest_whenOperationIdIsMissing() throws Exception {
        String token = loginAndGetToken();

        String requestBody = """
                {
                  "code": "123456"
                }
                """;

        ResponseEntity<String> response = postAuthorizedJson("/otp/validate", token, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Operation ID is required", body.get("error").asText());
    }

    @Test
    void validateOtp_shouldReturnBadRequest_whenCodeIsMissing() throws Exception {
        String token = loginAndGetToken();

        String requestBody = """
                {
                  "operationId": "payment-missing-code-001"
                }
                """;

        ResponseEntity<String> response = postAuthorizedJson("/otp/validate", token, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("OTP code is required", body.get("error").asText());
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

    private ResponseEntity<String> postAuthorizedJson(String url, String token, String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        return restTemplate.postForEntity(url, entity, String.class);
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
}