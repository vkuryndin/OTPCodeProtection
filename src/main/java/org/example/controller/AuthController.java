package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.dto.RegisterRequest;
import org.example.service.AuthService;
import org.example.service.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.example.dto.LoginRequest;
import org.example.model.User;

import java.util.LinkedHashMap;
import java.util.Map;

import org.example.dto.UserResponse;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    public AuthController(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        Long userId = authService.register(request);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "User registered successfully");
        response.put("userId", userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", e.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException e) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        User user = authService.authenticate(request.getLogin(), request.getPassword());
        String token = authService.loginAndGenerateToken(request.getLogin(), request.getPassword());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Login successful");
        response.put("token", token);
        response.put("userId", user.getId());
        response.put("login", user.getLogin());
        response.put("role", user.getRole());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
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

        tokenService.extractUserId(token);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Logout successful");
        return ResponseEntity.ok(response);
    }


}