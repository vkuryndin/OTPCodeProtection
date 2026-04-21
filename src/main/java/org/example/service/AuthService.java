package org.example.service;

import org.example.dto.RegisterRequest;
import org.example.model.Role;
import org.example.model.User;
import org.example.repository.UserRepository;
import org.example.security.PasswordHasher;
import org.springframework.stereotype.Service;
import org.example.util.AuthValidationUtil;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    public Long register(RegisterRequest request) {
        validateRegisterRequest(request);

        if (userRepository.findByLogin(request.getLogin()) != null) {
            throw new IllegalArgumentException("User with this login already exists");
        }

        if (request.getRole() == Role.ADMIN && userRepository.adminExists()) {
            throw new IllegalStateException("Admin already exists");
        }

        User user = new User();
        user.setLogin(request.getLogin().trim());
        user.setPasswordHash(passwordHasher.hash(request.getPassword()));
        user.setRole(request.getRole());
        user.setEmail(emptyToNull(request.getEmail()));
        user.setPhone(emptyToNull(request.getPhone()));
        user.setTelegramChatId(emptyToNull(request.getTelegramChatId()));

        return userRepository.createUser(user);
    }

    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        String loginError = AuthValidationUtil.validateLogin(request.getLogin());
        if (loginError != null) {
            throw new IllegalArgumentException(loginError);
        }

        String passwordError = AuthValidationUtil.validatePassword(request.getPassword());
        if (passwordError != null) {
            throw new IllegalArgumentException(passwordError);
        }

        if (request.getRole() == null) {
            throw new IllegalArgumentException("Role is required");
        }
    }

    public User login(String login, String password) {
        String loginError = AuthValidationUtil.validateLogin(login);
        if (loginError != null) {
            throw new IllegalArgumentException(loginError);
        }

        String passwordError = AuthValidationUtil.validatePassword(password);
        if (passwordError != null) {
            throw new IllegalArgumentException(passwordError);
        }

        User user = userRepository.findByLogin(login.trim());
        if (user == null) {
            throw new IllegalArgumentException("Invalid login or password");
        }

        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid login or password");
        }

        return user;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String emptyToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}