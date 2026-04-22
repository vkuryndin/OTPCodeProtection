package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.dto.LoginRequest;
import org.example.dto.LoginResponse;
import org.example.dto.LogoutResponse;
import org.example.dto.RegisterRequest;
import org.example.dto.RegisterResponse;
import org.example.model.User;
import org.example.security.AuthUtil;
import org.example.service.AuthService;
import org.example.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final AuthUtil authUtil;
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    public AuthController(AuthService authService, TokenService tokenService, AuthUtil authUtil) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.authUtil = authUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest request) {
        Long userId = authService.register(request);

        RegisterResponse response = new RegisterResponse(
                "User registered successfully",
                userId
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        User user = authService.authenticate(request.getLogin(), request.getPassword());
        String token = tokenService.generateToken(user);

        LoginResponse response = new LoginResponse(
                "Login successful",
                token,
                user.getId(),
                user.getLogin(),
                user.getRole()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(HttpServletRequest request) {
        String token = authUtil.extractToken(request);

        Long userId = tokenService.extractUserId(token);
        tokenService.revokeToken(token);

        log.info("Logout successful: userId={}", userId);

        LogoutResponse response = new LogoutResponse("Logout successful");
        return ResponseEntity.ok(response);
    }
}