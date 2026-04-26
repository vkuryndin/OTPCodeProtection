package org.example.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.example.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
  void generateOtp_shouldReturnBadRequest_whenEmailChannelContainsDeliveryTarget()
      throws Exception {
    String token = loginAndGetToken(fileLogin);

    String requestBody =
        """
            {
              "operationId": "payment-email-extra-target-001",
              "deliveryChannel": "EMAIL",
              "deliveryTarget": "other@example.com"
            }
            """;

    ResponseEntity<String> response = postAuthorizedJson("/otp/generate", token, requestBody);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals(
        "Delivery target must not be provided for EMAIL channel", body.get("error").asText());
  }

  @Test
  void generateOtp_shouldReturnBadRequest_whenSmsChannelContainsDeliveryTarget() throws Exception {
    String token = loginAndGetToken(fileLogin);

    String requestBody =
        """
            {
              "operationId": "payment-sms-extra-target-001",
              "deliveryChannel": "SMS",
              "deliveryTarget": "+37499000000"
            }
            """;

    ResponseEntity<String> response = postAuthorizedJson("/otp/generate", token, requestBody);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals(
        "Delivery target must not be provided for SMS channel", body.get("error").asText());
  }

  @Test
  void generateOtp_shouldReturnBadRequest_whenTelegramChannelContainsDeliveryTarget()
      throws Exception {
    String token = loginAndGetToken(fileLogin);

    String requestBody =
        """
            {
              "operationId": "payment-telegram-extra-target-001",
              "deliveryChannel": "TELEGRAM",
              "deliveryTarget": "123456789"
            }
            """;

    ResponseEntity<String> response = postAuthorizedJson("/otp/generate", token, requestBody);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals(
        "Delivery target must not be provided for TELEGRAM channel", body.get("error").asText());
  }

  private String shortId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }
}
