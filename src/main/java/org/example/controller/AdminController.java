package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.dto.DeleteUserResponse;
import org.example.dto.UpdateOtpConfigRequest;
import org.example.dto.UpdateOtpConfigResponse;
import org.example.dto.UserResponse;
import org.example.exception.NotFoundException;
import org.example.model.OtpConfig;
import org.example.model.Role;
import org.example.model.User;
import org.example.repository.OtpConfigRepository;
import org.example.repository.UserRepository;
import org.example.security.RequestAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.example.dto.LoggedInUserResponse;
import org.example.service.TokenService;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final UserRepository userRepository;
    private final OtpConfigRepository otpConfigRepository;
    private final RequestAuthService requestAuthService;
    private final TokenService tokenService;

    public AdminController(UserRepository userRepository,
                           OtpConfigRepository otpConfigRepository,
                           RequestAuthService requestAuthService,
                           TokenService tokenService) {
        this.userRepository = userRepository;
        this.otpConfigRepository = otpConfigRepository;
        this.requestAuthService = requestAuthService;
        this.tokenService = tokenService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getUsers(HttpServletRequest request) {
        Long adminId = requestAuthService.requireAdminUserId(request);

        List<UserResponse> users = userRepository.findAllNonAdmins()
                .stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());

        log.info("Admin fetched users list: adminId={}, returnedUsers={}", adminId, users.size());

        return ResponseEntity.ok(users);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<DeleteUserResponse> deleteUser(@PathVariable Long id,
                                                         HttpServletRequest request) {
        Long adminId = requestAuthService.requireAdminUserId(request);

        User user = requireExistingUserForDelete(adminId, id);
        ensureTargetUserIsNotAdmin(adminId, id, user);

        boolean deleted = userRepository.deleteById(id);
        if (!deleted) {
            log.warn("Admin delete failed during delete, adminId={}, targetUserId={}", adminId, id);
            throw new NotFoundException("User not found");
        }

        log.info("User deleted by admin: adminId={}, targetUserId={}, targetLogin={}",
                adminId, id, user.getLogin());

        DeleteUserResponse response = new DeleteUserResponse(
                "User deleted successfully",
                id
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/otp-config")
    public ResponseEntity<?> getOtpConfig(HttpServletRequest request) {
        Long adminId = requestAuthService.requireAdminUserId(request);

        OtpConfig config = otpConfigRepository.getConfig();
        if (config == null) {
            log.warn("Admin requested OTP config but config not found: adminId={}", adminId);
            return buildOtpConfigNotFoundResponse();
        }

        log.info("OTP config fetched by admin: adminId={}, codeLength={}, ttlSeconds={}",
                adminId, config.getCodeLength(), config.getTtlSeconds());

        return ResponseEntity.ok(config);
    }

    @PutMapping("/otp-config")
    public ResponseEntity<UpdateOtpConfigResponse> updateOtpConfig(@RequestBody UpdateOtpConfigRequest request,
                                                                   HttpServletRequest httpRequest) {
        Long adminId = requestAuthService.requireAdminUserId(httpRequest);

        validateUpdateOtpConfigRequest(request, adminId);

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

        UpdateOtpConfigResponse response = new UpdateOtpConfigResponse(
                "OTP config updated successfully",
                request.getCodeLength(),
                request.getTtlSeconds()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/logged-in-users")
    public ResponseEntity<List<LoggedInUserResponse>> getLoggedInUsers(HttpServletRequest request) {
        Long adminId = requestAuthService.requireAdminUserId(request);

        List<LoggedInUserResponse> users = tokenService.getLoggedInUsers();

        log.info("Admin fetched logged-in users: adminId={}, returnedUsers={}", adminId, users.size());
        return ResponseEntity.ok(users);
    }

    private User requireExistingUserForDelete(Long adminId, Long targetUserId) {
        User user = userRepository.findById(targetUserId);
        if (user == null) {
            log.warn("Admin delete failed: user not found, adminId={}, targetUserId={}", adminId, targetUserId);
            throw new NotFoundException("User not found");
        }
        return user;
    }

    private void ensureTargetUserIsNotAdmin(Long adminId, Long targetUserId, User user) {
        if (user.getRole() == Role.ADMIN) {
            log.warn("Admin delete failed: attempt to delete admin, adminId={}, targetUserId={}", adminId, targetUserId);
            throw new IllegalArgumentException("Admin cannot be deleted");
        }
    }

    private ResponseEntity<Map<String, Object>> buildOtpConfigNotFoundResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", "OTP config not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    private void validateUpdateOtpConfigRequest(UpdateOtpConfigRequest request, Long adminId) {
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
}