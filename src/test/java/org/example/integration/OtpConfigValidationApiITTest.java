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

    @Test
    void updateOtpConfig_shouldReturnBadRequest_whenCodeLengthIsTooSmall() throws Exception {
        String adminToken = loginAndGetToken(adminLogin);

        String requestBody = """
                {
                  "codeLength": 3,
                  "ttlSeconds": 300
                }
                """;

        ResponseEntity<String> response = putAuthorizedOtpConfig(adminToken, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Code length must be between 4 and 10", body.get("error").asText());
    }

    @Test
    void updateOtpConfig_shouldReturnBadRequest_whenCodeLengthIsTooLarge() throws Exception {
        String adminToken = loginAndGetToken(adminLogin);

        String requestBody = """
                {
                  "codeLength": 11,
                  "ttlSeconds": 300
                }
                """;

        ResponseEntity<String> response = putAuthorizedOtpConfig(adminToken, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Code length must be between 4 and 10", body.get("error").asText());
    }

    @Test
    void updateOtpConfig_shouldReturnBadRequest_whenTtlIsZero() throws Exception {
        String adminToken = loginAndGetToken(adminLogin);

        String requestBody = """
                {
                  "codeLength": 6,
                  "ttlSeconds": 0
                }
                """;

        ResponseEntity<String> response = putAuthorizedOtpConfig(adminToken, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("TTL seconds must be greater than 0", body.get("error").asText());
    }

    @Test
    void updateOtpConfig_shouldReturnBadRequest_whenCodeLengthIsMissing() throws Exception {
        String adminToken = loginAndGetToken(adminLogin);

        String requestBody = """
                {
                  "ttlSeconds": 300
                }
                """;

        ResponseEntity<String> response = putAuthorizedOtpConfig(adminToken, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Code length is required", body.get("error").asText());
    }

    @Test
    void updateOtpConfig_shouldReturnBadRequest_whenTtlIsMissing() throws Exception {
        String adminToken = loginAndGetToken(adminLogin);

        String requestBody = """
                {
                  "codeLength": 6
                }
                """;

        ResponseEntity<String> response = putAuthorizedOtpConfig(adminToken, requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("TTL seconds is required", body.get("error").asText());
    }

    @Test
    void updateOtpConfig_shouldReturnUnauthorized_whenTokenIsMissing() throws Exception {
        String requestBody = """
                {
                  "codeLength": 6,
                  "ttlSeconds": 300
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/admin/otp-config",
                HttpMethod.PUT,
                entity,
                String.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Authorization header is required", body.get("error").asText());
    }

    @Test
    void updateOtpConfig_shouldReturnForbidden_forUser() throws Exception {
        String userToken = loginAndGetToken(userLogin);

        String requestBody = """
                {
                  "codeLength": 6,
                  "ttlSeconds": 300
                }
                """;

        ResponseEntity<String> response = putAuthorizedOtpConfig(userToken, requestBody);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Access denied", body.get("error").asText());
    }

    private ResponseEntity<String> putAuthorizedOtpConfig(String token, String requestBody) {
        return exchangeAuthorized("/admin/otp-config", HttpMethod.PUT, token, requestBody);
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}