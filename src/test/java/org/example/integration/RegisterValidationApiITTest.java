package org.example.integration;

import java.util.stream.Stream;
import org.example.integration.support.TestHttpAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegisterValidationApiITTest extends BaseIntegrationTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidRegisterRequests")
  void register_shouldRejectInvalidRequest(
      String caseName, String requestBody, String expectedError) throws Exception {
    ResponseEntity<String> response = postRegisterJson(requestBody);

    TestHttpAssertions.assertError(response, HttpStatus.BAD_REQUEST, expectedError, objectMapper);
  }

  @Test
  void register_shouldReturnBadRequest_whenRequestBodyIsEmpty() throws Exception {
    ResponseEntity<String> response =
        restTemplate.postForEntity("/auth/register", jsonEntity(""), String.class);

    TestHttpAssertions.assertError(
        response, HttpStatus.BAD_REQUEST, "Request body is required", objectMapper);
  }

  @Test
  void register_shouldReturnBadRequest_whenRequestBodyIsInvalidJson() throws Exception {
    ResponseEntity<String> response =
        restTemplate.postForEntity("/auth/register", jsonEntity("{ invalid json }"), String.class);

    TestHttpAssertions.assertError(
        response, HttpStatus.BAD_REQUEST, "Request body is invalid", objectMapper);
  }

  private static Stream<Arguments> invalidRegisterRequests() {
    return Stream.of(
        Arguments.of(
            "missing login",
            """
                        {
                          "password": "12345678",
                          "role": "USER",
                          "email": "missing_login@test.com",
                          "phone": "+37400112233"
                        }
                        """,
            "Login is required"),
        Arguments.of(
            "invalid login characters",
            """
                        {
                          "login": "bad!*login",
                          "password": "12345678",
                          "role": "USER",
                          "email": "bad_login@test.com",
                          "phone": "+37400112233"
                        }
                        """,
            "Login may contain only letters, digits, dot, underscore and hyphen"),
        Arguments.of(
            "password too short",
            """
                        {
                          "login": "valid_user_1",
                          "password": "1234567",
                          "role": "USER",
                          "email": "short_password@test.com",
                          "phone": "+37400112233"
                        }
                        """,
            "Password must be at least 8 characters long"),
        Arguments.of(
            "missing role",
            """
                        {
                          "login": "it_reg_no_role_001",
                          "password": "12345678",
                          "email": "no_role@test.com",
                          "phone": "+37400112233"
                        }
                        """,
            "Role is required"));
  }

  private ResponseEntity<String> postRegisterJson(String requestBody) {
    return restTemplate.postForEntity("/auth/register", jsonEntity(requestBody), String.class);
  }

  private org.springframework.http.HttpEntity<String> jsonEntity(String body) {
    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    return new org.springframework.http.HttpEntity<>(body, headers);
  }
}
