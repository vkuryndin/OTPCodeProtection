package org.example.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.LoginRequest;
import org.example.model.Role;
import org.example.model.User;
import org.example.repository.ConnectionFactory;
import org.example.repository.UserRepository;
import org.example.security.PasswordHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthApiITTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private ObjectMapper objectMapper;

    private String testLogin;
    private final String testPassword = "12345678";

    @BeforeEach
    void setUp() {
        testLogin = "it_login_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        deleteUserByLogin(testLogin);

        User user = new User();
        user.setLogin(testLogin);
        user.setPasswordHash(passwordHasher.hash(testPassword));
        user.setRole(Role.USER);
        user.setEmail("it_" + testLogin + "@test.com");
        user.setPhone("+37400112233");

        userRepository.createUser(user);
    }

    @AfterEach
    void tearDown() {
        deleteUserByLogin(testLogin);
    }

    @Test
    void login_shouldReturnToken_whenCredentialsAreValid() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setLogin(testLogin);
        request.setPassword(testPassword);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<LoginRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/auth/login", entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());

        assertEquals("Login successful", body.get("message").asText());
        assertEquals(testLogin, body.get("login").asText());
        assertEquals("USER", body.get("role").asText());
        assertNotNull(body.get("userId"));
        assertNotNull(body.get("token"));
    }

    @Test
    void login_shouldReturnBadRequest_whenPasswordIsWrong() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setLogin(testLogin);
        request.setPassword("wrongpass");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<LoginRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/auth/login", entity, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Invalid login or password", body.get("error").asText());
    }

    @Test
    void logout_shouldReturnOk_whenTokenIsValid() throws Exception {
        String token = loginAndGetToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/auth/logout", entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Logout successful", body.get("message").asText());
    }

    @Test
    void logout_shouldInvalidateToken_forSecondLogoutAttempt() throws Exception {
        String token = loginAndGetToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> firstResponse =
                restTemplate.postForEntity("/auth/logout", entity, String.class);

        assertEquals(HttpStatus.OK, firstResponse.getStatusCode());

        ResponseEntity<String> secondResponse =
                restTemplate.postForEntity("/auth/logout", entity, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, secondResponse.getStatusCode());
        assertNotNull(secondResponse.getBody());

        JsonNode body = objectMapper.readTree(secondResponse.getBody());
        assertEquals("Invalid or expired token", body.get("error").asText());
    }

    private String loginAndGetToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setLogin(testLogin);
        request.setPassword(testPassword);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<LoginRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/auth/login", entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        return body.get("token").asText();
    }

    private void deleteUserByLogin(String login) {
        String sql = "DELETE FROM users WHERE login = ?";

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, login);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete test user", e);
        }
    }
}