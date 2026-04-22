package org.example.dto;

import org.example.model.DeliveryChannel;
import org.example.model.OtpStatus;

import java.time.LocalDateTime;

public record OtpGenerationResponse(
        String message,
        Long otpId,
        String operationId,
        OtpStatus status,
        DeliveryChannel deliveryChannel,
        String deliveryTarget,
        LocalDateTime expiresAt
) {
}