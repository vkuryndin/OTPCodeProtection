package org.example.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "otp.generate-rate-limit.enabled=true",
                "otp.generate-rate-limit.max-attempts=2",
                "otp.generate-rate-limit.window-seconds=60"
        }
)
class OtpGenerateRateLimitApiITTest extends BaseIntegrationTest {

    private String testLogin;
    private Path otpFile;

    @BeforeEach
    void setUp() throws Exception {
        testLogin = "it_otp_rl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        otpFile = createOtpFile("otp-rate-limit", testLogin);

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
    void generateOtp_shouldReturnTooManyRequests_whenGenerateRateLimitIsExceeded() throws Exception {
        String token = loginAndGetToken(testLogin);

        ResponseEntity<String> firstResponse = generateOtp(token, "rate-limit-op-001");
        ResponseEntity<String> secondResponse = generateOtp(token, "rate-limit-op-002");
        ResponseEntity<String> thirdResponse = generateOtp(token, "rate-limit-op-003");

        assertEquals(HttpStatus.CREATED, firstResponse.getStatusCode());
        assertEquals(HttpStatus.CREATED, secondResponse.getStatusCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, thirdResponse.getStatusCode());
        assertNotNull(thirdResponse.getBody());

        JsonNode body = objectMapper.readTree(thirdResponse.getBody());
        assertEquals("Too many OTP generation requests. Try again later.", body.get("error").asText());
    }

    private ResponseEntity<String> generateOtp(String token, String operationId) {
        String requestBody = """
                {
                  "operationId": "%s",
                  "deliveryChannel": "FILE",
                  "deliveryTarget": "%s"
                }
                """.formatted(operationId, escapeBackslashes(otpFile.toString()));

        return postAuthorizedJson("/otp/generate", token, requestBody);
    }
}