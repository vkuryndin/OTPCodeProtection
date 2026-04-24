package org.example.service;

import org.example.dto.GenerateOtpRequest;
import org.example.dto.OtpGenerationResponse;
import org.example.dto.OtpValidationResponse;
import org.example.dto.ValidateOtpRequest;
import org.example.exception.NotFoundException;
import org.example.exception.RateLimitExceededException;
import org.example.model.DeliveryChannel;
import org.example.model.OtpCode;
import org.example.model.OtpConfig;
import org.example.model.OtpStatus;
import org.example.model.Role;
import org.example.model.User;
import org.example.repository.OtpCodeRepository;
import org.example.repository.OtpConfigRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    @Mock
    private GenerateOtpRateLimitService generateOtpRateLimitService;

    @InjectMocks
    private OtpService otpService;

    @Nested
    class GenerateOtp {

        @Test
        void shouldCreateFileOtp_whenRequestIsValid() {
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
            when(otpCodeRepository.createOtpCodeReplacingActive(any(OtpCode.class))).thenReturn(100L);

            OtpGenerationResponse response = otpService.generateOtp(userId, request);

            assertEquals("OTP generated successfully", response.message());
            assertEquals(100L, response.otpId());
            assertEquals("payment-file-001", response.operationId());
            assertEquals(OtpStatus.ACTIVE, response.status());
            assertEquals(DeliveryChannel.FILE, response.deliveryChannel());
            assertEquals("otp-codes.txt", response.deliveryTarget());
            assertNotNull(response.expiresAt());

            ArgumentCaptor<OtpCode> otpCaptor = ArgumentCaptor.forClass(OtpCode.class);
            verify(otpCodeRepository).createOtpCodeReplacingActive(otpCaptor.capture());

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

            verify(generateOtpRateLimitService).validateAndRegisterAttempt(userId);
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
        void shouldThrowRateLimitExceededException_whenGenerationLimitIsExceeded() {
            Long userId = 11L;

            User user = new User();
            user.setId(userId);
            user.setLogin("user_rate_limit");
            user.setRole(Role.USER);
            user.setEmail("user_rate_limit@test.com");
            user.setPhone("+37400112233");
            user.setTelegramChatId("123456789");

            OtpConfig config = new OtpConfig();
            config.setId(1);
            config.setCodeLength(6);
            config.setTtlSeconds(300);

            GenerateOtpRequest request = new GenerateOtpRequest();
            request.setOperationId("payment-file-rate-limit-001");
            request.setDeliveryChannel(DeliveryChannel.FILE);
            request.setDeliveryTarget("otp-codes.txt");

            when(userRepository.findById(userId)).thenReturn(user);
            when(otpConfigRepository.getConfig()).thenReturn(config);

            doThrow(new RateLimitExceededException("Too many OTP generation requests. Try again later."))
                    .when(generateOtpRateLimitService)
                    .validateAndRegisterAttempt(userId);

            RateLimitExceededException exception = assertThrows(
                    RateLimitExceededException.class,
                    () -> otpService.generateOtp(userId, request)
            );

            assertEquals("Too many OTP generation requests. Try again later.", exception.getMessage());

            verify(generateOtpRateLimitService).validateAndRegisterAttempt(userId);
            verify(otpCodeRepository, never()).createOtpCodeReplacingActive(any(OtpCode.class));
            verifyNoInteractions(fileDeliveryService, emailDeliveryService, telegramDeliveryService, smsDeliveryService);
        }

        @Test
        void shouldSendEmail_whenChannelIsEmail() {
            Long userId = 12L;

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
            when(otpCodeRepository.createOtpCodeReplacingActive(any(OtpCode.class))).thenReturn(101L);

            OtpGenerationResponse response = otpService.generateOtp(userId, request);

            assertEquals("OTP generated successfully", response.message());
            assertEquals(DeliveryChannel.EMAIL, response.deliveryChannel());
            assertEquals("user_email@test.com", response.deliveryTarget());

            verify(generateOtpRateLimitService).validateAndRegisterAttempt(userId);
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
        void shouldDeleteCreatedOtp_whenDeliveryFails() {
            Long userId = 30L;

            User user = new User();
            user.setId(userId);
            user.setLogin("user_delivery_fail");
            user.setRole(Role.USER);
            user.setEmail("user_delivery_fail@test.com");
            user.setPhone("+37400112233");
            user.setTelegramChatId("123456789");

            OtpConfig config = new OtpConfig();
            config.setId(1);
            config.setCodeLength(6);
            config.setTtlSeconds(300);

            GenerateOtpRequest request = new GenerateOtpRequest();
            request.setOperationId("payment-file-fail-001");
            request.setDeliveryChannel(DeliveryChannel.FILE);
            request.setDeliveryTarget("otp-fail.txt");

            when(userRepository.findById(userId)).thenReturn(user);
            when(otpConfigRepository.getConfig()).thenReturn(config);
            when(otpCodeRepository.createOtpCodeReplacingActive(any(OtpCode.class))).thenReturn(555L);

            doThrow(new RuntimeException("File delivery failed"))
                    .when(fileDeliveryService)
                    .saveOtpToFile(
                            eq("otp-fail.txt"),
                            eq(userId),
                            eq("payment-file-fail-001"),
                            anyString(),
                            any(LocalDateTime.class)
                    );

            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> otpService.generateOtp(userId, request)
            );

            assertEquals("File delivery failed", exception.getMessage());

            verify(generateOtpRateLimitService).validateAndRegisterAttempt(userId);
            verify(otpCodeRepository).createOtpCodeReplacingActive(any(OtpCode.class));
            verify(fileDeliveryService).saveOtpToFile(
                    eq("otp-fail.txt"),
                    eq(userId),
                    eq("payment-file-fail-001"),
                    anyString(),
                    any(LocalDateTime.class)
            );
            verify(otpCodeRepository).deleteById(555L);

            verifyNoInteractions(emailDeliveryService, telegramDeliveryService, smsDeliveryService);
        }
    }

    @Nested
    class DeliveryRules {

        @Test
        void shouldRejectMissingUserEmail() {
            Long userId = 13L;

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

            verify(generateOtpRateLimitService, never()).validateAndRegisterAttempt(anyLong());
            verify(otpCodeRepository, never()).createOtpCodeReplacingActive(any(OtpCode.class));
            verifyNoInteractions(emailDeliveryService);
        }

        @Test
        void shouldRejectMissingUserPhone() {
            Long userId = 14L;

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

            verify(generateOtpRateLimitService, never()).validateAndRegisterAttempt(anyLong());
            verify(otpCodeRepository, never()).createOtpCodeReplacingActive(any(OtpCode.class));
            verifyNoInteractions(smsDeliveryService);
        }

        @Test
        void shouldRejectMissingTelegramChatId() {
            Long userId = 15L;

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

            verify(generateOtpRateLimitService, never()).validateAndRegisterAttempt(anyLong());
            verify(otpCodeRepository, never()).createOtpCodeReplacingActive(any(OtpCode.class));
            verifyNoInteractions(telegramDeliveryService);
        }

        @Test
        void shouldRejectMissingFileTarget() {
            Long userId = 16L;

            GenerateOtpRequest request = new GenerateOtpRequest();
            request.setOperationId("payment-file-002");
            request.setDeliveryChannel(DeliveryChannel.FILE);
            request.setDeliveryTarget("   ");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> otpService.generateOtp(userId, request)
            );

            assertEquals("Delivery target is required", exception.getMessage());

            verifyNoInteractions(
                    userRepository,
                    otpConfigRepository,
                    generateOtpRateLimitService,
                    otpCodeRepository,
                    fileDeliveryService,
                    emailDeliveryService,
                    telegramDeliveryService,
                    smsDeliveryService
            );
        }

        @Test
        void shouldRejectMissingOtpConfig() {
            Long userId = 17L;

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

            verify(generateOtpRateLimitService, never()).validateAndRegisterAttempt(anyLong());
            verify(otpCodeRepository, never()).createOtpCodeReplacingActive(any(OtpCode.class));
        }

        @Test
        void shouldRejectDeliveryTargetForEmail() {
            Long userId = 40L;

            GenerateOtpRequest request = new GenerateOtpRequest();
            request.setOperationId("payment-email-extra-target-001");
            request.setDeliveryChannel(DeliveryChannel.EMAIL);
            request.setDeliveryTarget("other@example.com");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> otpService.generateOtp(userId, request)
            );

            assertEquals("Delivery target must not be provided for EMAIL channel", exception.getMessage());

            verifyNoInteractions(
                    userRepository,
                    otpConfigRepository,
                    generateOtpRateLimitService,
                    otpCodeRepository,
                    fileDeliveryService,
                    emailDeliveryService,
                    telegramDeliveryService,
                    smsDeliveryService
            );
        }

        @Test
        void shouldRejectDeliveryTargetForSms() {
            Long userId = 41L;

            GenerateOtpRequest request = new GenerateOtpRequest();
            request.setOperationId("payment-sms-extra-target-001");
            request.setDeliveryChannel(DeliveryChannel.SMS);
            request.setDeliveryTarget("+37499000000");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> otpService.generateOtp(userId, request)
            );

            assertEquals("Delivery target must not be provided for SMS channel", exception.getMessage());

            verifyNoInteractions(
                    userRepository,
                    otpConfigRepository,
                    generateOtpRateLimitService,
                    otpCodeRepository,
                    fileDeliveryService,
                    emailDeliveryService,
                    telegramDeliveryService,
                    smsDeliveryService
            );
        }

        @Test
        void shouldRejectDeliveryTargetForTelegram() {
            Long userId = 42L;

            GenerateOtpRequest request = new GenerateOtpRequest();
            request.setOperationId("payment-telegram-extra-target-001");
            request.setDeliveryChannel(DeliveryChannel.TELEGRAM);
            request.setDeliveryTarget("987654321");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> otpService.generateOtp(userId, request)
            );

            assertEquals("Delivery target must not be provided for TELEGRAM channel", exception.getMessage());

            verifyNoInteractions(
                    userRepository,
                    otpConfigRepository,
                    generateOtpRateLimitService,
                    otpCodeRepository,
                    fileDeliveryService,
                    emailDeliveryService,
                    telegramDeliveryService,
                    smsDeliveryService
            );
        }
    }

    @Nested
    class ValidateOtp {

        @Test
        void shouldReturnUsed_whenCodeIsValid() {
            Long userId = 20L;

            ValidateOtpRequest request = new ValidateOtpRequest();
            request.setOperationId("payment-validate-001");
            request.setCode("123456");

            OtpCode otpCode = new OtpCode();
            otpCode.setId(200L);
            otpCode.setUserId(userId);
            otpCode.setOperationId("payment-validate-001");
            otpCode.setCode("123456");
            otpCode.setStatus(OtpStatus.USED);

            when(otpCodeRepository.consumeActiveCode(userId, "payment-validate-001", "123456"))
                    .thenReturn(otpCode);

            OtpValidationResponse response = otpService.validateOtp(userId, request);

            assertEquals("OTP validated successfully", response.message());
            assertEquals(200L, response.otpId());
            assertEquals("payment-validate-001", response.operationId());
            assertEquals(OtpStatus.USED, response.status());

            verify(otpCodeRepository).expireActiveCodes();
            verify(otpCodeRepository).consumeActiveCode(userId, "payment-validate-001", "123456");
        }

        @Test
        void shouldRejectInvalidOrExpiredCode() {
            Long userId = 21L;

            ValidateOtpRequest request = new ValidateOtpRequest();
            request.setOperationId("payment-validate-002");
            request.setCode("000000");

            when(otpCodeRepository.consumeActiveCode(userId, "payment-validate-002", "000000"))
                    .thenReturn(null);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> otpService.validateOtp(userId, request)
            );

            assertEquals("Invalid or expired OTP code", exception.getMessage());

            verify(otpCodeRepository).expireActiveCodes();
            verify(otpCodeRepository).consumeActiveCode(userId, "payment-validate-002", "000000");
        }

        @Test
        void shouldBlockAfterTooManyInvalidAttempts() {
            Long userId = 22L;

            ValidateOtpRequest request = new ValidateOtpRequest();
            request.setOperationId("payment-validate-003");
            request.setCode("654321");

            when(otpCodeRepository.consumeActiveCode(userId, "payment-validate-003", "654321"))
                    .thenReturn(null);

            for (int i = 0; i < 5; i++) {
                IllegalArgumentException exception = assertThrows(
                        IllegalArgumentException.class,
                        () -> otpService.validateOtp(userId, request)
                );

                assertEquals("Invalid or expired OTP code", exception.getMessage());
            }

            IllegalArgumentException blockedException = assertThrows(
                    IllegalArgumentException.class,
                    () -> otpService.validateOtp(userId, request)
            );

            assertEquals("Too many invalid OTP attempts. Try again later.", blockedException.getMessage());

            verify(otpCodeRepository, times(5)).consumeActiveCode(userId, "payment-validate-003", "654321");
        }

        @Test
        void shouldClearFailedAttempts_afterSuccessfulValidation() {
            Long userId = 23L;

            ValidateOtpRequest wrongRequest = new ValidateOtpRequest();
            wrongRequest.setOperationId("payment-validate-004");
            wrongRequest.setCode("000000");

            ValidateOtpRequest correctRequest = new ValidateOtpRequest();
            correctRequest.setOperationId("payment-validate-004");
            correctRequest.setCode("123456");

            OtpCode usedOtp = new OtpCode();
            usedOtp.setId(400L);
            usedOtp.setUserId(userId);
            usedOtp.setOperationId("payment-validate-004");
            usedOtp.setCode("123456");
            usedOtp.setStatus(OtpStatus.USED);

            when(otpCodeRepository.consumeActiveCode(userId, "payment-validate-004", "000000"))
                    .thenReturn(null);
            when(otpCodeRepository.consumeActiveCode(userId, "payment-validate-004", "123456"))
                    .thenReturn(usedOtp);

            IllegalArgumentException firstException = assertThrows(
                    IllegalArgumentException.class,
                    () -> otpService.validateOtp(userId, wrongRequest)
            );
            assertEquals("Invalid or expired OTP code", firstException.getMessage());

            OtpValidationResponse response = otpService.validateOtp(userId, correctRequest);
            assertEquals("OTP validated successfully", response.message());

            IllegalArgumentException secondException = assertThrows(
                    IllegalArgumentException.class,
                    () -> otpService.validateOtp(userId, wrongRequest)
            );
            assertEquals("Invalid or expired OTP code", secondException.getMessage());

            verify(otpCodeRepository, times(2)).consumeActiveCode(userId, "payment-validate-004", "000000");
            verify(otpCodeRepository).consumeActiveCode(userId, "payment-validate-004", "123456");
        }
    }
}