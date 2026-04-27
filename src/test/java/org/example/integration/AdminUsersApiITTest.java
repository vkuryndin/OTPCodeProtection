package org.example.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminUsersApiITTest extends BaseIntegrationTest {

  private String adminLogin;
  private String userLogin;
  private String targetUserLogin;

  private Long adminId;
  private Long targetUserId;

  @BeforeEach
  void setUp() {
    adminLogin = "it_admin_users_" + shortId();
    userLogin = "it_user_users_" + shortId();
    targetUserLogin = "it_target_users_" + shortId();

    deleteUserByLogin(targetUserLogin);
    deleteUserByLogin(userLogin);
    deleteUserByLogin(adminLogin);

    User admin = createAdmin(adminLogin);
    adminId = userRepository.createUser(admin);

    User user = createUser(userLogin);
    userRepository.createUser(user);

    User targetUser = createUser(targetUserLogin);
    targetUser.setPhone("+37400113344");
    targetUserId = userRepository.createUser(targetUser);
  }

  @AfterEach
  void tearDown() {
    deleteUserByLogin(targetUserLogin);
    deleteUserByLogin(userLogin);
    deleteUserByLogin(adminLogin);
  }

  @Test
  void getUsers_shouldReturnNonAdminUsers_forAdmin() throws Exception {
    String adminToken = loginAndGetToken(adminLogin);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(adminToken);

    HttpEntity<Void> entity = new HttpEntity<>(headers);

    ResponseEntity<String> response =
        restTemplate.exchange("/admin/users", HttpMethod.GET, entity, String.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertTrue(body.isArray());

    boolean containsUser = false;
    boolean containsTargetUser = false;
    boolean containsAdmin = false;

    for (JsonNode node : body) {
      String login = node.get("login").asText();
      String role = node.get("role").asText();

      if (userLogin.equals(login)) {
        containsUser = true;
      }
      if (targetUserLogin.equals(login)) {
        containsTargetUser = true;
      }
      if (adminLogin.equals(login)) {
        containsAdmin = true;
      }

      assertEquals("USER", role);
    }

    assertTrue(containsUser);
    assertTrue(containsTargetUser);
    assertFalse(containsAdmin);
  }

  @Test
  void getUsers_shouldReturnForbidden_forUser() throws Exception {
    String userToken = loginAndGetToken(userLogin);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(userToken);

    HttpEntity<Void> entity = new HttpEntity<>(headers);

    ResponseEntity<String> response =
        restTemplate.exchange("/admin/users", HttpMethod.GET, entity, String.class);

    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals("Access denied", body.get("error").asText());
  }

  @Test
  void deleteUser_shouldDeleteTargetUser_forAdmin() throws Exception {
    String adminToken = loginAndGetToken(adminLogin);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(adminToken);

    HttpEntity<Void> entity = new HttpEntity<>(headers);

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/admin/users/" + targetUserId, HttpMethod.DELETE, entity, String.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals("User deleted successfully", body.get("message").asText());
    assertEquals(targetUserId.longValue(), body.get("userId").asLong());

    assertFalse(userExistsById(targetUserId));
  }

  @Test
  void deleteUser_shouldReturnNotFound_whenUserDoesNotExist() throws Exception {
    String adminToken = loginAndGetToken(adminLogin);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(adminToken);

    HttpEntity<Void> entity = new HttpEntity<>(headers);

    ResponseEntity<String> response =
        restTemplate.exchange("/admin/users/99999999", HttpMethod.DELETE, entity, String.class);

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals("User not found", body.get("error").asText());
  }

  @Test
  void deleteUser_shouldReturnBadRequest_whenTryingToDeleteAdmin() throws Exception {
    String adminToken = loginAndGetToken(adminLogin);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(adminToken);

    HttpEntity<Void> entity = new HttpEntity<>(headers);

    ResponseEntity<String> response =
        restTemplate.exchange("/admin/users/" + adminId, HttpMethod.DELETE, entity, String.class);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals("Admin cannot be deleted", body.get("error").asText());
    assertTrue(userExistsById(adminId));
  }

  private String shortId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }
}
