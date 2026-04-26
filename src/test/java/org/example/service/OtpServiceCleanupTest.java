package org.example.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;
import org.example.dto.ValidateOtpRequest;
import org.example.repository.OtpCodeRepository;
import org.example.repository.OtpConfigRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OtpServiceCleanupTest {

  @Test
  void cleanupStaleValidationAttempts_shouldRemoveOldEntries() throws Exception {
    OtpCodeRepository otpCodeRepository = mock(OtpCodeRepository.class);
    OtpConfigRepository otpConfigRepository = mock(OtpConfigRepository.class);
    FileDeliveryService fileDeliveryService = mock(FileDeliveryService.class);
    EmailDeliveryService emailDeliveryService = mock(EmailDeliveryService.class);
    TelegramDeliveryService telegramDeliveryService = mock(TelegramDeliveryService.class);
    SmsDeliveryService smsDeliveryService = mock(SmsDeliveryService.class);
    UserRepository userRepository = mock(UserRepository.class);
    GenerateOtpRateLimitService generateOtpRateLimitService =
        mock(GenerateOtpRateLimitService.class);

    OtpService otpService =
        new OtpService(
            otpConfigRepository,
            otpCodeRepository,
            fileDeliveryService,
            emailDeliveryService,
            telegramDeliveryService,
            smsDeliveryService,
            userRepository,
            generateOtpRateLimitService);

    when(otpCodeRepository.consumeActiveCode(1L, "cleanup-op-001", "000000")).thenReturn(null);

    ValidateOtpRequest request = new ValidateOtpRequest();
    request.setOperationId("cleanup-op-001");
    request.setCode("000000");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> otpService.validateOtp(1L, request));
    assertEquals("Invalid or expired OTP code", exception.getMessage());

    Map<String, Object> attempts = getAttemptsMap(otpService);
    assertEquals(1, attempts.size());

    Object state = attempts.values().iterator().next();
    setField(state, "lastAttemptAt", LocalDateTime.now().minusMinutes(31));
    setField(state, "blockedUntil", null);

    otpService.cleanupStaleValidationAttempts();

    assertTrue(getAttemptsMap(otpService).isEmpty());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getAttemptsMap(OtpService otpService) throws Exception {
    Field field = OtpService.class.getDeclaredField("failedValidationAttempts");
    field.setAccessible(true);
    return (Map<String, Object>) field.get(otpService);
  }

  private void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
