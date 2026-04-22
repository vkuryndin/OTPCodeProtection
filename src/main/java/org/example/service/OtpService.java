package org.example.service;

import org.example.dto.GenerateOtpRequest;
import org.example.model.OtpCode;
import org.example.model.OtpConfig;
import org.example.model.OtpStatus;
import org.example.repository.OtpCodeRepository;
import org.example.repository.OtpConfigRepository;
import org.example.repository.UserRepository;
import org.example.model.User;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.example.model.DeliveryChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class OtpService {

    private final OtpConfigRepository otpConfigRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final FileDeliveryService fileDeliveryService;
    private final EmailDeliveryService emailDeliveryService;
    private final UserRepository userRepository;
    private final TelegramDeliveryService telegramDeliveryService;
    private final SmsDeliveryService smsDeliveryService;
    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    public OtpService(OtpConfigRepository otpConfigRepository,
                      OtpCodeRepository otpCodeRepository,
                      FileDeliveryService fileDeliveryService,
                      EmailDeliveryService emailDeliveryService,
                      TelegramDeliveryService telegramDeliveryService,
                      SmsDeliveryService smsDeliveryService,
                      UserRepository userRepository) {
        this.otpConfigRepository = otpConfigRepository;
        this.otpCodeRepository = otpCodeRepository;
        this.fileDeliveryService = fileDeliveryService;
        this.emailDeliveryService = emailDeliveryService;
        this.telegramDeliveryService = telegramDeliveryService;
        this.smsDeliveryService = smsDeliveryService;
        this.userRepository = userRepository;
    }

    public Map<String, Object> generateOtp(Long userId, GenerateOtpRequest request) {
        if (request == null) {
            log.warn("OTP generation failed: request body is missing, userId={}", userId);
            throw new IllegalArgumentException("Request body is required");
        }

        if (isBlank(request.getOperationId())) {
            log.warn("OTP generation failed: operationId is missing, userId={}", userId);
            throw new IllegalArgumentException("Operation ID is required");
        }

        if (request.getDeliveryChannel() == null) {
            log.warn("OTP generation failed: delivery channel is missing, userId={}, operationId={}",
                    userId, request.getOperationId());
            throw new IllegalArgumentException("Delivery channel is required");
        }

        User user = userRepository.findById(userId);
        if (user == null) {
            log.warn("OTP generation failed: user not found, userId={}", userId);
            throw new IllegalArgumentException("User not found");
        }

        OtpConfig config = otpConfigRepository.getConfig();
        if (config == null) {
            log.error("OTP generation failed: OTP config not found, userId={}", userId);
            throw new IllegalArgumentException("OTP config not found");
        }

        if (request.getDeliveryChannel() != DeliveryChannel.FILE
                && request.getDeliveryChannel() != DeliveryChannel.EMAIL
                && request.getDeliveryChannel() != DeliveryChannel.TELEGRAM
                && request.getDeliveryChannel() != DeliveryChannel.SMS) {
            log.warn("OTP generation failed: unsupported delivery channel, userId={}, operationId={}, channel={}",
                    userId, request.getOperationId(), request.getDeliveryChannel());
            throw new IllegalArgumentException("Delivery channel is not supported yet");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(config.getTtlSeconds());
        String code = generateNumericCode(config.getCodeLength());

        String actualTarget;

        if (request.getDeliveryChannel() == DeliveryChannel.FILE) {
            if (isBlank(request.getDeliveryTarget())) {
                log.warn("OTP generation failed: file target is missing, userId={}, operationId={}",
                        userId, request.getOperationId());
                throw new IllegalArgumentException("Delivery target is required");
            }
            actualTarget = request.getDeliveryTarget().trim();
        } else if (request.getDeliveryChannel() == DeliveryChannel.EMAIL) {
            if (isBlank(user.getEmail())) {
                log.warn("OTP generation failed: user email is not set, userId={}, login={}",
                        userId, user.getLogin());
                throw new IllegalArgumentException("User email is not set");
            }
            actualTarget = user.getEmail().trim();
        } else if (request.getDeliveryChannel() == DeliveryChannel.TELEGRAM) {
            if (isBlank(user.getTelegramChatId())) {
                log.warn("OTP generation failed: telegram chat id is not set, userId={}, login={}",
                        userId, user.getLogin());
                throw new IllegalArgumentException("User telegram chat id is not set");
            }
            actualTarget = user.getTelegramChatId().trim();
        } else {
            if (isBlank(user.getPhone())) {
                log.warn("OTP generation failed: user phone is not set, userId={}, login={}",
                        userId, user.getLogin());
                throw new IllegalArgumentException("User phone is not set");
            }
            actualTarget = user.getPhone().trim();
        }

        OtpCode otpCode = new OtpCode();
        otpCode.setUserId(userId);
        otpCode.setOperationId(request.getOperationId().trim());
        otpCode.setCode(code);
        otpCode.setStatus(OtpStatus.ACTIVE);
        otpCode.setDeliveryChannel(request.getDeliveryChannel());
        otpCode.setDeliveryTarget(actualTarget);
        otpCode.setExpiresAt(expiresAt);
        otpCode.setSentAt(now);

        Long otpId = otpCodeRepository.createOtpCode(otpCode);

        try {
            if (request.getDeliveryChannel() == DeliveryChannel.FILE) {
                fileDeliveryService.saveOtpToFile(
                        actualTarget,
                        userId,
                        otpCode.getOperationId(),
                        code,
                        expiresAt
                );
            } else if (request.getDeliveryChannel() == DeliveryChannel.EMAIL) {
                emailDeliveryService.sendOtpEmail(
                        actualTarget,
                        userId,
                        otpCode.getOperationId(),
                        code,
                        expiresAt
                );
            } else if (request.getDeliveryChannel() == DeliveryChannel.TELEGRAM) {
                telegramDeliveryService.sendOtpMessage(
                        actualTarget,
                        userId,
                        otpCode.getOperationId(),
                        code,
                        expiresAt
                );
            } else if (request.getDeliveryChannel() == DeliveryChannel.SMS) {
                smsDeliveryService.sendOtpSms(
                        actualTarget,
                        userId,
                        otpCode.getOperationId(),
                        code,
                        expiresAt
                );
            }
        } catch (RuntimeException e) {
            log.error("OTP delivery failed: otpId={}, userId={}, operationId={}, channel={}, error={}",
                    otpId, userId, otpCode.getOperationId(), otpCode.getDeliveryChannel(), e.getMessage());
            throw e;
        }

        log.info("OTP generated successfully: otpId={}, userId={}, operationId={}, channel={}, expiresAt={}",
                otpId, userId, otpCode.getOperationId(), otpCode.getDeliveryChannel(), expiresAt);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "OTP generated successfully");
        response.put("otpId", otpId);
        response.put("operationId", otpCode.getOperationId());
        response.put("status", otpCode.getStatus());
        response.put("deliveryChannel", otpCode.getDeliveryChannel());
        response.put("deliveryTarget", otpCode.getDeliveryTarget());
        response.put("expiresAt", otpCode.getExpiresAt());

        return response;
    }
    private String generateNumericCode(int length) {
        StringBuilder code = new StringBuilder();

        for (int i = 0; i < length; i++) {
            code.append(secureRandom.nextInt(10));
        }

        return code.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public Map<String, Object> validateOtp(Long userId, org.example.dto.ValidateOtpRequest request) {
        if (request == null) {
            log.warn("OTP validation failed: request body is missing, userId={}", userId);
            throw new IllegalArgumentException("Request body is required");
        }

        if (isBlank(request.getOperationId())) {
            log.warn("OTP validation failed: operationId is missing, userId={}", userId);
            throw new IllegalArgumentException("Operation ID is required");
        }

        if (isBlank(request.getCode())) {
            log.warn("OTP validation failed: OTP code is missing, userId={}, operationId={}",
                    userId, request.getOperationId());
            throw new IllegalArgumentException("OTP code is required");
        }

        otpCodeRepository.expireActiveCodes();

        OtpCode otpCode = otpCodeRepository.findActiveCode(
                userId,
                request.getOperationId().trim(),
                request.getCode().trim()
        );

        if (otpCode == null) {
            log.warn("OTP validation failed: invalid or expired code, userId={}, operationId={}",
                    userId, request.getOperationId());
            throw new IllegalArgumentException("Invalid or expired OTP code");
        }

        boolean updated = otpCodeRepository.markAsUsed(otpCode.getId());
        if (!updated) {
            log.warn("OTP validation failed: OTP already used or expired during update, otpId={}, userId={}",
                    otpCode.getId(), userId);
            throw new IllegalArgumentException("Invalid or expired OTP code");
        }

        log.info("OTP validated successfully: otpId={}, userId={}, operationId={}",
                otpCode.getId(), userId, otpCode.getOperationId());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "OTP validated successfully");
        response.put("otpId", otpCode.getId());
        response.put("operationId", otpCode.getOperationId());
        response.put("status", OtpStatus.USED);

        return response;
    }
}