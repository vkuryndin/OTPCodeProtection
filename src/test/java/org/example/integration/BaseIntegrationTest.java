package org.example.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.example.dto.LoginRequest;
import org.example.integration.support.TestDbHelper;
import org.example.integration.support.TestUsers;
import org.example.model.User;
import org.example.repository.UserRepository;
import org.example.security.PasswordHasher;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public abstract class BaseIntegrationTest {

  protected static final String PASSWORD = "12345678";

  @Autowired protected TestRestTemplate restTemplate;

  @Autowired protected UserRepository userRepository;

  @Autowired protected PasswordHasher passwordHasher;

  @Autowired protected ObjectMapper objectMapper;

  protected String loginAndGetToken(String login) throws Exception {
    return loginAndGetToken(login, PASSWORD);
  }

  protected String loginAndGetToken(String login, String password) throws Exception {
    LoginRequest request = new LoginRequest();
    request.setLogin(login);
    request.setPassword(password);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<LoginRequest> entity = new HttpEntity<>(request, headers);

    ResponseEntity<String> response =
        restTemplate.postForEntity("/auth/login", entity, String.class);

    Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
    Assertions.assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    return body.get("token").asText();
  }

  protected ResponseEntity<String> postAuthorized(String url, String token, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(token);

    HttpEntity<Object> entity = new HttpEntity<>(body, headers);
    return restTemplate.postForEntity(url, entity, String.class);
  }

  protected ResponseEntity<String> postAuthorizedJson(
      String url, String token, String requestBody) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(token);

    HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
    return restTemplate.postForEntity(url, entity, String.class);
  }

  protected ResponseEntity<String> exchangeAuthorized(
      String url, HttpMethod method, String token, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);

    if (body instanceof String) {
      headers.setContentType(MediaType.APPLICATION_JSON);
    }

    HttpEntity<Object> entity = new HttpEntity<>(body, headers);
    return restTemplate.exchange(url, method, entity, String.class);
  }

  protected void deleteUserByLogin(String login) {
    TestDbHelper.deleteUserByLogin(login);
  }

  protected void resetOtpConfig() {
    TestDbHelper.resetOtpConfig();
  }

  protected void setOtpConfig(int codeLength, int ttlSeconds) {
    TestDbHelper.setOtpConfig(codeLength, ttlSeconds);
  }

  protected User createUser(String login) {
    return TestUsers.user(login, passwordHasher.hash(PASSWORD));
  }

  protected User createAdmin(String login) {
    return TestUsers.admin(login, passwordHasher.hash(PASSWORD));
  }

  protected Path createOtpFile(String prefix, String login) throws IOException {
    Path otpFile = Path.of("build", "test-otp", prefix + "-" + login + ".txt");
    Files.createDirectories(otpFile.getParent());
    Files.deleteIfExists(otpFile);
    return otpFile;
  }

  protected String readLastCodeFromFile(Path file) throws IOException {
    String lastLine =
        Files.readAllLines(file).stream()
            .filter(line -> !line.isBlank())
            .reduce((first, second) -> second)
            .orElseThrow(() -> new IllegalStateException("OTP file is empty"));

    String marker = "code=";
    int start = lastLine.indexOf(marker);
    if (start < 0) {
      throw new IllegalStateException("OTP code not found in file");
    }

    int valueStart = start + marker.length();
    int valueEnd = lastLine.indexOf(" |", valueStart);
    if (valueEnd < 0) {
      valueEnd = lastLine.length();
    }

    return lastLine.substring(valueStart, valueEnd).trim();
  }

  protected String escapeBackslashes(String value) {
    return value.replace("\\", "\\\\");
  }

  protected long countOtpCodesByStatus(Long userId, String operationId, String status) {
    return TestDbHelper.countOtpCodesByStatus(userId, operationId, status);
  }

  protected long countOtpCodesByUserId(Long userId) {
    return TestDbHelper.countOtpCodesByUserId(userId);
  }

  protected boolean userExistsById(Long userId) {
    return TestDbHelper.userExistsById(userId);
  }

  protected boolean userExistsByLoginIgnoreCase(String login) {
    return TestDbHelper.userExistsByLoginIgnoreCase(login);
  }

  protected boolean userExistsByLoginExact(String login) {
    return TestDbHelper.userExistsByLoginExact(login);
  }
}
