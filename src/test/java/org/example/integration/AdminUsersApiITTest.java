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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminUsersApiITTest {

    private static final String PASSWORD = "12345678";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminLogin;
    private String userLogin;
    private String targetUserLogin;

    private Long adminId;
    private Long targetUserId;

    @BeforeEach
    void setUp() {
        adminLogin = "it_admin_users_" + shortId();
        userLogin = "it_user_users_" + shortId();
        targetUserLogin = "it_target_users_" + shortId();

        deleteUserByLogin(targetUserLogin);
        deleteUserByLogin(userLogin);
        deleteUserByLogin(adminLogin);

        User admin = new User();
        admin.setLogin(adminLogin);
        admin.setPasswordHash(passwordHasher.hash(PASSWORD));
        admin.setRole(Role.ADMIN);
        admin.setEmail(adminLogin + "@test.com");
        admin.setPhone("+37400110000");
        adminId = userRepository.createUser(admin);

        User user = new User();
        user.setLogin(userLogin);
        user.setPasswordHash(passwordHasher.hash(PASSWORD));
        user.setRole(Role.USER);
        user.setEmail(userLogin + "@test.com");
        user.setPhone("+37400112233");
        userRepository.createUser(user);

        User targetUser = new User();
        targetUser.setLogin(targetUserLogin);
        targetUser.setPasswordHash(passwordHasher.hash(PASSWORD));
        targetUser.setRole(Role.USER);
        targetUser.setEmail(targetUserLogin + "@test.com");
        targetUser.setPhone("+37400113344");
        targetUserId = userRepository.createUser(targetUser);
    }

    @AfterEach
    void tearDown() {
        deleteUserByLogin(targetUserLogin);
        deleteUserByLogin(userLogin);
        deleteUserByLogin(adminLogin);
    }

    @Test
    void getUsers_shouldReturnNonAdminUsers_forAdmin() throws Exception {
        String adminToken = loginAndGetToken(adminLogin);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response =
                restTemplate.exchange("/admin/users", HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertTrue(body.isArray());

        boolean containsUser = false;
        boolean containsTargetUser = false;
        boolean containsAdmin = false;

        for (JsonNode node : body) {
            String login = node.get("login").asText();
            String role = node.get("role").asText();

            if (userLogin.equals(login)) {
                containsUser = true;
            }
            if (targetUserLogin.equals(login)) {
                containsTargetUser = true;
            }
            if (adminLogin.equals(login)) {
                containsAdmin = true;
            }

            assertEquals("USER", role);
        }

        assertTrue(containsUser);
        assertTrue(containsTargetUser);
        assertFalse(containsAdmin);
    }

    @Test
    void getUsers_shouldReturnForbidden_forUser() throws Exception {
        String userToken = loginAndGetToken(userLogin);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response =
                restTemplate.exchange("/admin/users", HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Access denied", body.get("error").asText());
    }

    @Test
    void deleteUser_shouldDeleteTargetUser_forAdmin() throws Exception {
        String adminToken = loginAndGetToken(adminLogin);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/admin/users/" + targetUserId,
                HttpMethod.DELETE,
                entity,
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("User deleted successfully", body.get("message").asText());
        assertEquals(targetUserId.longValue(), body.get("userId").asLong());

        assertFalse(userExistsById(targetUserId));
    }

    @Test
    void deleteUser_shouldReturnNotFound_whenUserDoesNotExist() throws Exception {
        String adminToken = loginAndGetToken(adminLogin);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/admin/users/99999999",
                HttpMethod.DELETE,
                entity,
                String.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("User not found", body.get("error").asText());
    }

    @Test
    void deleteUser_shouldReturnBadRequest_whenTryingToDeleteAdmin() throws Exception {
        String adminToken = loginAndGetToken(adminLogin);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/admin/users/" + adminId,
                HttpMethod.DELETE,
                entity,
                String.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Admin cannot be deleted", body.get("error").asText());
        assertTrue(userExistsById(adminId));
    }

    private String loginAndGetToken(String login) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setLogin(login);
        request.setPassword(PASSWORD);

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

    private boolean userExistsById(Long id) {
        String sql = "SELECT COUNT(*) FROM users WHERE id = ?";

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, id);

            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check user existence by id", e);
        }
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

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}