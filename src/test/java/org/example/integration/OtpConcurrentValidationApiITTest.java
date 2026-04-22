package org.example.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.GenerateOtpRequest;
import org.example.dto.LoginRequest;
import org.example.dto.ValidateOtpRequest;
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
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OtpConcurrentValidationApiITTest {

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
    void setUp() throws IOException {
        testLogin = "it_otp_concurrent_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        otpFile = Path.of("build", "test-otp", "otp-concurrent-" + testLogin + ".txt");

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
    void tearDown() throws IOException {
        deleteUserByLogin(testLogin);
        Files.deleteIfExists(otpFile);
        resetOtpConfig();
    }

    @Test
    void validateOtp_shouldAllowOnlyOneSuccessfulRequest_whenTwoRequestsAreParallel() throws Exception {
        String token = loginAndGetToken();
        String operationId = "payment-concurrent-001";

        String code = generateOtpAndReadCode(token, operationId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<ResponseEntity<String>> task = () -> {
            startLatch.await(5, TimeUnit.SECONDS);

            ValidateOtpRequest request = new ValidateOtpRequest();
            request.setOperationId(operationId);
            request.setCode(code);

            return postAuthorized("/otp/validate", token, request);
        };

        Future<ResponseEntity<String>> firstFuture = executor.submit(task);
        Future<ResponseEntity<String>> secondFuture = executor.submit(task);

        startLatch.countDown();

        ResponseEntity<String> firstResponse = firstFuture.get(10, TimeUnit.SECONDS);
        ResponseEntity<String> secondResponse = secondFuture.get(10, TimeUnit.SECONDS);

        executor.shutdownNow();

        int okCount = 0;
        int badRequestCount = 0;

        if (firstResponse.getStatusCode() == HttpStatus.OK) {
            okCount++;
        }
        if (secondResponse.getStatusCode() == HttpStatus.OK) {
            okCount++;
        }
        if (firstResponse.getStatusCode() == HttpStatus.BAD_REQUEST) {
            badRequestCount++;
        }
        if (secondResponse.getStatusCode() == HttpStatus.BAD_REQUEST) {
            badRequestCount++;
        }

        assertEquals(1, okCount);
        assertEquals(1, badRequestCount);

        if (firstResponse.getStatusCode() == HttpStatus.BAD_REQUEST) {
            assertNotNull(firstResponse.getBody());
            JsonNode body = objectMapper.readTree(firstResponse.getBody());
            assertEquals("Invalid or expired OTP code", body.get("error").asText());
        }

        if (secondResponse.getStatusCode() == HttpStatus.BAD_REQUEST) {
            assertNotNull(secondResponse.getBody());
            JsonNode body = objectMapper.readTree(secondResponse.getBody());
            assertEquals("Invalid or expired OTP code", body.get("error").asText());
        }
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

    private String generateOtpAndReadCode(String token, String operationId) throws Exception {
        GenerateOtpRequest request = new GenerateOtpRequest();
        request.setOperationId(operationId);
        request.setDeliveryChannel(DeliveryChannel.FILE);
        request.setDeliveryTarget(otpFile.toString());

        ResponseEntity<String> response = postAuthorized("/otp/generate", token, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return readLastCodeFromFile(otpFile);
    }

    private ResponseEntity<String> postAuthorized(String url, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity(url, entity, String.class);
    }

    private String readLastCodeFromFile(Path file) throws IOException {
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