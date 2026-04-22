package org.example.service;

import org.example.dto.RegisterRequest;
import org.example.model.Role;
import org.example.model.User;
import org.example.repository.UserRepository;
import org.example.security.PasswordHasher;
import org.example.util.AuthValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
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

        Long userId = userRepository.createUser(user);

        log.info("User registered successfully: userId={}, login={}, role={}",
                userId, user.getLogin(), user.getRole());

        return userId;
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

    public String loginAndGenerateToken(String login, String password) {
        User user = authenticate(login, password);
        String token = tokenService.generateToken(user);

        log.info("JWT token issued: userId={}, login={}", user.getId(), user.getLogin());

        return token;
    }

    public User authenticate(String login, String password) {
        String loginError = AuthValidationUtil.validateLogin(login);
        if (loginError != null) {
            log.warn("Authentication failed: invalid login format, login={}", login);
            throw new IllegalArgumentException(loginError);
        }

        String passwordError = AuthValidationUtil.validatePassword(password);
        if (passwordError != null) {
            log.warn("Authentication failed: invalid password format, login={}", login);
            throw new IllegalArgumentException(passwordError);
        }

        String normalizedLogin = login.trim();
        User user = userRepository.findByLogin(normalizedLogin);

        if (user == null) {
            log.warn("Authentication failed: user not found, login={}", normalizedLogin);
            throw new IllegalArgumentException("Invalid login or password");
        }

        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            log.warn("Authentication failed: wrong password, login={}, userId={}",
                    normalizedLogin, user.getId());
            throw new IllegalArgumentException("Invalid login or password");
        }

        log.info("Authentication successful: userId={}, login={}, role={}",
                user.getId(), user.getLogin(), user.getRole());

        return user;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}