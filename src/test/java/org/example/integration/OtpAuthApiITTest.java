package org.example.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.example.integration.support.TestRequests;
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
class OtpAuthApiITTest extends BaseIntegrationTest {

  private String testLogin;
  private Path otpFile;

  @BeforeEach
  void setUp() throws Exception {
    testLogin = "it_otp_auth_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    otpFile = createOtpFile("otp-auth", testLogin);

    deleteUserByLogin(testLogin);

    User user = createUser(testLogin);
    userRepository.createUser(user);

    resetOtpConfig();
  }

  @AfterEach
  void tearDown() throws Exception {
    deleteUserByLogin(testLogin);
    Files.deleteIfExists(otpFile);
    resetOtpConfig();
  }

  @Test
  void generateOtp_shouldReturnUnauthorized_whenTokenIsMissing() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<?> entity =
        new HttpEntity<>(
            TestRequests.generateFileOtp("payment-auth-missing-token-001", otpFile.toString()),
            headers);

    ResponseEntity<String> response =
        restTemplate.postForEntity("/otp/generate", entity, String.class);

    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals("Authorization header is required", body.get("error").asText());
  }

  @Test
  void validateOtp_shouldReturnUnauthorized_whenTokenIsMissing() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<?> entity =
        new HttpEntity<>(TestRequests.validateOtp("payment-auth-validate-001", "123456"), headers);

    ResponseEntity<String> response =
        restTemplate.postForEntity("/otp/validate", entity, String.class);

    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals("Authorization header is required", body.get("error").asText());
  }

  @Test
  void generateOtp_shouldReturnUnauthorized_afterLogout() throws Exception {
    String token = loginAndGetToken(testLogin);

    HttpHeaders logoutHeaders = new HttpHeaders();
    logoutHeaders.setBearerAuth(token);

    HttpEntity<Void> logoutEntity = new HttpEntity<>(logoutHeaders);

    ResponseEntity<String> logoutResponse =
        restTemplate.postForEntity("/auth/logout", logoutEntity, String.class);

    assertEquals(HttpStatus.OK, logoutResponse.getStatusCode());

    ResponseEntity<String> response =
        postAuthorized(
            "/otp/generate",
            token,
            TestRequests.generateFileOtp("payment-after-logout-001", otpFile.toString()));

    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals("Invalid or expired token", body.get("error").asText());
  }
}
