package org.example.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegisterApiITRefactorTest extends BaseIntegrationTest {

    private String userLogin;
    private String duplicateLogin;
    private String existingAdminLogin;
    private String secondAdminLogin;

    @BeforeEach
    void setUp() {
        userLogin = "it_reg_user_" + shortId();
        duplicateLogin = "it_reg_dup_" + shortId();
        existingAdminLogin = "it_reg_admin_" + shortId();
        secondAdminLogin = "it_reg_admin2_" + shortId();

        deleteUserByLogin(userLogin);
        deleteUserByLogin(duplicateLogin);
        deleteUserByLogin(existingAdminLogin);
        deleteUserByLogin(secondAdminLogin);

        User existingUser = createUser(duplicateLogin);
        userRepository.createUser(existingUser);

        User existingAdmin = createAdmin(existingAdminLogin);
        userRepository.createUser(existingAdmin);
    }

    @AfterEach
    void tearDown() {
        deleteUserByLogin(userLogin);
        deleteUserByLogin(duplicateLogin);
        deleteUserByLogin(existingAdminLogin);
        deleteUserByLogin(secondAdminLogin);
    }

    @Test
    void register_shouldCreateUser_whenRequestIsValid() throws Exception {
        String requestBody = """
                {
                  "login": "%s",
                  "password": "%s",
                  "role": "USER",
                  "email": "%s@test.com",
                  "phone": "+37400112233"
                }
                """.formatted(userLogin, PASSWORD, userLogin);

        ResponseEntity<String> response = postRegisterJson(requestBody);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("User registered successfully", body.get("message").asText());
        assertTrue(body.has("userId"));
        assertTrue(userExistsByLoginIgnoreCase(userLogin));
    }

    @Test
    void register_shouldStoreLoginInLowerCase_whenMixedCaseLoginIsProvided() throws Exception {
        String mixedCaseLogin = userLogin.toUpperCase(Locale.ROOT);

        String requestBody = """
                {
                  "login": "%s",
                  "password": "%s",
                  "role": "USER",
                  "email": "%s@test.com",
                  "phone": "+37400112233"
                }
                """.formatted(mixedCaseLogin, PASSWORD, userLogin);

        ResponseEntity<String> response = postRegisterJson(requestBody);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("User registered successfully", body.get("message").asText());

        assertTrue(userExistsByLoginExact(userLogin));
        assertFalse(userExistsByLoginExact(mixedCaseLogin));
    }

    @Test
    void register_shouldReturnBadRequest_whenLoginAlreadyExists() throws Exception {
        String requestBody = """
                {
                  "login": "%s",
                  "password": "%s",
                  "role": "USER",
                  "email": "%s@test.com",
                  "phone": "+37400112233"
                }
                """.formatted(duplicateLogin, PASSWORD, duplicateLogin);

        ResponseEntity<String> response = postRegisterJson(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("User with this login already exists", body.get("error").asText());
    }

    @Test
    void register_shouldReturnBadRequest_whenLoginAlreadyExistsIgnoringCase() throws Exception {
        String duplicateLoginWithDifferentCase = duplicateLogin.toUpperCase(Locale.ROOT);

        String requestBody = """
                {
                  "login": "%s",
                  "password": "%s",
                  "role": "USER",
                  "email": "%s@test.com",
                  "phone": "+37400112233"
                }
                """.formatted(duplicateLoginWithDifferentCase, PASSWORD, duplicateLogin);

        ResponseEntity<String> response = postRegisterJson(requestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("User with this login already exists", body.get("error").asText());
    }

    @Test
    void register_shouldReturnConflict_whenSecondAdminIsCreated() throws Exception {
        String requestBody = """
                {
                  "login": "%s",
                  "password": "%s",
                  "role": "ADMIN",
                  "email": "%s@test.com",
                  "phone": "+37400110000"
                }
                """.formatted(secondAdminLogin, PASSWORD, secondAdminLogin);

        ResponseEntity<String> response = postRegisterJson(requestBody);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Admin already exists", body.get("error").asText());
        assertFalse(userExistsByLoginIgnoreCase(secondAdminLogin));
    }

    private ResponseEntity<String> postRegisterJson(String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        return restTemplate.postForEntity("/auth/register", entity, String.class);
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}