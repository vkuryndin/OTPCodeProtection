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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OtpApiITRefactorTest {

    private static final String PASSWORD = "12345678";
    private static final int DEFAULT_CODE_LENGTH = 6;

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
        testLogin = "it_otp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        otpFile = Path.of("build", "test-otp", "otp-" + testLogin + ".txt");

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
    void generateOtpToFile_shouldReturnCreated_andWriteCodeToFile() throws Exception {
        String token = loginAndGetToken();

        GenerateOtpRequest request = new GenerateOtpRequest();
        request.setOperationId("payment-file-001");
        request.setDeliveryChannel(DeliveryChannel.FILE);
        request.setDeliveryTarget(otpFile.toString());

        ResponseEntity<String> response = postAuthorized("/otp/generate", token, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("OTP generated successfully", body.get("message").asText());
        assertEquals("payment-file-001", body.get("operationId").asText());
        assertEquals("ACTIVE", body.get("status").asText());
        assertEquals("FILE", body.get("deliveryChannel").asText());
        assertTrue(body.has("otpId"));

        assertTrue(Files.exists(otpFile), "OTP file should be created");
        String code = readLastCodeFromFile(otpFile);

        assertNotNull(code);
        assertEquals(DEFAULT_CODE_LENGTH, code.length());
    }

    @Test
    void validateOtp_shouldReturnUsed_whenCodeIsValid() throws Exception {
        String token = loginAndGetToken();
        String operationId = "payment-file-valid-001";

        String code = generateOtpAndReadCode(token, operationId);

        ValidateOtpRequest request = new ValidateOtpRequest();
        request.setOperationId(operationId);
        request.setCode(code);

        ResponseEntity<String> response = postAuthorized("/otp/validate", token, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("OTP validated successfully", body.get("message").asText());
        assertEquals(operationId, body.get("operationId").asText());
        assertEquals("USED", body.get("status").asText());
    }

    @Test
    void validateOtp_shouldReturnBadRequest_whenCodeIsWrong() throws Exception {
        String token = loginAndGetToken();
        String operationId = "payment-file-wrong-001";

        generateOtpAndReadCode(token, operationId);

        ValidateOtpRequest request = new ValidateOtpRequest();
        request.setOperationId(operationId);
        request.setCode("000000");

        ResponseEntity<String> response = postAuthorized("/otp/validate", token, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Invalid or expired OTP code", body.get("error").asText());
    }

    @Test
    void validateOtp_shouldReturnBadRequest_whenCodeIsAlreadyUsed() throws Exception {
        String token = loginAndGetToken();
        String operationId = "payment-file-used-001";

        String code = generateOtpAndReadCode(token, operationId);

        ValidateOtpRequest request = new ValidateOtpRequest();
        request.setOperationId(operationId);
        request.setCode(code);

        ResponseEntity<String> firstResponse = postAuthorized("/otp/validate", token, request);
        assertEquals(HttpStatus.OK, firstResponse.getStatusCode());

        ResponseEntity<String> secondResponse = postAuthorized("/otp/validate", token, request);
        assertEquals(HttpStatus.BAD_REQUEST, secondResponse.getStatusCode());

        JsonNode body = objectMapper.readTree(secondResponse.getBody());
        assertEquals("Invalid or expired OTP code", body.get("error").asText());
    }

    @Test
    void validateOtp_shouldReturnBadRequest_whenCodeIsExpired() throws Exception {
        setOtpConfig(DEFAULT_CODE_LENGTH, 1);

        String token = loginAndGetToken();
        String operationId = "payment-file-expired-001";

        String code = generateOtpAndReadCode(token, operationId);

        Thread.sleep(1500);

        ValidateOtpRequest request = new ValidateOtpRequest();
        request.setOperationId(operationId);
        request.setCode(code);

        ResponseEntity<String> response = postAuthorized("/otp/validate", token, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Invalid or expired OTP code", body.get("error").asText());
    }

    @Test
    void generateOtp_shouldExpirePreviousActiveCode_whenSameOperationIdIsUsedAgain() throws Exception {
        setOtpConfig(10, 300);

        String token = loginAndGetToken();
        String operationId = "payment-file-repeat-001";

        String firstCode = generateOtpAndReadCode(token, operationId);
        String secondCode = generateOtpAndReadCode(token, operationId);

        assertNotEquals(firstCode, secondCode);

        assertEquals(1, countOtpCodesByStatus(testUserId, operationId, "ACTIVE"));
        assertEquals(1, countOtpCodesByStatus(testUserId, operationId, "EXPIRED"));

        ValidateOtpRequest firstRequest = new ValidateOtpRequest();
        firstRequest.setOperationId(operationId);
        firstRequest.setCode(firstCode);

        ResponseEntity<String> firstValidation = postAuthorized("/otp/validate", token, firstRequest);
        assertEquals(HttpStatus.BAD_REQUEST, firstValidation.getStatusCode());

        JsonNode firstBody = objectMapper.readTree(firstValidation.getBody());
        assertEquals("Invalid or expired OTP code", firstBody.get("error").asText());

        ValidateOtpRequest secondRequest = new ValidateOtpRequest();
        secondRequest.setOperationId(operationId);
        secondRequest.setCode(secondCode);

        ResponseEntity<String> secondValidation = postAuthorized("/otp/validate", token, secondRequest);
        assertEquals(HttpStatus.OK, secondValidation.getStatusCode());

        JsonNode secondBody = objectMapper.readTree(secondValidation.getBody());
        assertEquals("OTP validated successfully", secondBody.get("message").asText());
    }

    @Test
    void validateOldOtpAndGenerateNewOtpConcurrently_shouldKeepConsistentState() throws Exception {
        String token = loginAndGetToken();
        String operationId = "payment-file-generate-validate-race-001";

        String oldCode = generateOtpAndReadCode(token, operationId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<ResponseEntity<String>> generateTask = () -> {
            startLatch.await(5, TimeUnit.SECONDS);

            GenerateOtpRequest request = new GenerateOtpRequest();
            request.setOperationId(operationId);
            request.setDeliveryChannel(DeliveryChannel.FILE);
            request.setDeliveryTarget(otpFile.toString());

            return postAuthorized("/otp/generate", token, request);
        };

        Callable<ResponseEntity<String>> validateTask = () -> {
            startLatch.await(5, TimeUnit.SECONDS);

            ValidateOtpRequest request = new ValidateOtpRequest();
            request.setOperationId(operationId);
            request.setCode(oldCode);

            return postAuthorized("/otp/validate", token, request);
        };

        Future<ResponseEntity<String>> generateFuture = executor.submit(generateTask);
        Future<ResponseEntity<String>> validateFuture = executor.submit(validateTask);

        startLatch.countDown();

        ResponseEntity<String> generateResponse = generateFuture.get(10, TimeUnit.SECONDS);
        ResponseEntity<String> validateResponse = validateFuture.get(10, TimeUnit.SECONDS);

        executor.shutdownNow();

        assertEquals(HttpStatus.CREATED, generateResponse.getStatusCode());

        if (validateResponse.getStatusCode() == HttpStatus.OK) {
            assertNotNull(validateResponse.getBody());
            JsonNode body = objectMapper.readTree(validateResponse.getBody());
            assertEquals("OTP validated successfully", body.get("message").asText());
        } else {
            assertEquals(HttpStatus.BAD_REQUEST, validateResponse.getStatusCode());
            assertNotNull(validateResponse.getBody());
            JsonNode body = objectMapper.readTree(validateResponse.getBody());
            assertEquals("Invalid or expired OTP code", body.get("error").asText());
        }

        assertEquals(1, countOtpCodesByStatus(testUserId, operationId, "ACTIVE"));
        assertEquals(1,
                countOtpCodesByStatus(testUserId, operationId, "USED")
                        + countOtpCodesByStatus(testUserId, operationId, "EXPIRED"));
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
        assertTrue(Files.exists(otpFile));

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
        setOtpConfig(DEFAULT_CODE_LENGTH, 300);
    }

    private void setOtpConfig(int codeLength, int ttlSeconds) {
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
}