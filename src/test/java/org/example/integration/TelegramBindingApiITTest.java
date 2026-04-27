package org.example.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.UUID;
import org.example.integration.support.TestDbHelper;
import org.example.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TelegramBindingApiITTest extends BaseIntegrationTest {

  private String noEmailLogin;
  private String noBindTokenLogin;
  private String expiredBindLogin;

  @BeforeEach
  void setUp() {
    noEmailLogin = "it_tg_no_email_" + shortId();
    noBindTokenLogin = "it_tg_no_token_" + shortId();
    expiredBindLogin = "it_tg_expired_" + shortId();

    deleteUserByLogin(noEmailLogin);
    deleteUserByLogin(noBindTokenLogin);
    deleteUserByLogin(expiredBindLogin);

    User noEmailUser = createUser(noEmailLogin);
    noEmailUser.setEmail(null);
    userRepository.createUser(noEmailUser);

    User noBindTokenUser = createUser(noBindTokenLogin);
    userRepository.createUser(noBindTokenUser);

    User expiredBindUser = createUser(expiredBindLogin);
    Long expiredBindUserId = userRepository.createUser(expiredBindUser);

    TestDbHelper.updateTelegramBindState(
        expiredBindUserId, "expired-bind-token-" + shortId(), LocalDateTime.now().minusMinutes(5));
  }

  @AfterEach
  void tearDown() {
    deleteUserByLogin(noEmailLogin);
    deleteUserByLogin(noBindTokenLogin);
    deleteUserByLogin(expiredBindLogin);
  }

  @Test
  void startBinding_shouldReturnUnauthorized_whenTokenIsMissing() throws Exception {
    ResponseEntity<String> response =
        restTemplate.postForEntity("/telegram/bind/start", HttpEntity.EMPTY, String.class);

    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals("Authorization header is required", body.get("error").asText());
  }

  @Test
  void startBinding_shouldReturnBadRequest_whenUserEmailIsMissing() throws Exception {
    String token = loginAndGetToken(noEmailLogin);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);

    HttpEntity<Void> entity = new HttpEntity<>(headers);

    ResponseEntity<String> response =
        restTemplate.postForEntity("/telegram/bind/start", entity, String.class);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals("User email is not set", body.get("error").asText());
  }

  @Test
  void completeBinding_shouldReturnBadRequest_whenBindTokenIsMissing() throws Exception {
    String token = loginAndGetToken(noBindTokenLogin);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);

    HttpEntity<Void> entity = new HttpEntity<>(headers);

    ResponseEntity<String> response =
        restTemplate.postForEntity("/telegram/bind/complete", entity, String.class);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals("Telegram bind token is not set", body.get("error").asText());
  }

  @Test
  void completeBinding_shouldReturnBadRequest_whenBindTokenIsExpired() throws Exception {
    String token = loginAndGetToken(expiredBindLogin);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);

    HttpEntity<Void> entity = new HttpEntity<>(headers);

    ResponseEntity<String> response =
        restTemplate.postForEntity("/telegram/bind/complete", entity, String.class);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals("Telegram bind token expired", body.get("error").asText());
  }

  private String shortId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }
}
