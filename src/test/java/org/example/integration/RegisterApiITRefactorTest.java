package org.example.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegisterApiITRefactorTest {

    private static final String PASSWORD = "12345678";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private ObjectMapper objectMapper;

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

        User existingUser = new User();
        existingUser.setLogin(duplicateLogin);
        existingUser.setPasswordHash(passwordHasher.hash(PASSWORD));
        existingUser.setRole(Role.USER);
        existingUser.setEmail(duplicateLogin + "@test.com");
        existingUser.setPhone("+37400112233");
        userRepository.createUser(existingUser);

        User existingAdmin = new User();
        existingAdmin.setLogin(existingAdminLogin);
        existingAdmin.setPasswordHash(passwordHasher.hash(PASSWORD));
        existingAdmin.setRole(Role.ADMIN);
        existingAdmin.setEmail(existingAdminLogin + "@test.com");
        existingAdmin.setPhone("+37400110000");
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

    private boolean userExistsByLoginIgnoreCase(String login) {
        String sql = "SELECT COUNT(*) FROM users WHERE LOWER(login) = LOWER(?)";

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, login);

            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check user existence by login", e);
        }
    }

    private boolean userExistsByLoginExact(String login) {
        String sql = "SELECT COUNT(*) FROM users WHERE login = ?";

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, login);

            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check user existence by exact login", e);
        }
    }

    private void deleteUserByLogin(String login) {
        String sql = "DELETE FROM users WHERE LOWER(login) = LOWER(?)";

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, login);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete test user", e);
        }
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}