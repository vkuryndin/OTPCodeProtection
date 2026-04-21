package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.model.Role;
import org.example.model.User;
import org.example.repository.UserRepository;
import org.example.security.AuthUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.example.dto.UserResponse;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final AuthUtil authUtil;

    public AdminController(UserRepository userRepository, AuthUtil authUtil) {
        this.userRepository = userRepository;
        this.authUtil = authUtil;
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getUsers(HttpServletRequest request) {
        authUtil.requireAdmin(request);

        List<UserResponse> users = userRepository.findAllNonAdmins()
                .stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(users);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", e.getMessage());

        String message = e.getMessage();
        if ("Authorization header is required".equals(message)
                || "Invalid authorization format".equals(message)
                || "Token is required".equals(message)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        if ("User not found".equals(message)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        if ("Admin cannot be deleted".equals(message)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(SecurityException e) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long id,
                                                          HttpServletRequest request) {
        authUtil.requireAdmin(request);

        User user = userRepository.findById(id);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        if (user.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("Admin cannot be deleted");
        }

        boolean deleted = userRepository.deleteById(id);
        if (!deleted) {
            throw new IllegalArgumentException("User not found");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "User deleted successfully");
        response.put("userId", id);

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
}