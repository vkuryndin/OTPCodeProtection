package org.example.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeliveryValidationApiITTest extends BaseIntegrationTest {

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

        User emailMissingUser = createUser(emailMissingLogin);
        emailMissingUser.setEmail(null);
        userRepository.createUser(emailMissingUser);

        User phoneMissingUser = createUser(phoneMissingLogin);
        phoneMissingUser.setPhone(null);
        userRepository.createUser(phoneMissingUser);

        User telegramMissingUser = createUser(telegramMissingLogin);
        telegramMissingUser.setTelegramChatId(null);
        userRepository.createUser(telegramMissingUser);

        User fileUser = createUser(fileLogin);
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

    private ResponseEntity<String> generateOtpAuthorizedJson(String token, String requestBody) {
        return postAuthorizedJson("/otp/generate", token, requestBody);
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}