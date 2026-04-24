package org.example.integration.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public final class TestHttpAssertions {

    private TestHttpAssertions() {
    }

    public static void assertError(ResponseEntity<String> response,
                                   HttpStatus expectedStatus,
                                   String expectedError,
                                   ObjectMapper objectMapper) throws Exception {
        assertEquals(expectedStatus, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(expectedError, body.get("error").asText());
    }
}