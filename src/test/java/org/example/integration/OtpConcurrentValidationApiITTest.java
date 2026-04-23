package org.example.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.integration.support.TestRequests;
import org.example.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OtpConcurrentValidationApiITTest extends BaseIntegrationTest {

    private String testLogin;
    private Path otpFile;

    @BeforeEach
    void setUp() throws IOException {
        testLogin = "it_otp_concurrent_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        otpFile = createOtpFile("otp-concurrent", testLogin);

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
    void validateOtp_shouldAllowOnlyOneSuccessfulRequest_whenTwoRequestsAreParallel() throws Exception {
        String token = loginAndGetToken(testLogin);
        String operationId = "payment-concurrent-001";
        String code = generateOtpAndReadCode(token, operationId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        // Both threads wait on the latch so validation requests hit the API
        // almost одновременно and expose potential double-consume race conditions.
        Callable<ResponseEntity<String>> task = () -> {
            startLatch.await(5, TimeUnit.SECONDS);
            return postAuthorized(
                    "/otp/validate",
                    token,
                    TestRequests.validateOtp(operationId, code)
            );
        };

        Future<ResponseEntity<String>> firstFuture = executor.submit(task);
        Future<ResponseEntity<String>> secondFuture = executor.submit(task);

        startLatch.countDown();

        ResponseEntity<String> firstResponse = firstFuture.get(10, TimeUnit.SECONDS);
        ResponseEntity<String> secondResponse = secondFuture.get(10, TimeUnit.SECONDS);

        executor.shutdownNow();

        int okCount = 0;
        int badRequestCount = 0;

        if (firstResponse.getStatusCode() == HttpStatus.OK) {
            okCount++;
        }
        if (secondResponse.getStatusCode() == HttpStatus.OK) {
            okCount++;
        }
        if (firstResponse.getStatusCode() == HttpStatus.BAD_REQUEST) {
            badRequestCount++;
        }
        if (secondResponse.getStatusCode() == HttpStatus.BAD_REQUEST) {
            badRequestCount++;
        }

        assertEquals(1, okCount);
        assertEquals(1, badRequestCount);

        if (firstResponse.getStatusCode() == HttpStatus.BAD_REQUEST) {
            assertNotNull(firstResponse.getBody());
            JsonNode body = objectMapper.readTree(firstResponse.getBody());
            assertEquals("Invalid or expired OTP code", body.get("error").asText());
        }

        if (secondResponse.getStatusCode() == HttpStatus.BAD_REQUEST) {
            assertNotNull(secondResponse.getBody());
            JsonNode body = objectMapper.readTree(secondResponse.getBody());
            assertEquals("Invalid or expired OTP code", body.get("error").asText());
        }
    }

    private String generateOtpAndReadCode(String token, String operationId) throws Exception {
        ResponseEntity<String> response = postAuthorized(
                "/otp/generate",
                token,
                TestRequests.generateFileOtp(operationId, otpFile.toString())
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return readLastCodeFromFile(otpFile);
    }
}