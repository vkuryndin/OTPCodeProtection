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
        String requestBody = """
                {
                  "operationId": "payment-auth-missing-token-001",
                  "deliveryChannel": "FILE",
                  "deliveryTarget": "%s"
                }
                """.formatted(escapeBackslashes(otpFile.toString()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/otp/generate", entity, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Authorization header is required", body.get("error").asText());
    }

    @Test
    void validateOtp_shouldReturnUnauthorized_whenTokenIsMissing() throws Exception {
        String requestBody = """
                {
                  "operationId": "payment-auth-validate-001",
                  "code": "123456"
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

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

        String requestBody = """
                {
                  "operationId": "payment-after-logout-001",
                  "deliveryChannel": "FILE",
                  "deliveryTarget": "%s"
                }
                """.formatted(escapeBackslashes(otpFile.toString()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/otp/generate", entity, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Invalid or expired token", body.get("error").asText());
    }
}