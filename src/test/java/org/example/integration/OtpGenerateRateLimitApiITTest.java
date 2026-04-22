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

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "otp.generate-rate-limit.enabled=true",
                "otp.generate-rate-limit.max-attempts=2",
                "otp.generate-rate-limit.window-seconds=60"
        }
)
class OtpGenerateRateLimitApiITTest {

    private static final String PASSWORD = "12345678";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private ObjectMapper objectMapper;

    private String testLogin;
    private Path otpFile;

    @BeforeEach
    void setUp() throws Exception {
        testLogin = "it_otp_rl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        otpFile = Path.of("build", "test-otp", "otp-rate-limit-" + testLogin + ".txt");

        Files.createDirectories(otpFile.getParent());
        Files.deleteIfExists(otpFile);

        deleteUserByLogin(testLogin);

        User user = new User();
        user.setLogin(testLogin);
        user.setPasswordHash(passwordHasher.hash(PASSWORD));
        user.setRole(Role.USER);
        user.setEmail(testLogin + "@test.com");
        user.setPhone("+37400112233");
        user.setTelegramChatId("123456789");
        userRepository.createUser(user);

        resetOtpConfig();
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteUserByLogin(testLogin);
        Files.deleteIfExists(otpFile);
        resetOtpConfig();
    }

    @Test
    void generateOtp_shouldReturnTooManyRequests_whenGenerateRateLimitIsExceeded() throws Exception {
        String token = loginAndGetToken();

        ResponseEntity<String> firstResponse = generateOtp(token, "rate-limit-op-001");
        ResponseEntity<String> secondResponse = generateOtp(token, "rate-limit-op-002");
        ResponseEntity<String> thirdResponse = generateOtp(token, "rate-limit-op-003");

        assertEquals(HttpStatus.CREATED, firstResponse.getStatusCode());
        assertEquals(HttpStatus.CREATED, secondResponse.getStatusCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, thirdResponse.getStatusCode());
        assertNotNull(thirdResponse.getBody());

        JsonNode body = objectMapper.readTree(thirdResponse.getBody());
        assertEquals("Too many OTP generation requests. Try again later.", body.get("error").asText());
    }

    private ResponseEntity<String> generateOtp(String token, String operationId) {
        String requestBody = """
                {
                  "operationId": "%s",
                  "deliveryChannel": "FILE",
                  "deliveryTarget": "%s"
                }
                """.formatted(operationId, escapeBackslashes(otpFile.toString()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        return restTemplate.postForEntity("/otp/generate", entity, String.class);
    }

    private String loginAndGetToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setLogin(testLogin);
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

    private String escapeBackslashes(String value) {
        return value.replace("\\", "\\\\");
    }
}