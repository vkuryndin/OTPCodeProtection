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

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "otp.generate-rate-limit.enabled=false"
        }
)
class OtpConcurrentSameOperationGenerateApiITTest extends BaseIntegrationTest {

    private static final int PARALLEL_REQUESTS = 8;

    private String testLogin;
    private Long testUserId;
    private Path otpFile;

    @BeforeEach
    void setUp() throws IOException {
        testLogin = "it_otp_same_op_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        otpFile = createOtpFile("otp-same-op", testLogin);

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

    @Test
    void generateOtp_shouldLeaveOnlyOneActiveCode_whenSameOperationIdIsGeneratedInParallel() throws Exception {
        String token = loginAndGetToken(testLogin);
        String operationId = "payment-same-operation-race-001";

        ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);

        // The latch makes concurrent generate requests start together.
        // We expect exactly one ACTIVE OTP to survive and the previous ones
        // to be moved to EXPIRED for the same user + operationId pair.
        Callable<ResponseEntity<String>> task = () -> {
            startLatch.await(5, TimeUnit.SECONDS);
            return postAuthorized(
                    "/otp/generate",
                    token,
                    TestRequests.generateFileOtp(operationId, otpFile.toString())
            );
        };

        @SuppressWarnings("unchecked")
        Future<ResponseEntity<String>>[] futures = new Future[PARALLEL_REQUESTS];
        for (int i = 0; i < PARALLEL_REQUESTS; i++) {
            futures[i] = executor.submit(task);
        }

        startLatch.countDown();

        for (int i = 0; i < PARALLEL_REQUESTS; i++) {
            ResponseEntity<String> response = futures[i].get(15, TimeUnit.SECONDS);
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());

            JsonNode body = objectMapper.readTree(response.getBody());
            assertEquals("OTP generated successfully", body.get("message").asText());
            assertEquals(operationId, body.get("operationId").asText());
            assertEquals("ACTIVE", body.get("status").asText());
        }

        executor.shutdownNow();

        long activeCount = countOtpCodesByStatus(testUserId, operationId, "ACTIVE");
        long expiredCount = countOtpCodesByStatus(testUserId, operationId, "EXPIRED");

        assertEquals(1, activeCount);
        assertEquals(PARALLEL_REQUESTS - 1L, expiredCount);
    }
}