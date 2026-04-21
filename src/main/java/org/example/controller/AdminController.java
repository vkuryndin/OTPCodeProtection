package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.dto.UpdateOtpConfigRequest;
import org.example.model.Role;
import org.example.model.User;
import org.example.repository.OtpConfigRepository;
import org.example.repository.UserRepository;
import org.example.security.AuthUtil;
import org.example.service.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.example.dto.UserResponse;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import org.example.model.OtpConfig;
import org.example.model.Role;
import org.example.repository.OtpConfigRepository;
import org.example.service.TokenService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final AuthUtil authUtil;
    private final OtpConfigRepository otpConfigRepository;
    private final TokenService tokenService;
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    public AdminController(UserRepository userRepository, AuthUtil authUtil, OtpConfigRepository otpConfigRepository, TokenService tokenService) {
        this.userRepository = userRepository;
        this.authUtil = authUtil;
        this.otpConfigRepository = otpConfigRepository;
        this.tokenService = tokenService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getUsers(HttpServletRequest request) {
        requireAdmin(request);
        Long adminId = tokenService.extractUserId(extractToken(request));

        List<UserResponse> users = userRepository.findAllNonAdmins()
                .stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());

        log.info("Admin fetched users list: adminId={}, returnedUsers={}", adminId, users.size());

        return ResponseEntity.ok(users);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long id,
                                                          HttpServletRequest request) {
        requireAdmin(request);
        Long adminId = tokenService.extractUserId(extractToken(request));

        User user = userRepository.findById(id);
        if (user == null) {
            log.warn("Admin delete failed: user not found, adminId={}, targetUserId={}", adminId, id);
            throw new IllegalArgumentException("User not found");
        }

        if (user.getRole() == Role.ADMIN) {
            log.warn("Admin delete failed: attempt to delete admin, adminId={}, targetUserId={}", adminId, id);
            throw new IllegalArgumentException("Admin cannot be deleted");
        }

        boolean deleted = userRepository.deleteById(id);
        if (!deleted) {
            log.warn("Admin delete failed during delete, adminId={}, targetUserId={}", adminId, id);
            throw new IllegalArgumentException("User not found");
        }

        log.info("User deleted by admin: adminId={}, targetUserId={}, targetLogin={}",
                adminId, id, user.getLogin());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "User deleted successfully");
        response.put("userId", id);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/otp-config")
    public ResponseEntity<?> getOtpConfig(HttpServletRequest request) {
        requireAdmin(request);
        Long adminId = tokenService.extractUserId(extractToken(request));

        OtpConfig config = otpConfigRepository.getConfig();
        if (config == null) {
            log.warn("Admin requested OTP config but config not found: adminId={}", adminId);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("error", "OTP config not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        log.info("OTP config fetched by admin: adminId={}, codeLength={}, ttlSeconds={}",
                adminId, config.getCodeLength(), config.getTtlSeconds());

        return ResponseEntity.ok(config);
    }
    @PutMapping("/otp-config")
    public ResponseEntity<Map<String, Object>> updateOtpConfig(@RequestBody UpdateOtpConfigRequest request,
                                                               HttpServletRequest httpRequest) {
        requireAdmin(httpRequest);
        Long adminId = tokenService.extractUserId(extractToken(httpRequest));

        if (request == null) {
            log.warn("OTP config update failed: request body is missing, adminId={}", adminId);
            throw new IllegalArgumentException("Request body is required");
        }

        if (request.getCodeLength() == null) {
            log.warn("OTP config update failed: codeLength is missing, adminId={}", adminId);
            throw new IllegalArgumentException("Code length is required");
        }

        if (request.getTtlSeconds() == null) {
            log.warn("OTP config update failed: ttlSeconds is missing, adminId={}", adminId);
            throw new IllegalArgumentException("TTL seconds is required");
        }

        if (request.getCodeLength() < 4 || request.getCodeLength() > 10) {
            log.warn("OTP config update failed: invalid codeLength={}, adminId={}",
                    request.getCodeLength(), adminId);
            throw new IllegalArgumentException("Code length must be between 4 and 10");
        }

        if (request.getTtlSeconds() <= 0) {
            log.warn("OTP config update failed: invalid ttlSeconds={}, adminId={}",
                    request.getTtlSeconds(), adminId);
            throw new IllegalArgumentException("TTL seconds must be greater than 0");
        }

        boolean updated = otpConfigRepository.updateConfig(
                request.getCodeLength(),
                request.getTtlSeconds()
        );

        if (!updated) {
            log.warn("OTP config update failed: config not found, adminId={}", adminId);
            throw new IllegalArgumentException("OTP config not found");
        }

        log.info("OTP config updated by admin: adminId={}, codeLength={}, ttlSeconds={}",
                adminId, request.getCodeLength(), request.getTtlSeconds());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "OTP config updated successfully");
        response.put("codeLength", request.getCodeLength());
        response.put("ttlSeconds", request.getTtlSeconds());

        return ResponseEntity.ok(response);
    }


    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getLogin(),
                user.getRole(),
                user.getEmail(),
                user.getPhone(),
                user.getTelegramChatId(),
                user.getCreatedAt()
        );

    }
    private void requireAdmin(HttpServletRequest request) {
        String token = extractToken(request);
        String role = tokenService.extractRole(token);

        if (!Role.ADMIN.name().equals(role)) {
            throw new SecurityException("Access denied");
        }
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
}