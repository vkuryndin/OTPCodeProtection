package org.example.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.UUID;
import org.example.model.Role;
import org.example.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegisterApiITRefactorTest extends BaseIntegrationTest {

  private String userLogin;
  private String duplicateLogin;
  private String existingAdminLogin;
  private String secondAdminLogin;

  @BeforeEach
  void setUp() {
    userLogin = "it_reg_user_" + shortId();
    duplicateLogin = "it_reg_dup_" + shortId();
    existingAdminLogin = "it_reg_admin_" + shortId();
    secondAdminLogin = "it_reg_admin2_" + shortId();

    deleteUserByLogin(userLogin);
    deleteUserByLogin(duplicateLogin);
    deleteUserByLogin(existingAdminLogin);
    deleteUserByLogin(secondAdminLogin);

    User existingUser = createUser(duplicateLogin);
    userRepository.createUser(existingUser);

    User existingAdmin = createAdmin(existingAdminLogin);
    userRepository.createUser(existingAdmin);
  }

  @AfterEach
  void tearDown() {
    deleteUserByLogin(userLogin);
    deleteUserByLogin(duplicateLogin);
    deleteUserByLogin(existingAdminLogin);
    deleteUserByLogin(secondAdminLogin);
  }

  @Test
  void register_shouldCreateUser_whenRequestIsValid() throws Exception {
    String requestBody =
        registerRequestJson(
            userLogin, PASSWORD, Role.USER, userLogin + "@test.com", "+37400112233");

    ResponseEntity<String> response = postRegisterJson(requestBody);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals("User registered successfully", body.get("message").asText());
    assertTrue(body.has("userId"));
    assertTrue(userExistsByLoginIgnoreCase(userLogin));
  }

  @Test
  void register_shouldStoreLoginInLowerCase_whenMixedCaseLoginIsProvided() throws Exception {
    String mixedCaseLogin = userLogin.toUpperCase(Locale.ROOT);

    String requestBody =
        registerRequestJson(
            mixedCaseLogin, PASSWORD, Role.USER, userLogin + "@test.com", "+37400112233");

    ResponseEntity<String> response = postRegisterJson(requestBody);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals("User registered successfully", body.get("message").asText());

    assertTrue(userExistsByLoginExact(userLogin));
    assertFalse(userExistsByLoginExact(mixedCaseLogin));
  }

  @Test
  void register_shouldReturnBadRequest_whenLoginAlreadyExists() throws Exception {
    String requestBody =
        registerRequestJson(
            duplicateLogin, PASSWORD, Role.USER, duplicateLogin + "@test.com", "+37400112233");

    ResponseEntity<String> response = postRegisterJson(requestBody);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals("User with this login already exists", body.get("error").asText());
  }

  @Test
  void register_shouldReturnBadRequest_whenLoginAlreadyExistsIgnoringCase() throws Exception {
    String duplicateLoginWithDifferentCase = duplicateLogin.toUpperCase(Locale.ROOT);

    String requestBody =
        registerRequestJson(
            duplicateLoginWithDifferentCase,
            PASSWORD,
            Role.USER,
            duplicateLogin + "@test.com",
            "+37400112233");

    ResponseEntity<String> response = postRegisterJson(requestBody);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals("User with this login already exists", body.get("error").asText());
  }

  @Test
  void register_shouldReturnConflict_whenSecondAdminIsCreated() throws Exception {
    String requestBody =
        registerRequestJson(
            secondAdminLogin, PASSWORD, Role.ADMIN, secondAdminLogin + "@test.com", "+37400110000");

    ResponseEntity<String> response = postRegisterJson(requestBody);

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals("Admin already exists", body.get("error").asText());
    assertFalse(userExistsByLoginIgnoreCase(secondAdminLogin));
  }

  private ResponseEntity<String> postRegisterJson(String requestBody) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
    return restTemplate.postForEntity("/auth/register", entity, String.class);
  }

  private String shortId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }

  private String registerRequestJson(
      String login, String password, Role role, String email, String phone) {
    return ("{%n"
            + "  \"login\": \"%s\",%n"
            + "  \"password\": \"%s\",%n"
            + "  \"role\": \"%s\",%n"
            + "  \"email\": \"%s\",%n"
            + "  \"phone\": \"%s\"%n"
            + "}")
        .formatted(login, password, role.name(), email, phone);
  }
}
