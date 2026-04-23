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
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "otp.generate-rate-limit.enabled=true",
                "otp.generate-rate-limit.max-attempts=1",
                "otp.generate-rate-limit.window-seconds=60"
        }
)
class OtpGenerateConcurrentRateLimitApiITTest {

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
        testLogin = "it_otp_concurrent_rl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        otpFile = Path.of("build", "test-otp", "otp-concurrent-rate-limit-" + testLogin + ".txt");

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
    void generateOtp_shouldReturnOneCreatedAndOneTooManyRequests_whenRequestsAreParallel() throws Exception {
        String token = loginAndGetToken();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<ResponseEntity<String>> task1 = () -> {
            startLatch.await(5, TimeUnit.SECONDS);
            return generateOtp(token, "rate-limit-parallel-001");
        };

        Callable<ResponseEntity<String>> task2 = () -> {
            startLatch.await(5, TimeUnit.SECONDS);
            return generateOtp(token, "rate-limit-parallel-002");
        };

        Future<ResponseEntity<String>> firstFuture = executor.submit(task1);
        Future<ResponseEntity<String>> secondFuture = executor.submit(task2);

        startLatch.countDown();

        ResponseEntity<String> firstResponse = firstFuture.get(10, TimeUnit.SECONDS);
        ResponseEntity<String> secondResponse = secondFuture.get(10, TimeUnit.SECONDS);

        executor.shutdownNow();

        int createdCount = 0;
        int tooManyRequestsCount = 0;

        if (firstResponse.getStatusCode() == HttpStatus.CREATED) {
            createdCount++;
        }
        if (secondResponse.getStatusCode() == HttpStatus.CREATED) {
            createdCount++;
        }
        if (firstResponse.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            tooManyRequestsCount++;
        }
        if (secondResponse.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            tooManyRequestsCount++;
        }

        assertEquals(1, createdCount);
        assertEquals(1, tooManyRequestsCount);

        if (firstResponse.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            assertNotNull(firstResponse.getBody());
            JsonNode body = objectMapper.readTree(firstResponse.getBody());
            assertEquals("Too many OTP generation requests. Try again later.", body.get("error").asText());
        }

        if (secondResponse.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            assertNotNull(secondResponse.getBody());
            JsonNode body = objectMapper.readTree(secondResponse.getBody());
            assertEquals("Too many OTP generation requests. Try again later.", body.get("error").asText());
        }
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