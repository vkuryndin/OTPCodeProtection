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

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public AuthService(UserRepository userRepository,
                       PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    public Long register(RegisterRequest request) {
        validateRegisterRequest(request);

        String normalizedLogin = request.getLogin().trim();
        Role role = request.getRole();

        if (userRepository.findByLogin(normalizedLogin) != null) {
            throw new IllegalArgumentException("User with this login already exists");
        }

        if (role == Role.ADMIN && userRepository.adminExists()) {
            throw new IllegalStateException("Admin already exists");
        }

        User user = buildUserForRegistration(request, normalizedLogin);
        Long userId = userRepository.createUser(user);

        log.info("User registered successfully: userId={}, login={}, role={}",
                userId, user.getLogin(), user.getRole());

        return userId;
    }

    public User login(String login, String password) {
        return authenticate(login, password);
    }

    public User authenticate(String login, String password) {
        String normalizedLogin = validateCredentialsForAuthentication(login, password);

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

    private String validateCredentialsForAuthentication(String login, String password) {
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

        return login.trim();
    }

    private User buildUserForRegistration(RegisterRequest request, String normalizedLogin) {
        User user = new User();
        user.setLogin(normalizedLogin);
        user.setPasswordHash(passwordHasher.hash(request.getPassword()));
        user.setRole(request.getRole());
        user.setEmail(emptyToNull(request.getEmail()));
        user.setPhone(emptyToNull(request.getPhone()));
        user.setTelegramChatId(emptyToNull(request.getTelegramChatId()));
        return user;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}