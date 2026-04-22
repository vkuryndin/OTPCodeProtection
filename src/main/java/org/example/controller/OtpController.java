package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.dto.GenerateOtpRequest;
import org.example.dto.OtpGenerationResponse;
import org.example.dto.OtpValidationResponse;
import org.example.dto.ValidateOtpRequest;
import org.example.security.AuthUtil;
import org.example.service.OtpService;
import org.example.service.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/otp")
public class OtpController {

    private final OtpService otpService;
    private final TokenService tokenService;
    private final AuthUtil authUtil;

    public OtpController(OtpService otpService, TokenService tokenService, AuthUtil authUtil) {
        this.otpService = otpService;
        this.tokenService = tokenService;
        this.authUtil = authUtil;
    }

    @PostMapping("/generate")
    public ResponseEntity<OtpGenerationResponse> generateOtp(@RequestBody GenerateOtpRequest request,
                                                             HttpServletRequest httpRequest) {
        Long userId = extractUserId(httpRequest);
        OtpGenerationResponse response = otpService.generateOtp(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/validate")
    public ResponseEntity<OtpValidationResponse> validateOtp(@RequestBody ValidateOtpRequest request,
                                                             HttpServletRequest httpRequest) {
        Long userId = extractUserId(httpRequest);
        OtpValidationResponse response = otpService.validateOtp(userId, request);
        return ResponseEntity.ok(response);
    }

    private Long extractUserId(HttpServletRequest request) {
        String token = authUtil.extractToken(request);
        return tokenService.extractUserId(token);
    }
}