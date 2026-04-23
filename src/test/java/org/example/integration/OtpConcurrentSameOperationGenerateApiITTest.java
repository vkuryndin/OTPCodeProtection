package org.example.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.GenerateOtpRequest;
import org.example.dto.LoginRequest;
import org.example.model.DeliveryChannel;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "otp.generate-rate-limit.enabled=false"
        }
)
class OtpConcurrentSameOperationGenerateApiITTest {

    private static final String PASSWORD = "12345678";
    private static final int PARALLEL_REQUESTS = 8;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private ObjectMapper objectMapper;

    private String testLogin;
    private Long testUserId;
    private Path otpFile;

    @BeforeEach
    void setUp() throws IOException {
        testLogin = "it_otp_same_op_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        otpFile = Path.of("build", "test-otp", "otp-same-op-" + testLogin + ".txt");

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

        testUserId = userRepository.createUser(user);

        resetOtpConfig();
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteUserByLogin(testLogin);
        Files.deleteIfExists(otpFile);
        resetOtpConfig();
    }

    @Test
    void generateOtp_shouldLeaveOnlyOneActiveCode_whenSameOperationIdIsGeneratedInParallel() throws Exception {
        String token = loginAndGetToken();
        String operationId = "payment-same-operation-race-001";

        ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<ResponseEntity<String>> task = () -> {
            startLatch.await(5, TimeUnit.SECONDS);

            GenerateOtpRequest request = new GenerateOtpRequest();
            request.setOperationId(operationId);
            request.setDeliveryChannel(DeliveryChannel.FILE);
            request.setDeliveryTarget(otpFile.toString());

            return postAuthorized("/otp/generate", token, request);
        };

        Future<ResponseEntity<String>>[] futures = new Future[PARALLEL_REQUESTS];
        for (int i = 0; i < PARALLEL_REQUESTS; i++) {
            futures[i] = executor.submit(task);
        }

        startLatch.countDown();

        for (int i = 0; i < PARALLEL_REQUESTS; i++) {
            ResponseEntity<String> response = futures[i].get(15, TimeUnit.SECONDS);
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());

            JsonNode body = objectMapper.readTree(response.getBody());
            assertEquals("OTP generated successfully", body.get("message").asText());
            assertEquals(operationId, body.get("operationId").asText());
            assertEquals("ACTIVE", body.get("status").asText());
        }

        executor.shutdownNow();

        long activeCount = countOtpCodesByStatus(testUserId, operationId, "ACTIVE");
        long expiredCount = countOtpCodesByStatus(testUserId, operationId, "EXPIRED");

        assertEquals(1, activeCount);
        assertEquals(PARALLEL_REQUESTS - 1L, expiredCount);
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

    private ResponseEntity<String> postAuthorized(String url, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity(url, entity, String.class);
    }

    private long countOtpCodesByStatus(Long userId, String operationId, String status) {
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
}