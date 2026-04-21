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

import org.example.model.DeliveryChannel;


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
            throw new IllegalArgumentException("Request body is required");
        }

        if (isBlank(request.getOperationId())) {
            throw new IllegalArgumentException("Operation ID is required");
        }

        if (request.getDeliveryChannel() == null) {
            throw new IllegalArgumentException("Delivery channel is required");
        }

        User user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        OtpConfig config = otpConfigRepository.getConfig();
        if (config == null) {
            throw new IllegalArgumentException("OTP config not found");
        }

        if (request.getDeliveryChannel() != DeliveryChannel.FILE
                && request.getDeliveryChannel() != DeliveryChannel.EMAIL
                && request.getDeliveryChannel() != DeliveryChannel.TELEGRAM
                && request.getDeliveryChannel() != DeliveryChannel.SMS) {
            throw new IllegalArgumentException("Delivery channel is not supported yet");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(config.getTtlSeconds());
        String code = generateNumericCode(config.getCodeLength());

        String actualTarget;

        if (request.getDeliveryChannel() == DeliveryChannel.FILE) {
            if (isBlank(request.getDeliveryTarget())) {
                throw new IllegalArgumentException("Delivery target is required");
            }
            actualTarget = request.getDeliveryTarget().trim();
        } else if (request.getDeliveryChannel() == DeliveryChannel.EMAIL) {
            if (isBlank(user.getEmail())) {
                throw new IllegalArgumentException("User email is not set");
            }
            actualTarget = user.getEmail().trim();
        } else if (request.getDeliveryChannel() == DeliveryChannel.TELEGRAM) {
            if (isBlank(user.getTelegramChatId())) {
                throw new IllegalArgumentException("User telegram chat id is not set");
            }
            actualTarget = user.getTelegramChatId().trim();
        } else {
            if (isBlank(user.getPhone())) {
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
            throw new IllegalArgumentException("Request body is required");
        }

        if (isBlank(request.getOperationId())) {
            throw new IllegalArgumentException("Operation ID is required");
        }

        if (isBlank(request.getCode())) {
            throw new IllegalArgumentException("OTP code is required");
        }

        otpCodeRepository.expireActiveCodes();

        OtpCode otpCode = otpCodeRepository.findActiveCode(
                userId,
                request.getOperationId().trim(),
                request.getCode().trim()
        );

        if (otpCode == null) {
            throw new IllegalArgumentException("Invalid or expired OTP code");
        }

        boolean updated = otpCodeRepository.markAsUsed(otpCode.getId());
        if (!updated) {
            throw new IllegalArgumentException("Invalid or expired OTP code");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "OTP validated successfully");
        response.put("otpId", otpCode.getId());
        response.put("operationId", otpCode.getOperationId());
        response.put("status", OtpStatus.USED);

        return response;
    }

}