package org.example.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OtpRequestValidationApiITTest extends BaseIntegrationTest {

    private String testLogin;

    @BeforeEach
    void setUp() {
        testLogin = "it_otp_req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        deleteUserByLogin(testLogin);

        User user = createUser(testLogin);
        userRepository.createUser(user);

        resetOtpConfig();
    }

    @AfterEach
    void tearDown() {
        deleteUserByLogin(testLogin);
        resetOtpConfig();
    }

    @Test
    void generateOtp_shouldReturnBadRequest_whenOperationIdIsMissing() throws Exception {
        String token = loginAndGetToken(testLogin);

        String requestBody = """
                {
                  "deliveryChannel": "FILE",
                  "deliveryTarget": "otp-codes.txt"
                }
                """;

        ResponseEntity<String> response = postAuthorizedJson("/otp/generate", token, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Operation ID is required", body.get("error").asText());
    }

    @Test
    void generateOtp_shouldReturnBadRequest_whenDeliveryChannelIsMissing() throws Exception {
        String token = loginAndGetToken(testLogin);

        String requestBody = """
                {
                  "operationId": "payment-missing-channel-001",
                  "deliveryTarget": "otp-codes.txt"
                }
                """;

        ResponseEntity<String> response = postAuthorizedJson("/otp/generate", token, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Delivery channel is required", body.get("error").asText());
    }

    @Test
    void validateOtp_shouldReturnBadRequest_whenOperationIdIsMissing() throws Exception {
        String token = loginAndGetToken(testLogin);

        String requestBody = """
                {
                  "code": "123456"
                }
                """;

        ResponseEntity<String> response = postAuthorizedJson("/otp/validate", token, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Operation ID is required", body.get("error").asText());
    }

    @Test
    void validateOtp_shouldReturnBadRequest_whenCodeIsMissing() throws Exception {
        String token = loginAndGetToken(testLogin);

        String requestBody = """
                {
                  "operationId": "payment-missing-code-001"
                }
                """;

        ResponseEntity<String> response = postAuthorizedJson("/otp/validate", token, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("OTP code is required", body.get("error").asText());
    }
}