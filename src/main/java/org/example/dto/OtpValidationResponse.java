package org.example.dto;

import org.example.model.OtpStatus;

public record OtpValidationResponse(
        String message,
        Long otpId,
        String operationId,
        OtpStatus status
) {
}