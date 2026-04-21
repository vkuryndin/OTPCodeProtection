package org.example.service;

import org.example.dto.GenerateOtpRequest;
import org.example.model.OtpCode;
import org.example.model.OtpConfig;
import org.example.model.OtpStatus;
import org.example.repository.OtpCodeRepository;
import org.example.repository.OtpConfigRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.example.model.DeliveryChannel;

@Service
public class OtpService {

    private final OtpConfigRepository otpConfigRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final FileDeliveryService fileDeliveryService;

    public OtpService(OtpConfigRepository otpConfigRepository,
                      OtpCodeRepository otpCodeRepository, FileDeliveryService fileDeliveryService) {
        this.otpConfigRepository = otpConfigRepository;
        this.otpCodeRepository = otpCodeRepository;
        this.fileDeliveryService = fileDeliveryService;
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

        if (isBlank(request.getDeliveryTarget())) {
            throw new IllegalArgumentException("Delivery target is required");
        }

        OtpConfig config = otpConfigRepository.getConfig();
        if (config == null) {
            throw new IllegalArgumentException("OTP config not found");
        }
        if (request.getDeliveryChannel() != DeliveryChannel.FILE) {
            throw new IllegalArgumentException("Delivery channel is not supported yet");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(config.getTtlSeconds());
        String code = generateNumericCode(config.getCodeLength());

        OtpCode otpCode = new OtpCode();
        otpCode.setUserId(userId);
        otpCode.setOperationId(request.getOperationId().trim());
        otpCode.setCode(code);
        otpCode.setStatus(OtpStatus.ACTIVE);
        otpCode.setDeliveryChannel(request.getDeliveryChannel());
        otpCode.setDeliveryTarget(request.getDeliveryTarget().trim());
        otpCode.setExpiresAt(now.plusSeconds(config.getTtlSeconds()));
        otpCode.setSentAt(now);

        Long otpId = otpCodeRepository.createOtpCode(otpCode);
        fileDeliveryService.saveOtpToFile(
                otpCode.getDeliveryTarget(),
                userId,
                otpCode.getOperationId(),
                code,
                expiresAt
        );
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