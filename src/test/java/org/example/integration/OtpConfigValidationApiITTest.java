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
class OtpConfigValidationApiITTest {

    private static final String PASSWORD = "12345678";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminLogin;
    private String userLogin;

    @BeforeEach
    void setUp() {
        adminLogin = "it_admin_cfg_" + shortId();
        userLogin = "it_user_cfg_" + shortId();

        deleteUserByLogin(userLogin);
        deleteUserByLogin(adminLogin);

        User admin = new User();
        admin.setLogin(adminLogin);
        admin.setPasswordHash(passwordHasher.hash(PASSWORD));
        admin.setRole(Role.ADMIN);
        admin.setEmail(adminLogin + "@test.com");
        admin.setPhone("+37400110000");
        userRepository.createUser(admin);

        User user = new User();
        user.setLogin(userLogin);
        user.setPasswordHash(passwordHasher.hash(PASSWORD));
        user.setRole(Role.USER);
        user.setEmail(userLogin + "@test.com");
        user.setPhone("+37400112233");
        userRepository.createUser(user);

        resetOtpConfig();
    }

    @AfterEach
    void tearDown() {
        deleteUserByLogin(userLogin);
        deleteUserByLogin(adminLogin);
        resetOtpConfig();
    }

    @Test
    void updateOtpConfig_shouldReturnBadRequest_whenCodeLengthIsTooSmall() throws Exception {
        String adminToken = loginAndGetToken(adminLogin);

        String requestBody = """
                {
                  "codeLength": 3,
                  "ttlSeconds": 300
                }
                """;

        ResponseEntity<String> response = putAuthorizedOtpConfig(adminToken, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Code length must be between 4 and 10", body.get("error").asText());
    }

    @Test
    void updateOtpConfig_shouldReturnBadRequest_whenCodeLengthIsTooLarge() throws Exception {
        String adminToken = loginAndGetToken(adminLogin);

        String requestBody = """
                {
                  "codeLength": 11,
                  "ttlSeconds": 300
                }
                """;

        ResponseEntity<String> response = putAuthorizedOtpConfig(adminToken, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Code length must be between 4 and 10", body.get("error").asText());
    }

    @Test
    void updateOtpConfig_shouldReturnBadRequest_whenTtlIsZero() throws Exception {
        String adminToken = loginAndGetToken(adminLogin);

        String requestBody = """
                {
                  "codeLength": 6,
                  "ttlSeconds": 0
                }
                """;

        ResponseEntity<String> response = putAuthorizedOtpConfig(adminToken, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("TTL seconds must be greater than 0", body.get("error").asText());
    }

    @Test
    void updateOtpConfig_shouldReturnBadRequest_whenCodeLengthIsMissing() throws Exception {
        String adminToken = loginAndGetToken(adminLogin);

        String requestBody = """
                {
                  "ttlSeconds": 300
                }
                """;

        ResponseEntity<String> response = putAuthorizedOtpConfig(adminToken, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Code length is required", body.get("error").asText());
    }

    @Test
    void updateOtpConfig_shouldReturnBadRequest_whenTtlIsMissing() throws Exception {
        String adminToken = loginAndGetToken(adminLogin);

        String requestBody = """
                {
                  "codeLength": 6
                }
                """;

        ResponseEntity<String> response = putAuthorizedOtpConfig(adminToken, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("TTL seconds is required", body.get("error").asText());
    }

    @Test
    void updateOtpConfig_shouldReturnUnauthorized_whenTokenIsMissing() throws Exception {
        String requestBody = """
                {
                  "codeLength": 6,
                  "ttlSeconds": 300
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/admin/otp-config",
                HttpMethod.PUT,
                entity,
                String.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Authorization header is required", body.get("error").asText());
    }

    @Test
    void updateOtpConfig_shouldReturnForbidden_forUser() throws Exception {
        String userToken = loginAndGetToken(userLogin);

        String requestBody = """
                {
                  "codeLength": 6,
                  "ttlSeconds": 300
                }
                """;

        ResponseEntity<String> response = putAuthorizedOtpConfig(userToken, requestBody);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Access denied", body.get("error").asText());
    }

    private String loginAndGetToken(String login) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setLogin(login);
        request.setPassword(PASSWORD);

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

    private ResponseEntity<String> putAuthorizedOtpConfig(String token, String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        return restTemplate.exchange(
                "/admin/otp-config",
                HttpMethod.PUT,
                entity,
                String.class
        );
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

    private void resetOtpConfig() {
        String sql = """
                INSERT INTO otp_config (id, code_length, ttl_seconds, updated_at)
                VALUES (1, 6, 300, CURRENT_TIMESTAMP)
                ON CONFLICT (id)
                DO UPDATE SET
                    code_length = EXCLUDED.code_length,
                    ttl_seconds = EXCLUDED.ttl_seconds,
                    updated_at = CURRENT_TIMESTAMP
                """;

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to reset OTP config", e);
        }
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}