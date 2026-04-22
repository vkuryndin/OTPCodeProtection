package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.dto.GenerateOtpRequest;
import org.example.dto.OtpGenerationResponse;
import org.example.dto.OtpValidationResponse;
import org.example.dto.ValidateOtpRequest;
import org.example.security.RequestAuthService;
import org.example.service.OtpService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/otp")
public class OtpController {

    private final OtpService otpService;
    private final RequestAuthService requestAuthService;

    public OtpController(OtpService otpService, RequestAuthService requestAuthService) {
        this.otpService = otpService;
        this.requestAuthService = requestAuthService;
    }

    @PostMapping("/generate")
    public ResponseEntity<OtpGenerationResponse> generateOtp(@RequestBody GenerateOtpRequest request,
                                                             HttpServletRequest httpRequest) {
        Long userId = requestAuthService.extractUserId(httpRequest);
        OtpGenerationResponse response = otpService.generateOtp(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/validate")
    public ResponseEntity<OtpValidationResponse> validateOtp(@RequestBody ValidateOtpRequest request,
                                                             HttpServletRequest httpRequest) {
        Long userId = requestAuthService.extractUserId(httpRequest);
        OtpValidationResponse response = otpService.validateOtp(userId, request);
        return ResponseEntity.ok(response);
    }
}