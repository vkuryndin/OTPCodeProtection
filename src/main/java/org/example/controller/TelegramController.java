package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.service.TelegramBindingService;
import org.example.service.TokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/telegram")
public class TelegramController {

    private final TelegramBindingService telegramBindingService;
    private final TokenService tokenService;

    public TelegramController(TelegramBindingService telegramBindingService,
                              TokenService tokenService) {
        this.telegramBindingService = telegramBindingService;
        this.tokenService = tokenService;
    }

    @PostMapping("/bind/start")
    public ResponseEntity<Map<String, Object>> startBinding(HttpServletRequest request) {
        String token = extractToken(request);
        Long userId = tokenService.extractUserId(token);

        Map<String, Object> response = telegramBindingService.startBinding(userId);
        return ResponseEntity.ok(response);
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
    @PostMapping("/bind/complete")
    public ResponseEntity<Map<String, Object>> completeBinding(HttpServletRequest request) {
        String token = extractToken(request);
        Long userId = tokenService.extractUserId(token);

        Map<String, Object> response = telegramBindingService.completeBinding(userId);
        return ResponseEntity.ok(response);
    }

}