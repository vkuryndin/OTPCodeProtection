package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.security.AuthUtil;
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
    private final AuthUtil authUtil;

    public TelegramController(TelegramBindingService telegramBindingService,
                              TokenService tokenService,
                              AuthUtil authUtil) {
        this.telegramBindingService = telegramBindingService;
        this.tokenService = tokenService;
        this.authUtil = authUtil;
    }

    @PostMapping("/bind/start")
    public ResponseEntity<Map<String, Object>> startBinding(HttpServletRequest request) {
        String token = authUtil.extractToken(request);
        Long userId = tokenService.extractUserId(token);

        Map<String, Object> response = telegramBindingService.startBinding(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/bind/complete")
    public ResponseEntity<Map<String, Object>> completeBinding(HttpServletRequest request) {
        String token = authUtil.extractToken(request);
        Long userId = tokenService.extractUserId(token);

        Map<String, Object> response = telegramBindingService.completeBinding(userId);
        return ResponseEntity.ok(response);
    }
}