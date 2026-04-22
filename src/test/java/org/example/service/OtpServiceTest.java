package org.example.service;

import org.example.dto.GenerateOtpRequest;
import org.example.dto.ValidateOtpRequest;
import org.example.exception.NotFoundException;
import org.example.model.DeliveryChannel;
import org.example.model.OtpCode;
import org.example.model.OtpConfig;
import org.example.model.OtpStatus;
import org.example.model.Role;
import org.example.model.User;
import org.example.repository.OtpCodeRepository;
import org.example.repository.OtpConfigRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpConfigRepository otpConfigRepository;

    @Mock
    private OtpCodeRepository otpCodeRepository;

    @Mock
    private FileDeliveryService fileDeliveryService;

    @Mock
    private EmailDeliveryService emailDeliveryService;

    @Mock
    private TelegramDeliveryService telegramDeliveryService;

    @Mock
    private SmsDeliveryService smsDeliveryService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OtpService otpService;

    @Test
    void generateOtp_shouldCreateFileOtp_whenRequestIsValid() {
        Long userId = 10L;

        User user = new User();
        user.setId(userId);
        user.setLogin("user_file");
        user.setRole(Role.USER);
        user.setEmail("user_file@test.com");
        user.setPhone("+37400112233");
        user.setTelegramChatId("123456789");

        OtpConfig config = new OtpConfig();
        config.setId(1);
        config.setCodeLength(6);
        config.setTtlSeconds(300);

        GenerateOtpRequest request = new GenerateOtpRequest();
        request.setOperationId("payment-file-001");
        request.setDeliveryChannel(DeliveryChannel.FILE);
        request.setDeliveryTarget("otp-codes.txt");

        when(userRepository.findById(userId)).thenReturn(user);
        when(otpConfigRepository.getConfig()).thenReturn(config);
        when(otpCodeRepository.createOtpCode(any(OtpCode.class))).thenReturn(100L);

        Object response = otpService.generateOtp(userId, request);

        assertEquals("OTP generated successfully", readProperty(response, "message"));
        assertEquals(100L, readProperty(response, "otpId"));
        assertEquals("payment-file-001", readProperty(response, "operationId"));
        assertEquals(OtpStatus.ACTIVE, readProperty(response, "status"));
        assertEquals(DeliveryChannel.FILE, readProperty(response, "deliveryChannel"));
        assertEquals("otp-codes.txt", readProperty(response, "deliveryTarget"));
        assertNotNull(readProperty(response, "expiresAt"));

        ArgumentCaptor<OtpCode> otpCaptor = ArgumentCaptor.forClass(OtpCode.class);
        verify(otpCodeRepository).createOtpCode(otpCaptor.capture());

        OtpCode savedOtp = otpCaptor.getValue();
        assertEquals(userId, savedOtp.getUserId());
        assertEquals("payment-file-001", savedOtp.getOperationId());
        assertEquals(OtpStatus.ACTIVE, savedOtp.getStatus());
        assertEquals(DeliveryChannel.FILE, savedOtp.getDeliveryChannel());
        assertEquals("otp-codes.txt", savedOtp.getDeliveryTarget());
        assertNotNull(savedOtp.getCode());
        assertTrue(savedOtp.getCode().matches("\\d{6}"));
        assertNotNull(savedOtp.getExpiresAt());
        assertNotNull(savedOtp.getSentAt());

        verify(fileDeliveryService).saveOtpToFile(
                eq("otp-codes.txt"),
                eq(userId),
                eq("payment-file-001"),
                argThat(code -> code != null && code.matches("\\d{6}")),
                any(LocalDateTime.class)
        );

        verifyNoInteractions(emailDeliveryService, telegramDeliveryService, smsDeliveryService);
    }

    @Test
    void generateOtp_shouldSendEmail_whenChannelIsEmail() {
        Long userId = 11L;

        User user = new User();
        user.setId(userId);
        user.setLogin("user_email");
        user.setRole(Role.USER);
        user.setEmail("user_email@test.com");

        OtpConfig config = new OtpConfig();
        config.setId(1);
        config.setCodeLength(6);
        config.setTtlSeconds(300);

        GenerateOtpRequest request = new GenerateOtpRequest();
        request.setOperationId("payment-email-001");
        request.setDeliveryChannel(DeliveryChannel.EMAIL);

        when(userRepository.findById(userId)).thenReturn(user);
        when(otpConfigRepository.getConfig()).thenReturn(config);
        when(otpCodeRepository.createOtpCode(any(OtpCode.class))).thenReturn(101L);

        Object response = otpService.generateOtp(userId, request);

        assertEquals("OTP generated successfully", readProperty(response, "message"));
        assertEquals(DeliveryChannel.EMAIL, readProperty(response, "deliveryChannel"));
        assertEquals("user_email@test.com", readProperty(response, "deliveryTarget"));

        verify(emailDeliveryService).sendOtpEmail(
                eq("user_email@test.com"),
                eq(userId),
                eq("payment-email-001"),
                argThat(code -> code != null && code.matches("\\d{6}")),
                any(LocalDateTime.class)
        );

        verifyNoInteractions(fileDeliveryService, telegramDeliveryService, smsDeliveryService);
    }

    @Test
    void generateOtp_shouldThrowIllegalArgumentException_whenUserEmailIsMissing() {
        Long userId = 12L;

        User user = new User();
        user.setId(userId);
        user.setLogin("user_no_email");
        user.setRole(Role.USER);
        user.setEmail(null);

        OtpConfig config = new OtpConfig();
        config.setId(1);
        config.setCodeLength(6);
        config.setTtlSeconds(300);

        GenerateOtpRequest request = new GenerateOtpRequest();
        request.setOperationId("payment-email-002");
        request.setDeliveryChannel(DeliveryChannel.EMAIL);

        when(userRepository.findById(userId)).thenReturn(user);
        when(otpConfigRepository.getConfig()).thenReturn(config);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> otpService.generateOtp(userId, request)
        );

        assertEquals("User email is not set", exception.getMessage());

        verify(otpCodeRepository, never()).createOtpCode(any(OtpCode.class));
        verifyNoInteractions(emailDeliveryService);
    }

    @Test
    void generateOtp_shouldThrowIllegalArgumentException_whenUserPhoneIsMissing() {
        Long userId = 13L;

        User user = new User();
        user.setId(userId);
        user.setLogin("user_no_phone");
        user.setRole(Role.USER);
        user.setEmail("user_no_phone@test.com");
        user.setPhone(null);

        OtpConfig config = new OtpConfig();
        config.setId(1);
        config.setCodeLength(6);
        config.setTtlSeconds(300);

        GenerateOtpRequest request = new GenerateOtpRequest();
        request.setOperationId("payment-sms-001");
        request.setDeliveryChannel(DeliveryChannel.SMS);

        when(userRepository.findById(userId)).thenReturn(user);
        when(otpConfigRepository.getConfig()).thenReturn(config);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> otpService.generateOtp(userId, request)
        );

        assertEquals("User phone is not set", exception.getMessage());

        verify(otpCodeRepository, never()).createOtpCode(any(OtpCode.class));
        verifyNoInteractions(smsDeliveryService);
    }

    @Test
    void generateOtp_shouldThrowIllegalArgumentException_whenTelegramChatIdIsMissing() {
        Long userId = 14L;

        User user = new User();
        user.setId(userId);
        user.setLogin("user_no_tg");
        user.setRole(Role.USER);
        user.setEmail("user_no_tg@test.com");
        user.setPhone("+37400112233");
        user.setTelegramChatId(null);

        OtpConfig config = new OtpConfig();
        config.setId(1);
        config.setCodeLength(6);
        config.setTtlSeconds(300);

        GenerateOtpRequest request = new GenerateOtpRequest();
        request.setOperationId("payment-telegram-001");
        request.setDeliveryChannel(DeliveryChannel.TELEGRAM);

        when(userRepository.findById(userId)).thenReturn(user);
        when(otpConfigRepository.getConfig()).thenReturn(config);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> otpService.generateOtp(userId, request)
        );

        assertEquals("User telegram chat id is not set", exception.getMessage());

        verify(otpCodeRepository, never()).createOtpCode(any(OtpCode.class));
        verifyNoInteractions(telegramDeliveryService);
    }

    @Test
    void generateOtp_shouldThrowIllegalArgumentException_whenFileTargetIsMissing() {
        Long userId = 15L;

        User user = new User();
        user.setId(userId);
        user.setLogin("user_file_missing");
        user.setRole(Role.USER);
        user.setEmail("user_file_missing@test.com");
        user.setPhone("+37400112233");
        user.setTelegramChatId("123456789");

        OtpConfig config = new OtpConfig();
        config.setId(1);
        config.setCodeLength(6);
        config.setTtlSeconds(300);

        GenerateOtpRequest request = new GenerateOtpRequest();
        request.setOperationId("payment-file-002");
        request.setDeliveryChannel(DeliveryChannel.FILE);
        request.setDeliveryTarget("   ");

        when(userRepository.findById(userId)).thenReturn(user);
        when(otpConfigRepository.getConfig()).thenReturn(config);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> otpService.generateOtp(userId, request)
        );

        assertEquals("Delivery target is required", exception.getMessage());

        verify(otpCodeRepository, never()).createOtpCode(any(OtpCode.class));
        verifyNoInteractions(fileDeliveryService);
    }

    @Test
    void generateOtp_shouldThrowNotFoundException_whenOtpConfigIsMissing() {
        Long userId = 16L;

        User user = new User();
        user.setId(userId);
        user.setLogin("user_no_config");
        user.setRole(Role.USER);

        GenerateOtpRequest request = new GenerateOtpRequest();
        request.setOperationId("payment-file-003");
        request.setDeliveryChannel(DeliveryChannel.FILE);
        request.setDeliveryTarget("otp-codes.txt");

        when(userRepository.findById(userId)).thenReturn(user);
        when(otpConfigRepository.getConfig()).thenReturn(null);

        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> otpService.generateOtp(userId, request)
        );

        assertEquals("OTP config not found", exception.getMessage());

        verify(otpCodeRepository, never()).createOtpCode(any(OtpCode.class));
    }

    @Test
    void validateOtp_shouldReturnUsed_whenCodeIsValid() {
        Long userId = 20L;

        ValidateOtpRequest request = new ValidateOtpRequest();
        request.setOperationId("payment-validate-001");
        request.setCode("123456");

        OtpCode otpCode = new OtpCode();
        otpCode.setId(200L);
        otpCode.setUserId(userId);
        otpCode.setOperationId("payment-validate-001");
        otpCode.setCode("123456");
        otpCode.setStatus(OtpStatus.ACTIVE);

        when(otpCodeRepository.findActiveCode(userId, "payment-validate-001", "123456"))
                .thenReturn(otpCode);
        when(otpCodeRepository.markAsUsed(200L)).thenReturn(true);

        Object response = otpService.validateOtp(userId, request);

        assertEquals("OTP validated successfully", readProperty(response, "message"));
        assertEquals(200L, readProperty(response, "otpId"));
        assertEquals("payment-validate-001", readProperty(response, "operationId"));
        assertEquals(OtpStatus.USED, readProperty(response, "status"));

        verify(otpCodeRepository).expireActiveCodes();
        verify(otpCodeRepository).findActiveCode(userId, "payment-validate-001", "123456");
        verify(otpCodeRepository).markAsUsed(200L);
    }

    @Test
    void validateOtp_shouldThrowIllegalArgumentException_whenCodeIsInvalidOrExpired() {
        Long userId = 21L;

        ValidateOtpRequest request = new ValidateOtpRequest();
        request.setOperationId("payment-validate-002");
        request.setCode("000000");

        when(otpCodeRepository.findActiveCode(userId, "payment-validate-002", "000000"))
                .thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> otpService.validateOtp(userId, request)
        );

        assertEquals("Invalid or expired OTP code", exception.getMessage());

        verify(otpCodeRepository).expireActiveCodes();
        verify(otpCodeRepository).findActiveCode(userId, "payment-validate-002", "000000");
        verify(otpCodeRepository, never()).markAsUsed(anyLong());
    }

    @Test
    void validateOtp_shouldThrowIllegalArgumentException_whenMarkAsUsedFails() {
        Long userId = 22L;

        ValidateOtpRequest request = new ValidateOtpRequest();
        request.setOperationId("payment-validate-003");
        request.setCode("654321");

        OtpCode otpCode = new OtpCode();
        otpCode.setId(300L);
        otpCode.setUserId(userId);
        otpCode.setOperationId("payment-validate-003");
        otpCode.setCode("654321");
        otpCode.setStatus(OtpStatus.ACTIVE);

        when(otpCodeRepository.findActiveCode(userId, "payment-validate-003", "654321"))
                .thenReturn(otpCode);
        when(otpCodeRepository.markAsUsed(300L)).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> otpService.validateOtp(userId, request)
        );

        assertEquals("Invalid or expired OTP code", exception.getMessage());

        verify(otpCodeRepository).expireActiveCodes();
        verify(otpCodeRepository).findActiveCode(userId, "payment-validate-003", "654321");
        verify(otpCodeRepository).markAsUsed(300L);
    }

    private Object readProperty(Object target, String propertyName) {
        try {
            String capitalized = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);

            try {
                Method getter = target.getClass().getMethod("get" + capitalized);
                return getter.invoke(target);
            } catch (NoSuchMethodException ignored) {
                Method accessor = target.getClass().getMethod(propertyName);
                return accessor.invoke(target);
            }
        } catch (Exception e) {
            fail("Failed to read property '" + propertyName + "' from " + target.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }
}