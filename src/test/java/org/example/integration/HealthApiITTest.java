package org.example.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthApiITTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void health_shouldReturnOk() {
    ResponseEntity<String> response = restTemplate.getForEntity("/health", String.class);

    assertEquals(200, response.getStatusCode().value());
    assertEquals("OK", response.getBody());
  }

  @Test
  void healthDb_shouldReturnUpAndTestDatabaseName() throws Exception {
    ResponseEntity<String> response = restTemplate.getForEntity("/health/db", String.class);

    assertEquals(200, response.getStatusCode().value());
    assertNotNull(response.getBody());

    JsonNode body = objectMapper.readTree(response.getBody());
    assertEquals("UP", body.get("status").asText());
    assertEquals("otp_service_test", body.get("database").asText());
  }
}
