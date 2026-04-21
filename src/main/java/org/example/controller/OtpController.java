package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.dto.GenerateOtpRequest;
import org.example.service.OtpService;
import org.example.service.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/otp")
public class OtpController {

    private final OtpService otpService;
    private final TokenService tokenService;

    public OtpController(OtpService otpService, TokenService tokenService) {
        this.otpService = otpService;
        this.tokenService = tokenService;
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateOtp(@RequestBody GenerateOtpRequest request,
                                                           HttpServletRequest httpRequest) {
        String token = extractToken(httpRequest);
        Long userId = tokenService.extractUserId(token);

        Map<String, Object> response = otpService.generateOtp(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || authHeader.isBlank()) {
            throw new IllegalArgumentException("Authorization header is required");
        }

        if (!authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Invalid authorization format");
        }

        String token = authHeader.substring("Bearer ".length()).trim();

        if (token.isBlank()) {
            throw new IllegalArgumentException("Token is required");
        }

        return token;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", e.getMessage());

        String message = e.getMessage();
        if ("Authorization header is required".equals(message)
                || "Invalid authorization format".equals(message)
                || "Token is required".equals(message)
                || "Invalid or expired token".equals(message)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        if ("OTP config not found".equals(message)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        return ResponseEntity.badRequest().body(response);
    }
}