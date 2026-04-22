package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.security.RequestAuthService;
import org.example.service.TelegramBindingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/telegram")
public class TelegramController {

    private final TelegramBindingService telegramBindingService;
    private final RequestAuthService requestAuthService;

    public TelegramController(TelegramBindingService telegramBindingService,
                              RequestAuthService requestAuthService) {
        this.telegramBindingService = telegramBindingService;
        this.requestAuthService = requestAuthService;
    }

    @PostMapping("/bind/start")
    public ResponseEntity<Map<String, Object>> startBinding(HttpServletRequest request) {
        Long userId = requestAuthService.extractUserId(request);
        Map<String, Object> response = telegramBindingService.startBinding(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/bind/complete")
    public ResponseEntity<Map<String, Object>> completeBinding(HttpServletRequest request) {
        Long userId = requestAuthService.extractUserId(request);
        Map<String, Object> response = telegramBindingService.completeBinding(userId);
        return ResponseEntity.ok(response);
    }
}