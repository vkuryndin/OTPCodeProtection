package org.example.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.*;
import org.example.integration.support.TestRequests;
import org.example.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "otp.generate-rate-limit.enabled=true",
      "otp.generate-rate-limit.max-attempts=1",
      "otp.generate-rate-limit.window-seconds=60"
    })
class OtpGenerateConcurrentRateLimitApiITTest extends BaseIntegrationTest {

  private String testLogin;
  private Path otpFile;

  @BeforeEach
  void setUp() throws Exception {
    testLogin =
        "it_otp_concurrent_rl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    otpFile = createOtpFile("otp-concurrent-rate-limit", testLogin);

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
  void generateOtp_shouldReturnOneCreatedAndOneTooManyRequests_whenRequestsAreParallel()
      throws Exception {
    String token = loginAndGetToken(testLogin);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch startLatch = new CountDownLatch(1);

    Callable<ResponseEntity<String>> task1 =
        () -> {
          startLatch.await(5, TimeUnit.SECONDS);
          return postAuthorized(
              "/otp/generate",
              token,
              TestRequests.generateFileOtp("rate-limit-parallel-001", otpFile.toString()));
        };

    Callable<ResponseEntity<String>> task2 =
        () -> {
          startLatch.await(5, TimeUnit.SECONDS);
          return postAuthorized(
              "/otp/generate",
              token,
              TestRequests.generateFileOtp("rate-limit-parallel-002", otpFile.toString()));
        };

    Future<ResponseEntity<String>> firstFuture = executor.submit(task1);
    Future<ResponseEntity<String>> secondFuture = executor.submit(task2);

    startLatch.countDown();

    ResponseEntity<String> firstResponse = firstFuture.get(10, TimeUnit.SECONDS);
    ResponseEntity<String> secondResponse = secondFuture.get(10, TimeUnit.SECONDS);

    executor.shutdownNow();

    int createdCount = 0;
    int tooManyRequestsCount = 0;

    if (firstResponse.getStatusCode() == HttpStatus.CREATED) {
      createdCount++;
    }
    if (secondResponse.getStatusCode() == HttpStatus.CREATED) {
      createdCount++;
    }
    if (firstResponse.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
      tooManyRequestsCount++;
    }
    if (secondResponse.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
      tooManyRequestsCount++;
    }

    assertEquals(1, createdCount);
    assertEquals(1, tooManyRequestsCount);

    if (firstResponse.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
      assertNotNull(firstResponse.getBody());
      JsonNode body = objectMapper.readTree(firstResponse.getBody());
      assertEquals(
          "Too many OTP generation requests. Try again later.", body.get("error").asText());
    }

    if (secondResponse.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
      assertNotNull(secondResponse.getBody());
      JsonNode body = objectMapper.readTree(secondResponse.getBody());
      assertEquals(
          "Too many OTP generation requests. Try again later.", body.get("error").asText());
    }
  }
}
