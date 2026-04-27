package org.example.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.example.dto.GenerateOtpRequest;
import org.example.model.DeliveryChannel;
import org.example.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserDeleteCascadeApiITTest extends BaseIntegrationTest {

  private String adminLogin;
  private String userLogin;

  private Long userId;
  private Path otpFile;

  @BeforeEach
  void setUp() throws IOException {
    adminLogin = "it_admin_del_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    userLogin = "it_user_del_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    otpFile = createOtpFile("delete", userLogin);

    deleteUserByLogin(adminLogin);
    deleteUserByLogin(userLogin);

    User admin = createAdmin(adminLogin);
    userRepository.createUser(admin);

    User user = createUser(userLogin);
    userId = userRepository.createUser(user);

    resetOtpConfig();
  }

  @AfterEach
  void tearDown() throws IOException {
    deleteUserByLogin(userLogin);
    deleteUserByLogin(adminLogin);
    Files.deleteIfExists(otpFile);
    resetOtpConfig();
  }

  @Test
  void deleteUser_shouldAlsoDeleteUserOtpCodes() throws Exception {
    String userToken = loginAndGetToken(userLogin);

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

    String adminToken = loginAndGetToken(adminLogin);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(adminToken);

    HttpEntity<Void> deleteEntity = new HttpEntity<>(headers);

    ResponseEntity<String> deleteResponse =
        restTemplate.exchange(
            "/admin/users/" + userId, HttpMethod.DELETE, deleteEntity, String.class);

    assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
    assertNotNull(deleteResponse.getBody());

    JsonNode deleteBody = objectMapper.readTree(deleteResponse.getBody());
    assertEquals("User deleted successfully", deleteBody.get("message").asText());
    assertEquals(userId.longValue(), deleteBody.get("userId").asLong());

    assertFalse(userExistsById(userId), "User should be deleted");
    assertEquals(0, countOtpCodesByUserId(userId), "User OTP codes should be deleted by cascade");
  }
}
