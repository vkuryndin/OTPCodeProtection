package org.example.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.GenerateOtpRequest;
import org.example.dto.LoginRequest;
import org.example.model.DeliveryChannel;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserDeleteCascadeApiITTest {

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

    private final String adminPassword = "12345678";
    private final String userPassword = "12345678";

    private Long adminId;
    private Long userId;

    private Path otpFile;

    @BeforeEach
    void setUp() throws IOException {
        adminLogin = "it_admin_del_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        userLogin = "it_user_del_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        otpFile = Path.of("build", "test-otp", "delete-" + userLogin + ".txt");
        Files.createDirectories(otpFile.getParent());
        Files.deleteIfExists(otpFile);

        deleteUserByLogin(adminLogin);
        deleteUserByLogin(userLogin);

        User admin = new User();
        admin.setLogin(adminLogin);
        admin.setPasswordHash(passwordHasher.hash(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setEmail(adminLogin + "@test.com");
        admin.setPhone("+37400110000");
        adminId = userRepository.createUser(admin);

        User user = new User();
        user.setLogin(userLogin);
        user.setPasswordHash(passwordHasher.hash(userPassword));
        user.setRole(Role.USER);
        user.setEmail(userLogin + "@test.com");
        user.setPhone("+37400112233");
        userId = userRepository.createUser(user);

        upsertOtpConfig(6, 300);
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteUserByLogin(userLogin);
        deleteUserByLogin(adminLogin);
        Files.deleteIfExists(otpFile);
        upsertOtpConfig(6, 300);
    }

    @Test
    void deleteUser_shouldAlsoDeleteUserOtpCodes() throws Exception {
        String userToken = loginAndGetToken(userLogin, userPassword);

        GenerateOtpRequest generateRequest = new GenerateOtpRequest();
        generateRequest.setOperationId("payment-delete-001");
        generateRequest.setDeliveryChannel(DeliveryChannel.FILE);
        generateRequest.setDeliveryTarget(otpFile.toString());

        ResponseEntity<String> generateResponse =
                postAuthorized("/otp/generate", userToken, generateRequest);

        assertEquals(HttpStatus.CREATED, generateResponse.getStatusCode());
        assertTrue(Files.exists(otpFile));

        long otpCountBeforeDelete = countOtpCodesByUserId(userId);
        assertTrue(otpCountBeforeDelete > 0, "User should have OTP codes before deletion");

        String adminToken = loginAndGetToken(adminLogin, adminPassword);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        HttpEntity<Void> deleteEntity = new HttpEntity<>(headers);

        ResponseEntity<String> deleteResponse = restTemplate.exchange(
                "/admin/users/" + userId,
                HttpMethod.DELETE,
                deleteEntity,
                String.class
        );

        assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
        assertNotNull(deleteResponse.getBody());

        JsonNode deleteBody = objectMapper.readTree(deleteResponse.getBody());
        assertEquals("User deleted successfully", deleteBody.get("message").asText());
        assertEquals(userId.longValue(), deleteBody.get("userId").asLong());

        assertFalse(userExistsById(userId), "User should be deleted");
        assertEquals(0, countOtpCodesByUserId(userId), "User OTP codes should be deleted by cascade");
    }

    private String loginAndGetToken(String login, String password) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setLogin(login);
        request.setPassword(password);

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

    private ResponseEntity<String> postAuthorized(String url, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity(url, entity, String.class);
    }

    private long countOtpCodesByUserId(Long userId) {
        String sql = "SELECT COUNT(*) FROM otp_codes WHERE user_id = ?";

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, userId);

            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count otp_codes by user_id", e);
        }
    }

    private boolean userExistsById(Long userId) {
        String sql = "SELECT COUNT(*) FROM users WHERE id = ?";

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, userId);

            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check user existence", e);
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

    private void upsertOtpConfig(int codeLength, int ttlSeconds) {
        String sql = """
                INSERT INTO otp_config (id, code_length, ttl_seconds, updated_at)
                VALUES (1, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (id)
                DO UPDATE SET
                    code_length = EXCLUDED.code_length,
                    ttl_seconds = EXCLUDED.ttl_seconds,
                    updated_at = CURRENT_TIMESTAMP
                """;

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, codeLength);
            statement.setInt(2, ttlSeconds);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to upsert OTP config", e);
        }
    }
}