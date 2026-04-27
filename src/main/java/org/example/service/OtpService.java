package org.example.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.example.dto.GenerateOtpRequest;
import org.example.dto.OtpGenerationResponse;
import org.example.dto.OtpValidationResponse;
import org.example.dto.ValidateOtpRequest;
import org.example.exception.NotFoundException;
import org.example.model.DeliveryChannel;
import org.example.model.OtpCode;
import org.example.model.OtpConfig;
import org.example.model.OtpStatus;
import org.example.model.User;
import org.example.repository.OtpCodeRepository;
import org.example.repository.OtpConfigRepository;
import org.example.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OtpService {

  private static final Logger log = LoggerFactory.getLogger(OtpService.class);

  private static final int MAX_INVALID_VALIDATION_ATTEMPTS = 5;
  private static final long VALIDATION_BLOCK_MINUTES = 5;
  private static final long VALIDATION_ATTEMPT_RETENTION_MINUTES = 30;

  private static final int OPERATION_ID_MAX_LENGTH = 100;
  private static final int DELIVERY_TARGET_MAX_LENGTH = 255;
  private static final int OTP_CODE_MAX_LENGTH = 10;

  private final OtpConfigRepository otpConfigRepository;
  private final OtpCodeRepository otpCodeRepository;
  private final FileDeliveryService fileDeliveryService;
  private final EmailDeliveryService emailDeliveryService;
  private final UserRepository userRepository;
  private final TelegramDeliveryService telegramDeliveryService;
  private final SmsDeliveryService smsDeliveryService;
  private final GenerateOtpRateLimitService generateOtpRateLimitService;
  private final SecureRandom secureRandom = new SecureRandom();

  private final Map<String, ValidationAttemptState> failedValidationAttempts =
      new ConcurrentHashMap<>();

  public OtpService(
      OtpConfigRepository otpConfigRepository,
      OtpCodeRepository otpCodeRepository,
      FileDeliveryService fileDeliveryService,
      EmailDeliveryService emailDeliveryService,
      TelegramDeliveryService telegramDeliveryService,
      SmsDeliveryService smsDeliveryService,
      UserRepository userRepository,
      GenerateOtpRateLimitService generateOtpRateLimitService) {
    this.otpConfigRepository = otpConfigRepository;
    this.otpCodeRepository = otpCodeRepository;
    this.fileDeliveryService = fileDeliveryService;
    this.emailDeliveryService = emailDeliveryService;
    this.telegramDeliveryService = telegramDeliveryService;
    this.smsDeliveryService = smsDeliveryService;
    this.userRepository = userRepository;
    this.generateOtpRateLimitService = generateOtpRateLimitService;
  }

  // Generates exactly one актуальный OTP for the user and operation.
  // For the same userId + operationId the previous ACTIVE code is expired before a new one is
  // created.

  public OtpGenerationResponse generateOtp(Long userId, GenerateOtpRequest request) {
    validateGenerateRequest(userId, request);

    String operationId = request.getOperationId().trim();
    DeliveryChannel channel = request.getDeliveryChannel();

    User user = requireUser(userId);
    OtpConfig config = requireOtpConfig(userId);

    ensureSupportedChannel(userId, operationId, channel);

    LocalDateTime now = LocalDateTime.now();
    LocalDateTime expiresAt = now.plusSeconds(config.getTtlSeconds());
    String code = generateNumericCode(config.getCodeLength());
    String deliveryTarget = resolveDeliveryTarget(userId, user, request);

    generateOtpRateLimitService.validateAndRegisterAttempt(userId);

    OtpCode otpCode =
        buildOtpCode(userId, operationId, channel, deliveryTarget, code, now, expiresAt);
    Long otpId = otpCodeRepository.createOtpCodeReplacingActive(otpCode);

    try {
      deliverOtp(userId, otpCode, code, expiresAt);
    } catch (RuntimeException e) {
      cleanupFailedDeliveryOtp(otpId, userId, otpCode, e);
      throw e;
    }

    log.info(
        "OTP generated successfully: otpId={}, userId={}, operationId={}, channel={}, expiresAt={}",
        otpId,
        userId,
        otpCode.getOperationId(),
        otpCode.getDeliveryChannel(),
        expiresAt);

    return new OtpGenerationResponse(
        "OTP generated successfully",
        otpId,
        otpCode.getOperationId(),
        otpCode.getStatus(),
        otpCode.getDeliveryChannel(),
        otpCode.getDeliveryTarget(),
        otpCode.getExpiresAt());
  }

  // OTP validation uses an atomic repository operation, so one code cannot be successfully reused
  // in concurrent requests.
  public OtpValidationResponse validateOtp(Long userId, ValidateOtpRequest request) {
    validateValidateRequest(userId, request);

    String operationId = request.getOperationId().trim();
    String code = request.getCode().trim();

    otpCodeRepository.expireActiveCodes();
    ensureValidationNotBlocked(userId, operationId);

    OtpCode otpCode = otpCodeRepository.consumeActiveCode(userId, operationId, code);

    if (otpCode == null) {
      registerFailedValidationAttempt(userId, operationId);

      log.warn(
          "OTP validation failed: invalid or expired code, userId={}, operationId={}",
          userId,
          operationId);
      throw new IllegalArgumentException("Invalid or expired OTP code");
    }

    clearFailedValidationAttempts(userId, operationId);

    log.info(
        "OTP validated successfully: otpId={}, userId={}, operationId={}",
        otpCode.getId(),
        userId,
        otpCode.getOperationId());

    return new OtpValidationResponse(
        "OTP validated successfully", otpCode.getId(), otpCode.getOperationId(), OtpStatus.USED);
  }

  private void validateGenerateRequest(Long userId, GenerateOtpRequest request) {
    if (request == null) {
      log.warn("OTP generation failed: request body is missing, userId={}", userId);
      throw new IllegalArgumentException("Request body is required");
    }

    if (request.getOperationId() == null || request.getOperationId().isBlank()) {
      log.warn("OTP generation failed: operationId is missing, userId={}", userId);
      throw new IllegalArgumentException("Operation ID is required");
    }

    validateMaxLength(
        request.getOperationId(), OPERATION_ID_MAX_LENGTH, "Operation ID is too long");

    if (request.getDeliveryTarget() != null) {
      validateMaxLength(
          request.getDeliveryTarget(), DELIVERY_TARGET_MAX_LENGTH, "Delivery target is too long");
    }

    if (request.getDeliveryChannel() == null) {
      log.warn(
          "OTP generation failed: delivery channel is missing, userId={}, operationId={}",
          userId,
          request.getOperationId());
      throw new IllegalArgumentException("Delivery channel is required");
    }

    validateDeliveryTargetRules(userId, request);
  }

  private void validateValidateRequest(Long userId, ValidateOtpRequest request) {
    if (request == null) {
      log.warn("OTP validation failed: request body is missing, userId={}", userId);
      throw new IllegalArgumentException("Request body is required");
    }

    if (request.getOperationId() == null || request.getOperationId().isBlank()) {
      log.warn("OTP validation failed: operationId is missing, userId={}", userId);
      throw new IllegalArgumentException("Operation ID is required");
    }

    validateMaxLength(
        request.getOperationId(), OPERATION_ID_MAX_LENGTH, "Operation ID is too long");

    if (request.getCode() == null || request.getCode().isBlank()) {
      log.warn(
          "OTP validation failed: OTP code is missing, userId={}, operationId={}",
          userId,
          request.getOperationId());
      throw new IllegalArgumentException("OTP code is required");
    }

    validateMaxLength(request.getCode(), OTP_CODE_MAX_LENGTH, "OTP code is too long");
  }

  private User requireUser(Long userId) {
    User user = userRepository.findById(userId);
    if (user == null) {
      log.warn("OTP generation failed: user not found, userId={}", userId);
      throw new IllegalArgumentException("User not found");
    }
    return user;
  }

  private OtpConfig requireOtpConfig(Long userId) {
    OtpConfig config = otpConfigRepository.getConfig();
    if (config == null) {
      log.error("OTP generation failed: OTP config not found, userId={}", userId);
      throw new NotFoundException("OTP config not found");
    }
    return config;
  }

  private void ensureSupportedChannel(Long userId, String operationId, DeliveryChannel channel) {
    if (channel != DeliveryChannel.FILE
        && channel != DeliveryChannel.EMAIL
        && channel != DeliveryChannel.TELEGRAM
        && channel != DeliveryChannel.SMS) {
      log.warn(
          "OTP generation failed: unsupported delivery channel, userId={}, operationId={}, channel={}",
          userId,
          operationId,
          channel);
      throw new IllegalArgumentException("Delivery channel is not supported yet");
    }
  }

  private OtpCode buildOtpCode(
      Long userId,
      String operationId,
      DeliveryChannel deliveryChannel,
      String deliveryTarget,
      String code,
      LocalDateTime sentAt,
      LocalDateTime expiresAt) {
    OtpCode otpCode = new OtpCode();
    otpCode.setUserId(userId);
    otpCode.setOperationId(operationId);
    otpCode.setCode(code);
    otpCode.setStatus(OtpStatus.ACTIVE);
    otpCode.setDeliveryChannel(deliveryChannel);
    otpCode.setDeliveryTarget(deliveryTarget);
    otpCode.setExpiresAt(expiresAt);
    otpCode.setSentAt(sentAt);
    return otpCode;
  }

  // If delivery fails after OTP creation, remove the just-created record
  // so the database does not keep an ACTIVE code that was never actually delivered.
  private void cleanupFailedDeliveryOtp(
      Long otpId, Long userId, OtpCode otpCode, RuntimeException originalError) {
    log.error(
        "OTP delivery failed: otpId={}, userId={}, operationId={}, channel={}, error={}",
        otpId,
        userId,
        otpCode.getOperationId(),
        otpCode.getDeliveryChannel(),
        originalError.getMessage());

    try {
      boolean deleted = otpCodeRepository.deleteById(otpId);

      if (deleted) {
        log.info(
            "OTP removed after failed delivery: otpId={}, userId={}, operationId={}, channel={}",
            otpId,
            userId,
            otpCode.getOperationId(),
            otpCode.getDeliveryChannel());
      } else {
        log.warn(
            "Failed to remove OTP after delivery error: otpId={}, userId={}, operationId={}, channel={}",
            otpId,
            userId,
            otpCode.getOperationId(),
            otpCode.getDeliveryChannel());
      }
    } catch (RuntimeException cleanupError) {
      log.error(
          "OTP cleanup failed after delivery error: otpId={}, userId={}, operationId={}, channel={}",
          otpId,
          userId,
          otpCode.getOperationId(),
          otpCode.getDeliveryChannel(),
          cleanupError);
    }
  }

  private String generateNumericCode(int length) {
    StringBuilder code = new StringBuilder(length);

    for (int i = 0; i < length; i++) {
      code.append(secureRandom.nextInt(10));
    }

    return code.toString();
  }

  private void deliverOtp(Long userId, OtpCode otpCode, String code, LocalDateTime expiresAt) {
    switch (otpCode.getDeliveryChannel()) {
      case FILE ->
          fileDeliveryService.saveOtpToFile(
              otpCode.getDeliveryTarget(), userId, otpCode.getOperationId(), code, expiresAt);
      case EMAIL ->
          emailDeliveryService.sendOtpEmail(
              otpCode.getDeliveryTarget(), userId, otpCode.getOperationId(), code, expiresAt);
      case TELEGRAM ->
          telegramDeliveryService.sendOtpMessage(
              otpCode.getDeliveryTarget(), userId, otpCode.getOperationId(), code, expiresAt);
      case SMS ->
          smsDeliveryService.sendOtpSms(
              otpCode.getDeliveryTarget(), userId, otpCode.getOperationId(), code, expiresAt);
    }
  }

  private String resolveDeliveryTarget(Long userId, User user, GenerateOtpRequest request) {
    return switch (request.getDeliveryChannel()) {
      case FILE -> request.getDeliveryTarget().trim();
      case EMAIL -> {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
          log.warn(
              "OTP generation failed: user email is not set, userId={}, login={}",
              userId,
              user.getLogin());
          throw new IllegalArgumentException("User email is not set");
        }
        yield user.getEmail().trim();
      }
      case TELEGRAM -> {
        if (user.getTelegramChatId() == null || user.getTelegramChatId().isBlank()) {
          log.warn(
              "OTP generation failed: telegram chat id is not set, userId={}, login={}",
              userId,
              user.getLogin());
          throw new IllegalArgumentException("User telegram chat id is not set");
        }
        yield user.getTelegramChatId().trim();
      }
      case SMS -> {
        if (user.getPhone() == null || user.getPhone().isBlank()) {
          log.warn(
              "OTP generation failed: user phone is not set, userId={}, login={}",
              userId,
              user.getLogin());
          throw new IllegalArgumentException("User phone is not set");
        }
        yield user.getPhone().trim();
      }
    };
  }

  @Scheduled(fixedDelayString = "${otp.validation-attempt-cleanup.fixed-delay-ms:600000}")
  public synchronized void cleanupStaleValidationAttempts() {
    LocalDateTime now = LocalDateTime.now();
    failedValidationAttempts.entrySet().removeIf(entry -> entry.getValue().shouldRemove(now));
  }

  private synchronized void ensureValidationNotBlocked(Long userId, String operationId) {
    String key = validationAttemptKey(userId, operationId);
    ValidationAttemptState state = failedValidationAttempts.get(key);

    if (state == null) {
      return;
    }

    LocalDateTime now = LocalDateTime.now();

    if (state.shouldRemove(now)) {
      failedValidationAttempts.remove(key);
      return;
    }

    if (state.blockedUntil != null && state.blockedUntil.isAfter(now)) {
      log.warn(
          "OTP validation blocked: userId={}, operationId={}, blockedUntil={}",
          userId,
          operationId,
          state.blockedUntil);
      throw new IllegalArgumentException("Too many invalid OTP attempts. Try again later.");
    }
  }

  private synchronized void registerFailedValidationAttempt(Long userId, String operationId) {
    String key = validationAttemptKey(userId, operationId);
    ValidationAttemptState state =
        failedValidationAttempts.computeIfAbsent(key, ignored -> new ValidationAttemptState());

    LocalDateTime now = LocalDateTime.now();

    if (state.shouldRemove(now)) {
      state.failedAttempts = 0;
      state.blockedUntil = null;
    }

    state.lastAttemptAt = now;

    if (state.blockedUntil != null && state.blockedUntil.isAfter(now)) {
      return;
    }

    state.failedAttempts++;

    if (state.failedAttempts >= MAX_INVALID_VALIDATION_ATTEMPTS) {
      state.blockedUntil = now.plusMinutes(VALIDATION_BLOCK_MINUTES);
      log.warn(
          "OTP validation temporarily blocked: userId={}, operationId={}, failedAttempts={}, blockedUntil={}",
          userId,
          operationId,
          state.failedAttempts,
          state.blockedUntil);
    }
  }

  private synchronized void clearFailedValidationAttempts(Long userId, String operationId) {
    failedValidationAttempts.remove(validationAttemptKey(userId, operationId));
  }

  private void validateMaxLength(String value, int maxLength, String message) {
    if (value != null && value.length() > maxLength) {
      throw new IllegalArgumentException(message);
    }
  }

  // deliveryTarget is allowed only for FILE.
  // For EMAIL, SMS and TELEGRAM the destination is taken from the user's profile in the database.
  private void validateDeliveryTargetRules(Long userId, GenerateOtpRequest request) {
    DeliveryChannel channel = request.getDeliveryChannel();
    String deliveryTarget = request.getDeliveryTarget();

    if (channel == DeliveryChannel.FILE) {
      if (deliveryTarget == null || deliveryTarget.isBlank()) {
        log.warn(
            "OTP generation failed: file target is missing, userId={}, operationId={}",
            userId,
            request.getOperationId());
        throw new IllegalArgumentException("Delivery target is required");
      }
      return;
    }

    if (deliveryTarget != null) {
      log.warn(
          "OTP generation failed: delivery target must not be provided, userId={}, operationId={}, channel={}",
          userId,
          request.getOperationId(),
          channel);
      throw new IllegalArgumentException(
          "Delivery target must not be provided for " + channel.name() + " channel");
    }
  }

  private String validationAttemptKey(Long userId, String operationId) {
    return userId + ":" + operationId;
  }

  private static final class ValidationAttemptState {
    private int failedAttempts;
    private LocalDateTime blockedUntil;
    private LocalDateTime lastAttemptAt;

    private boolean shouldRemove(LocalDateTime now) {
      if (lastAttemptAt == null) {
        return true;
      }

      return !lastAttemptAt.plusMinutes(VALIDATION_ATTEMPT_RETENTION_MINUTES).isAfter(now);
    }
  }
}
