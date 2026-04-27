package org.example.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.example.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminApiITTest extends BaseIntegrationTest {

  private String adminLogin;
  private String userLogin;

  @BeforeEach
  void setUp() {
    adminLogin = "it_admin_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    userLogin = "it_user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    deleteUserByLogin(adminLogin);
    deleteUserByLogin(userLogin);

    User admin = createAdmin(adminLogin);
    userRepository.createUser(admin);

    User user = createUser(userLogin);
    userRepository.createUser(user);

    resetOtpConfig();
  }

  @AfterEach
  void tearDown() {
    deleteUserByLogin(userLogin);
    deleteUserByLogin(adminLogin);
    resetOtpConfig();
  }

  @Test
  void getOtpConfig_shouldReturnConfig_forAdmin() throws Exception {
    String adminToken = loginAndGetToken(adminLogin);

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
    String adminToken = loginAndGetToken(adminLogin);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(adminToken);
    headers.setContentType(MediaType.APPLICATION_JSON);

    String requestBody =
        """
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
        restTemplate.exchange(
            "/admin/otp-config", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertNotNull(checkResponse.getBody());
    JsonNode checkBody = objectMapper.readTree(checkResponse.getBody());
    assertEquals(8, checkBody.get("codeLength").asInt());
    assertEquals(120, checkBody.get("ttlSeconds").asInt());
  }

  @Test
  void getOtpConfig_shouldReturnForbidden_forUser() throws Exception {
    String userToken = loginAndGetToken(userLogin);

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

  @Test
  void getLoggedInUsers_shouldReturnCurrentlyLoggedInUsers_forAdmin() throws Exception {
    String adminToken = loginAndGetToken(adminLogin);
    loginAndGetToken(userLogin);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(adminToken);

    HttpEntity<Void> entity = new HttpEntity<>(headers);

    ResponseEntity<String> response =
        restTemplate.exchange("/admin/logged-in-users", HttpMethod.GET, entity, String.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());

    boolean containsAdmin = false;
    boolean containsUser = false;

    for (JsonNode node : body) {
      String login = node.get("login").asText();

      if (adminLogin.equals(login)) {
        containsAdmin = true;
      }
      if (userLogin.equals(login)) {
        containsUser = true;
      }
    }

    assertTrue(containsAdmin);
    assertTrue(containsUser);
  }
}
