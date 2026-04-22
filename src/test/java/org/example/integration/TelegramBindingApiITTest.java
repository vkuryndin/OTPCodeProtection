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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TelegramBindingApiITTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private ObjectMapper objectMapper;

    private final String password = "12345678";

    private String noEmailLogin;
    private String noBindTokenLogin;
    private String expiredBindLogin;

    private Long noBindTokenUserId;
    private Long expiredBindUserId;

    @BeforeEach
    void setUp() {
        noEmailLogin = "it_tg_no_email_" + shortId();
        noBindTokenLogin = "it_tg_no_token_" + shortId();
        expiredBindLogin = "it_tg_expired_" + shortId();

        deleteUserByLogin(noEmailLogin);
        deleteUserByLogin(noBindTokenLogin);
        deleteUserByLogin(expiredBindLogin);

        User noEmailUser = new User();
        noEmailUser.setLogin(noEmailLogin);
        noEmailUser.setPasswordHash(passwordHasher.hash(password));
        noEmailUser.setRole(Role.USER);
        noEmailUser.setEmail(null);
        noEmailUser.setPhone("+37400112233");
        userRepository.createUser(noEmailUser);

        User noBindTokenUser = new User();
        noBindTokenUser.setLogin(noBindTokenLogin);
        noBindTokenUser.setPasswordHash(passwordHasher.hash(password));
        noBindTokenUser.setRole(Role.USER);
        noBindTokenUser.setEmail(noBindTokenLogin + "@test.com");
        noBindTokenUser.setPhone("+37400112233");
        noBindTokenUserId = userRepository.createUser(noBindTokenUser);

        User expiredBindUser = new User();
        expiredBindUser.setLogin(expiredBindLogin);
        expiredBindUser.setPasswordHash(passwordHasher.hash(password));
        expiredBindUser.setRole(Role.USER);
        expiredBindUser.setEmail(expiredBindLogin + "@test.com");
        expiredBindUser.setPhone("+37400112233");
        expiredBindUserId = userRepository.createUser(expiredBindUser);

        updateTelegramBindState(
                expiredBindUserId,
                "expired-bind-token-" + shortId(),
                LocalDateTime.now().minusMinutes(5)
        );
    }

    @AfterEach
    void tearDown() {
        deleteUserByLogin(noEmailLogin);
        deleteUserByLogin(noBindTokenLogin);
        deleteUserByLogin(expiredBindLogin);
    }

    @Test
    void startBinding_shouldReturnUnauthorized_whenTokenIsMissing() throws Exception {
        ResponseEntity<String> response =
                restTemplate.postForEntity("/telegram/bind/start", HttpEntity.EMPTY, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Authorization header is required", body.get("error").asText());
    }

    @Test
    void startBinding_shouldReturnBadRequest_whenUserEmailIsMissing() throws Exception {
        String token = loginAndGetToken(noEmailLogin, password);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/telegram/bind/start", entity, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("User email is not set", body.get("error").asText());
    }

    @Test
    void completeBinding_shouldReturnBadRequest_whenBindTokenIsMissing() throws Exception {
        String token = loginAndGetToken(noBindTokenLogin, password);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/telegram/bind/complete", entity, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Telegram bind token is not set", body.get("error").asText());
    }

    @Test
    void completeBinding_shouldReturnBadRequest_whenBindTokenIsExpired() throws Exception {
        String token = loginAndGetToken(expiredBindLogin, password);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/telegram/bind/complete", entity, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("Telegram bind token expired", body.get("error").asText());
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

    private void updateTelegramBindState(Long userId, String bindToken, LocalDateTime expiresAt) {
        String sql = """
                UPDATE users
                SET telegram_bind_token = ?, telegram_bind_expires_at = ?
                WHERE id = ?
                """;

        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, bindToken);
            statement.setTimestamp(2, Timestamp.valueOf(expiresAt));
            statement.setLong(3, userId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update telegram bind state", e);
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