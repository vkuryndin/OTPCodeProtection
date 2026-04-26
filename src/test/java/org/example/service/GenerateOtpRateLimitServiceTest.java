package org.example.service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Map;
import org.example.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

class GenerateOtpRateLimitServiceTest {

  @Test
  void validateAndRegisterAttempt_shouldNotBlock_whenRateLimitIsDisabled() {
    GenerateOtpRateLimitService service = new GenerateOtpRateLimitService(false, 2, 60);

    assertDoesNotThrow(
        () -> {
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

    RateLimitExceededException exception =
        assertThrows(
            RateLimitExceededException.class, () -> service.validateAndRegisterAttempt(1L));

    assertEquals("Too many OTP generation requests. Try again later.", exception.getMessage());
  }

  @Test
  void cleanupExpiredWindows_shouldRemoveExpiredEntries() throws Exception {
    GenerateOtpRateLimitService service = new GenerateOtpRateLimitService(true, 5, 1);

    service.validateAndRegisterAttempt(1L);
    assertEquals(1, getAttemptsMap(service).size());

    Thread.sleep(1100);

    service.cleanupExpiredWindows();

    assertTrue(getAttemptsMap(service).isEmpty());
  }

  @SuppressWarnings("unchecked")
  private Map<Long, ?> getAttemptsMap(GenerateOtpRateLimitService service) throws Exception {
    Field field = GenerateOtpRateLimitService.class.getDeclaredField("attemptsByUserId");
    field.setAccessible(true);
    return (Map<Long, ?>) field.get(service);
  }
}
