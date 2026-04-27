package org.example.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.example.integration.support.TestRequests;
import org.example.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OtpConcurrentBruteforceApiITTest extends BaseIntegrationTest {

  private String testLogin;
  private Path otpFile;

  @BeforeEach
  void setUp() throws IOException {
    testLogin =
        "it_otp_bruteforce_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    otpFile = createOtpFile("otp-bruteforce", testLogin);

    deleteUserByLogin(testLogin);

    User user = createUser(testLogin);
    userRepository.createUser(user);

    resetOtpConfig();
  }

  @AfterEach
  void tearDown() throws IOException {
    deleteUserByLogin(testLogin);
    Files.deleteIfExists(otpFile);
    resetOtpConfig();
  }

  @Test
  void validateOtp_shouldBlockFurtherAttempts_afterParallelInvalidRequests() throws Exception {
    String token = loginAndGetToken(testLogin);
    String operationId = "payment-bruteforce-001";

    ResponseEntity<String> generateResponse =
        postAuthorized(
            "/otp/generate", token, TestRequests.generateFileOtp(operationId, otpFile.toString()));
    assertEquals(HttpStatus.CREATED, generateResponse.getStatusCode());

    ExecutorService executor = Executors.newFixedThreadPool(5);
    CountDownLatch startLatch = new CountDownLatch(1);

    // All invalid validation attempts start together to simulate
    // a brute-force burst against the same user + operationId.
    Callable<ResponseEntity<String>> task =
        () -> {
          boolean started = startLatch.await(5, TimeUnit.SECONDS);
          if (!started) {
            throw new IllegalStateException("Failed to start concurrent scenario in time");
          }
          return postAuthorized(
              "/otp/validate", token, TestRequests.validateOtp(operationId, "000000"));
        };

    @SuppressWarnings("unchecked")
    Future<ResponseEntity<String>>[] futures = new Future[5];
    for (int i = 0; i < 5; i++) {
      futures[i] = executor.submit(task);
    }

    startLatch.countDown();

    for (int i = 0; i < 5; i++) {
      ResponseEntity<String> response = futures[i].get(10, TimeUnit.SECONDS);
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
      assertNotNull(response.getBody());

      JsonNode body = objectMapper.readTree(response.getBody());
      assertEquals("Invalid or expired OTP code", body.get("error").asText());
    }

    executor.shutdownNow();

    ResponseEntity<String> blockedResponse =
        postAuthorized("/otp/validate", token, TestRequests.validateOtp(operationId, "000000"));

    assertEquals(HttpStatus.BAD_REQUEST, blockedResponse.getStatusCode());
    assertNotNull(blockedResponse.getBody());

    JsonNode blockedBody = objectMapper.readTree(blockedResponse.getBody());
    assertEquals(
        "Too many invalid OTP attempts. Try again later.", blockedBody.get("error").asText());
  }
}
