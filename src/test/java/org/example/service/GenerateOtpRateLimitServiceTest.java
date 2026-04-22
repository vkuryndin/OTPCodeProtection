package org.example.service;

import org.example.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GenerateOtpRateLimitServiceTest {

    @Test
    void validateAndRegisterAttempt_shouldNotBlock_whenRateLimitIsDisabled() {
        GenerateOtpRateLimitService service = new GenerateOtpRateLimitService(false, 2, 60);

        assertDoesNotThrow(() -> {
            for (int i = 0; i < 10; i++) {
                service.validateAndRegisterAttempt(1L);
            }
        });
    }

    @Test
    void validateAndRegisterAttempt_shouldBlock_whenAttemptsExceedLimit() {
        GenerateOtpRateLimitService service = new GenerateOtpRateLimitService(true, 2, 60);

        assertDoesNotThrow(() -> service.validateAndRegisterAttempt(1L));
        assertDoesNotThrow(() -> service.validateAndRegisterAttempt(1L));

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> service.validateAndRegisterAttempt(1L)
        );

        assertEquals("Too many OTP generation requests. Try again later.", exception.getMessage());
    }
}