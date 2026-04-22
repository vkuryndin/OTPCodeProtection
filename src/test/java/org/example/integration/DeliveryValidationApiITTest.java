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
class DeliveryValidationApiITTest {

    private static final String PASSWORD = "12345678";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private ObjectMapper objectMapper;

    private String emailMissingLogin;
    private String phoneMissingLogin;
    private String telegramMissingLogin;
    private String fileLogin;

    @BeforeEach
    void setUp() {
        emailMissingLogin = "it_email_missing_" + shortId();
        phoneMissingLogin = "it_phone_missing_" + shortId();
        telegramMissingLogin = "it_tg_missing_" + shortId();
        fileLogin = "it_file_missing_" + shortId();

        deleteUserByLogin(emailMissingLogin);
        deleteUserByLogin(phoneMissingLogin);
        deleteUserByLogin(telegramMissingLogin);
        deleteUserByLogin(fileLogin);

        User emailMissingUser = new User();
        emailMissingUser.setLogin(emailMissingLogin);
        emailMissingUser.setPasswordHash(passwordHasher.hash(PASSWORD));
        emailMissingUser.setRole(Role.USER);
        emailMissingUser.setEmail(null);
        emailMissingUser.setPhone("+37400112233");
        emailMissingUser.setTelegramChatId("123456789");
        userRepository.createUser(emailMissingUser);

        User phoneMissingUser = new User();
        phoneMissingUser.setLogin(phoneMissingLogin);
        phoneMissingUser.setPasswordHash(passwordHasher.hash(PASSWORD));
        phoneMissingUser.setRole(Role.USER);
        phoneMissingUser.setEmail(phoneMissingLogin + "@test.com");
        phoneMissingUser.setPhone(null);
        phoneMissingUser.setTelegramChatId("123456789");
        userRepository.createUser(phoneMissingUser);

        User telegramMissingUser = new User();
        telegramMissingUser.setLogin(telegramMissingLogin);
        telegramMissingUser.setPasswordHash(passwordHasher.hash(PASSWORD));
        telegramMissingUser.setRole(Role.USER);
        telegramMissingUser.setEmail(telegramMissingLogin + "@test.com");
        telegramMissingUser.setPhone("+37400112233");
        telegramMissingUser.setTelegramChatId(null);
        userRepository.createUser(telegramMissingUser);

        User fileUser = new User();
        fileUser.setLogin(fileLogin);
        fileUser.setPasswordHash(passwordHasher.hash(PASSWORD));
        fileUser.setRole(Role.USER);
        fileUser.setEmail(fileLogin + "@test.com");
        fileUser.setPhone("+37400112233");
        fileUser.setTelegramChatId("123456789");
        userRepository.createUser(fileUser);

        resetOtpConfig();
    }

    @AfterEach
    void tearDown() {
        deleteUserByLogin(emailMissingLogin);
        deleteUserByLogin(phoneMissingLogin);
        deleteUserByLogin(telegramMissingLogin);
        deleteUserByLogin(fileLogin);

        resetOtpConfig();
    }

    @Test
    void generateOtp_shouldReturnBadRequest_whenEmailChannelAndUserEmailIsMissing() throws Exception {
        String token = loginAndGetToken(emailMissingLogin);

        String requestBody = """
                {
                  "operationId": "payment-email-missing-001",
                  "deliveryChannel": "EMAIL"
                }
                """;

        ResponseEntity<String> response = generateOtpAuthorizedJson(token, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("User email is not set", body.get("error").asText());
    }

    @Test
    void generateOtp_shouldReturnBadRequest_whenSmsChannelAndUserPhoneIsMissing() throws Exception {
        String token = loginAndGetToken(phoneMissingLogin);

        String requestBody = """
                {
                  "operationId": "payment-sms-missing-001",
                  "deliveryChannel": "SMS"
                }
                """;

        ResponseEntity<String> response = generateOtpAuthorizedJson(token, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("User phone is not set", body.get("error").asText());
    }

    @Test
    void generateOtp_shouldReturnBadRequest_whenTelegramChannelAndChatIdIsMissing() throws Exception {
        String token = loginAndGetToken(telegramMissingLogin);

        String requestBody = """
                {
                  "operationId": "payment-telegram-missing-001",
                  "deliveryChannel": "TELEGRAM"
                }
                """;

        ResponseEntity<String> response = generateOtpAuthorizedJson(token, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("User telegram chat id is not set", body.get("error").asText());
    }

    @Test
    void generateOtp_shouldReturnBadRequest_whenFileChannelAndDeliveryTargetIsMissing() throws Exception {
        String token = loginAndGetToken(fileLogin);

        String requestBody = """
                {
                  "operationId": "payment-file-missing-001",
                  "deliveryChannel": "FILE"
                }
                """;

        ResponseEntity<String> response = generateOtpAuthorizedJson(token, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Delivery target is required", body.get("error").asText());
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

    private ResponseEntity<String> generateOtpAuthorizedJson(String token, String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        return restTemplate.postForEntity("/otp/generate", entity, String.class);
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