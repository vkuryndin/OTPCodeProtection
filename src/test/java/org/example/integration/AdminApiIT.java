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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminApiIT {

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

    private final String adminPassword = "12345678";
    private final String userPassword = "12345678";

    @BeforeEach
    void setUp() {
        adminLogin = "it_admin_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        userLogin = "it_user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        deleteUserByLogin(adminLogin);
        deleteUserByLogin(userLogin);

        User admin = new User();
        admin.setLogin(adminLogin);
        admin.setPasswordHash(passwordHasher.hash(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setEmail(adminLogin + "@test.com");
        admin.setPhone("+37400110000");
        userRepository.createUser(admin);

        User user = new User();
        user.setLogin(userLogin);
        user.setPasswordHash(passwordHasher.hash(userPassword));
        user.setRole(Role.USER);
        user.setEmail(userLogin + "@test.com");
        user.setPhone("+37400112233");
        userRepository.createUser(user);

        upsertOtpConfig(6, 300);
    }

    @AfterEach
    void tearDown() {
        deleteUserByLogin(userLogin);
        deleteUserByLogin(adminLogin);
        upsertOtpConfig(6, 300);
    }

    @Test
    void getOtpConfig_shouldReturnConfig_forAdmin() throws Exception {
        String adminToken = loginAndGetToken(adminLogin, adminPassword);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response =
                restTemplate.exchange("/admin/otp-config", HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(1, body.get("id").asInt());
        assertEquals(6, body.get("codeLength").asInt());
        assertEquals(300, body.get("ttlSeconds").asInt());
    }

    @Test
    void updateOtpConfig_shouldUpdateValues_forAdmin() throws Exception {
        String adminToken = loginAndGetToken(adminLogin, adminPassword);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody = """
                {
                  "codeLength": 8,
                  "ttlSeconds": 120
                }
                """;

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response =
                restTemplate.exchange("/admin/otp-config", HttpMethod.PUT, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("OTP config updated successfully", body.get("message").asText());
        assertEquals(8, body.get("codeLength").asInt());
        assertEquals(120, body.get("ttlSeconds").asInt());

        ResponseEntity<String> checkResponse =
                restTemplate.exchange("/admin/otp-config", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        JsonNode checkBody = objectMapper.readTree(checkResponse.getBody());
        assertEquals(8, checkBody.get("codeLength").asInt());
        assertEquals(120, checkBody.get("ttlSeconds").asInt());
    }

    @Test
    void getOtpConfig_shouldReturnForbidden_forUser() throws Exception {
        String userToken = loginAndGetToken(userLogin, userPassword);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response =
                restTemplate.exchange("/admin/otp-config", HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Access denied", body.get("error").asText());
    }

    private String loginAndGetToken(String login, String password) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setLogin(login);
        request.setPassword(password);

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
}