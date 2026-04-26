package org.example.integration;

import java.util.UUID;
import java.util.stream.Stream;
import org.example.integration.support.TestHttpAssertions;
import org.example.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OtpConfigValidationApiITTest extends BaseIntegrationTest {

  private String adminLogin;
  private String userLogin;

  @BeforeEach
  void setUp() {
    adminLogin = "it_admin_cfg_" + shortId();
    userLogin = "it_user_cfg_" + shortId();

    deleteUserByLogin(userLogin);
    deleteUserByLogin(adminLogin);

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

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidOtpConfigRequests")
  void updateOtpConfig_shouldRejectInvalidInput(
      String caseName, String requestBody, String expectedError) throws Exception {
    String adminToken = loginAndGetToken(adminLogin);

    ResponseEntity<String> response =
        exchangeAuthorized("/admin/otp-config", HttpMethod.PUT, adminToken, requestBody);

    TestHttpAssertions.assertError(response, HttpStatus.BAD_REQUEST, expectedError, objectMapper);
  }

  @Test
  void updateOtpConfig_shouldReturnUnauthorized_whenTokenIsMissing() throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            "/admin/otp-config",
            HttpMethod.PUT,
            jsonEntity(
                """
                                {
                                  "codeLength": 6,
                                  "ttlSeconds": 300
                                }
                                """),
            String.class);

    TestHttpAssertions.assertError(
        response, HttpStatus.UNAUTHORIZED, "Authorization header is required", objectMapper);
  }

  @Test
  void updateOtpConfig_shouldReturnForbidden_forUser() throws Exception {
    String userToken = loginAndGetToken(userLogin);

    ResponseEntity<String> response =
        exchangeAuthorized(
            "/admin/otp-config",
            HttpMethod.PUT,
            userToken,
            """
                        {
                          "codeLength": 6,
                          "ttlSeconds": 300
                        }
                        """);

    TestHttpAssertions.assertError(response, HttpStatus.FORBIDDEN, "Access denied", objectMapper);
  }

  private static Stream<Arguments> invalidOtpConfigRequests() {
    return Stream.of(
        Arguments.of(
            "code length too small",
            """
                        {
                          "codeLength": 3,
                          "ttlSeconds": 300
                        }
                        """,
            "Code length must be between 4 and 10"),
        Arguments.of(
            "code length too large",
            """
                        {
                          "codeLength": 11,
                          "ttlSeconds": 300
                        }
                        """,
            "Code length must be between 4 and 10"),
        Arguments.of(
            "ttl is zero",
            """
                        {
                          "codeLength": 6,
                          "ttlSeconds": 0
                        }
                        """,
            "TTL seconds must be greater than 0"),
        Arguments.of(
            "code length missing",
            """
                        {
                          "ttlSeconds": 300
                        }
                        """,
            "Code length is required"),
        Arguments.of(
            "ttl missing",
            """
                        {
                          "codeLength": 6
                        }
                        """,
            "TTL seconds is required"));
  }

  private org.springframework.http.HttpEntity<String> jsonEntity(String body) {
    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    return new org.springframework.http.HttpEntity<>(body, headers);
  }

  private String shortId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }
}
