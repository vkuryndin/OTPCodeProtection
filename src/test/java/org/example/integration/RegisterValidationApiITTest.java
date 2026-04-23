package org.example.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegisterValidationApiITTest extends BaseIntegrationTest {

    @Test
    void register_shouldReturnBadRequest_whenLoginIsMissing() throws Exception {
        String requestBody = """
                {
                  "password": "12345678",
                  "role": "USER",
                  "email": "missing_login@test.com",
                  "phone": "+37400112233"
                }
                """;

        ResponseEntity<String> response = postRegisterJson(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Login is required", body.get("error").asText());
    }

    @Test
    void register_shouldReturnBadRequest_whenLoginHasInvalidCharacters() throws Exception {
        String requestBody = """
                {
                  "login": "bad!*login",
                  "password": "12345678",
                  "role": "USER",
                  "email": "bad_login@test.com",
                  "phone": "+37400112233"
                }
                """;

        ResponseEntity<String> response = postRegisterJson(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Login may contain only letters, digits, dot, underscore and hyphen", body.get("error").asText());
    }

    @Test
    void register_shouldReturnBadRequest_whenPasswordIsTooShort() throws Exception {
        String requestBody = """
                {
                  "login": "valid_user_1",
                  "password": "1234567",
                  "role": "USER",
                  "email": "short_password@test.com",
                  "phone": "+37400112233"
                }
                """;

        ResponseEntity<String> response = postRegisterJson(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Password must be at least 8 characters long", body.get("error").asText());
    }

    @Test
    void register_shouldReturnBadRequest_whenRoleIsMissing() throws Exception {
        String login = "it_reg_no_role_001";
        deleteUserByLogin(login);

        String requestBody = """
                {
                  "login": "%s",
                  "password": "12345678",
                  "email": "no_role@test.com",
                  "phone": "+37400112233"
                }
                """.formatted(login);

        ResponseEntity<String> response = postRegisterJson(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Role is required", body.get("error").asText());

        deleteUserByLogin(login);
    }

    @Test
    void register_shouldReturnBadRequest_whenRequestBodyIsEmpty() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>("", headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/auth/register", entity, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Request body is required", body.get("error").asText());
    }

    @Test
    void register_shouldReturnBadRequest_whenRequestBodyIsInvalidJson() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>("{ invalid json }", headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/auth/register", entity, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Request body is invalid", body.get("error").asText());
    }

    private ResponseEntity<String> postRegisterJson(String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        return restTemplate.postForEntity("/auth/register", entity, String.class);
    }
}