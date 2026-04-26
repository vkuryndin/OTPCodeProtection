package org.example.dto;

import java.time.LocalDateTime;
import org.example.model.DeliveryChannel;
import org.example.model.OtpStatus;

public record OtpGenerationResponse(
    String message,
    Long otpId,
    String operationId,
    OtpStatus status,
    DeliveryChannel deliveryChannel,
    String deliveryTarget,
    LocalDateTime expiresAt) {}
