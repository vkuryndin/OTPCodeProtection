package org.example.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OtpApiITRefactorTest extends BaseIntegrationTest {

  private static final int DEFAULT_CODE_LENGTH = 6;

  private String testLogin;
  private Long testUserId;
  private Path otpFile;

  @BeforeEach
  void setUp() throws IOException {
    testLogin = "it_otp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    otpFile = createOtpFile("otp", testLogin);

    deleteUserByLogin(testLogin);

    User user = createUser(testLogin);
    testUserId = userRepository.createUser(user);

    resetOtpConfig();
  }

  @AfterEach
  void tearDown() throws IOException {
    deleteUserByLogin(testLogin);
    Files.deleteIfExists(otpFile);
    resetOtpConfig();
  }

  @Nested
  class GenerateOtp {

    @Test
    void shouldReturnCreatedAndWriteCodeToFile() throws Exception {
      String token = loginAndGetToken(testLogin);

      ResponseEntity<String> response =
          postAuthorized(
              "/otp/generate",
              token,
              TestRequests.generateFileOtp("payment-file-001", otpFile.toString()));

      assertEquals(HttpStatus.CREATED, response.getStatusCode());

      JsonNode body = objectMapper.readTree(response.getBody());
      assertEquals("OTP generated successfully", body.get("message").asText());
      assertEquals("payment-file-001", body.get("operationId").asText());
      assertEquals("ACTIVE", body.get("status").asText());
      assertEquals("FILE", body.get("deliveryChannel").asText());
      assertTrue(body.has("otpId"));

      assertTrue(Files.exists(otpFile));
      String code = readLastCodeFromFile(otpFile);
      assertEquals(DEFAULT_CODE_LENGTH, code.length());
    }

    @Test
    void shouldExpirePreviousActiveCode_whenSameOperationIdIsUsedAgain() throws Exception {
      setOtpConfig(10, 300);

      String token = loginAndGetToken(testLogin);
      String operationId = "payment-file-repeat-001";

      String firstCode = generateOtpAndReadCode(token, operationId);
      String secondCode = generateOtpAndReadCode(token, operationId);

      assertNotEquals(firstCode, secondCode);
      assertEquals(1, countOtpCodesByStatus(testUserId, operationId, "ACTIVE"));
      assertEquals(1, countOtpCodesByStatus(testUserId, operationId, "EXPIRED"));

      ResponseEntity<String> firstValidation =
          postAuthorized("/otp/validate", token, TestRequests.validateOtp(operationId, firstCode));
      assertEquals(HttpStatus.BAD_REQUEST, firstValidation.getStatusCode());

      JsonNode firstBody = objectMapper.readTree(firstValidation.getBody());
      assertEquals("Invalid or expired OTP code", firstBody.get("error").asText());

      ResponseEntity<String> secondValidation =
          postAuthorized("/otp/validate", token, TestRequests.validateOtp(operationId, secondCode));
      assertEquals(HttpStatus.OK, secondValidation.getStatusCode());

      JsonNode secondBody = objectMapper.readTree(secondValidation.getBody());
      assertEquals("OTP validated successfully", secondBody.get("message").asText());
    }
  }

  @Nested
  class ValidateOtp {

    @Test
    void shouldReturnUsed_whenCodeIsValid() throws Exception {
      String token = loginAndGetToken(testLogin);
      String operationId = "payment-file-valid-001";
      String code = generateOtpAndReadCode(token, operationId);

      ResponseEntity<String> response =
          postAuthorized("/otp/validate", token, TestRequests.validateOtp(operationId, code));

      assertEquals(HttpStatus.OK, response.getStatusCode());

      JsonNode body = objectMapper.readTree(response.getBody());
      assertEquals("OTP validated successfully", body.get("message").asText());
      assertEquals(operationId, body.get("operationId").asText());
      assertEquals("USED", body.get("status").asText());
    }

    @Test
    void shouldReturnBadRequest_whenCodeIsWrong() throws Exception {
      String token = loginAndGetToken(testLogin);
      String operationId = "payment-file-wrong-001";

      generateOtpAndReadCode(token, operationId);

      ResponseEntity<String> response =
          postAuthorized("/otp/validate", token, TestRequests.validateOtp(operationId, "000000"));

      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

      JsonNode body = objectMapper.readTree(response.getBody());
      assertEquals("Invalid or expired OTP code", body.get("error").asText());
    }

    @Test
    void shouldReturnBadRequest_whenCodeIsAlreadyUsed() throws Exception {
      String token = loginAndGetToken(testLogin);
      String operationId = "payment-file-used-001";
      String code = generateOtpAndReadCode(token, operationId);

      ResponseEntity<String> firstResponse =
          postAuthorized("/otp/validate", token, TestRequests.validateOtp(operationId, code));
      assertEquals(HttpStatus.OK, firstResponse.getStatusCode());

      ResponseEntity<String> secondResponse =
          postAuthorized("/otp/validate", token, TestRequests.validateOtp(operationId, code));
      assertEquals(HttpStatus.BAD_REQUEST, secondResponse.getStatusCode());

      JsonNode body = objectMapper.readTree(secondResponse.getBody());
      assertEquals("Invalid or expired OTP code", body.get("error").asText());
    }

    @Test
    void shouldReturnBadRequest_whenCodeIsExpired() throws Exception {
      setOtpConfig(DEFAULT_CODE_LENGTH, 1);

      String token = loginAndGetToken(testLogin);
      String operationId = "payment-file-expired-001";
      String code = generateOtpAndReadCode(token, operationId);

      Thread.sleep(1500);

      ResponseEntity<String> response =
          postAuthorized("/otp/validate", token, TestRequests.validateOtp(operationId, code));

      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

      JsonNode body = objectMapper.readTree(response.getBody());
      assertEquals("Invalid or expired OTP code", body.get("error").asText());
    }
  }

  @Nested
  class Concurrency {

    @Test
    void shouldKeepConsistentState_whenOldOtpIsValidatedAndNewOneIsGeneratedConcurrently()
        throws Exception {
      String token = loginAndGetToken(testLogin);
      String operationId = "payment-file-generate-validate-race-001";

      String oldCode = generateOtpAndReadCode(token, operationId);

      ExecutorService executor = Executors.newFixedThreadPool(2);
      CountDownLatch startLatch = new CountDownLatch(1);

      Callable<ResponseEntity<String>> generateTask =
          () -> {
            boolean started = startLatch.await(5, TimeUnit.SECONDS);
            if (!started) {
              throw new IllegalStateException("Failed to start concurrent scenario in time");
            }
            return postAuthorized(
                "/otp/generate",
                token,
                TestRequests.generateFileOtp(operationId, otpFile.toString()));
          };

      Callable<ResponseEntity<String>> validateTask =
          () -> {
            boolean started = startLatch.await(5, TimeUnit.SECONDS);
            if (!started) {
              throw new IllegalStateException("Failed to start concurrent scenario in time");
            }
            return postAuthorized(
                "/otp/validate", token, TestRequests.validateOtp(operationId, oldCode));
          };

      Future<ResponseEntity<String>> generateFuture = executor.submit(generateTask);
      Future<ResponseEntity<String>> validateFuture = executor.submit(validateTask);

      startLatch.countDown();

      ResponseEntity<String> generateResponse = generateFuture.get(10, TimeUnit.SECONDS);
      ResponseEntity<String> validateResponse = validateFuture.get(10, TimeUnit.SECONDS);

      executor.shutdownNow();

      assertEquals(HttpStatus.CREATED, generateResponse.getStatusCode());

      if (validateResponse.getStatusCode() == HttpStatus.OK) {
        JsonNode body = objectMapper.readTree(validateResponse.getBody());
        assertEquals("OTP validated successfully", body.get("message").asText());
      } else {
        assertEquals(HttpStatus.BAD_REQUEST, validateResponse.getStatusCode());
        JsonNode body = objectMapper.readTree(validateResponse.getBody());
        assertEquals("Invalid or expired OTP code", body.get("error").asText());
      }

      assertEquals(1, countOtpCodesByStatus(testUserId, operationId, "ACTIVE"));
      assertEquals(
          1,
          countOtpCodesByStatus(testUserId, operationId, "USED")
              + countOtpCodesByStatus(testUserId, operationId, "EXPIRED"));
    }
  }

  private String generateOtpAndReadCode(String token, String operationId) throws Exception {
    ResponseEntity<String> response =
        postAuthorized(
            "/otp/generate", token, TestRequests.generateFileOtp(operationId, otpFile.toString()));

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertTrue(Files.exists(otpFile));
    return readLastCodeFromFile(otpFile);
  }
}
