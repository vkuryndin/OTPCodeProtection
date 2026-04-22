package org.example.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthApiITTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void health_shouldReturnOk() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/health", String.class);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("OK", response.getBody());
    }

    @Test
    void healthDb_shouldReturnUpAndTestDatabaseName() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/health/db", String.class);

        assertEquals(200, response.getStatusCode().value());

        String body = response.getBody();
        assertNotNull(body);

        assertTrue(body.contains("\"status\":\"UP\""));
        assertTrue(body.contains("\"database\":\"otp_service_test\""));
    }
}