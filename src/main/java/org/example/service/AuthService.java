package org.example.service;

import java.util.Locale;
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

  private static final int LOGIN_MAX_LENGTH = 100;
  private static final int PASSWORD_MAX_LENGTH = 255;
  private static final int EMAIL_MAX_LENGTH = 255;
  private static final int PHONE_MAX_LENGTH = 30;
  private static final int TELEGRAM_CHAT_ID_MAX_LENGTH = 100;

  private final UserRepository userRepository;
  private final PasswordHasher passwordHasher;
  private final TokenService tokenService;

  public AuthService(
      UserRepository userRepository, PasswordHasher passwordHasher, TokenService tokenService) {
    this.userRepository = userRepository;
    this.passwordHasher = passwordHasher;
    this.tokenService = tokenService;
  }

  public Long register(RegisterRequest request) {
    validateRegisterRequest(request);

    String normalizedLogin = normalizeLogin(request.getLogin());

    if (userRepository.findByLogin(normalizedLogin) != null) {
      throw new IllegalArgumentException("User with this login already exists");
    }

    if (request.getRole() == Role.ADMIN && userRepository.adminExists()) {
      throw new IllegalStateException("Admin already exists");
    }

    User user = new User();
    user.setLogin(normalizedLogin);
    user.setPasswordHash(passwordHasher.hash(request.getPassword()));
    user.setRole(request.getRole());
    user.setEmail(emptyToNull(request.getEmail()));
    user.setPhone(emptyToNull(request.getPhone()));
    user.setTelegramChatId(emptyToNull(request.getTelegramChatId()));

    Long userId = userRepository.createUser(user);

    log.info(
        "User registered successfully: userId={}, login={}, role={}",
        userId,
        user.getLogin(),
        user.getRole());

    return userId;
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

    validateLoginFieldLengths(login, password);

    String normalizedLogin = normalizeLogin(login);
    User user = userRepository.findByLogin(normalizedLogin);

    if (user == null) {
      log.warn("Authentication failed: user not found, login={}", normalizedLogin);
      throw new IllegalArgumentException("Invalid login or password");
    }

    if (!passwordHasher.matches(password, user.getPasswordHash())) {
      log.warn(
          "Authentication failed: wrong password, login={}, userId={}",
          normalizedLogin,
          user.getId());
      throw new IllegalArgumentException("Invalid login or password");
    }

    log.info(
        "Authentication successful: userId={}, login={}, role={}",
        user.getId(),
        user.getLogin(),
        user.getRole());

    return user;
  }

  public String loginAndGenerateToken(String login, String password) {
    User user = authenticate(login, password);
    String token = tokenService.generateToken(user);

    log.info("JWT token issued: userId={}, login={}", user.getId(), user.getLogin());

    return token;
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

    validateRegisterFieldLengths(request);
  }

  private void validateRegisterFieldLengths(RegisterRequest request) {
    validateMaxLength(request.getLogin(), LOGIN_MAX_LENGTH, "Login is too long");
    validateMaxLength(request.getPassword(), PASSWORD_MAX_LENGTH, "Password is too long");
    validateMaxLength(request.getEmail(), EMAIL_MAX_LENGTH, "Email is too long");
    validateMaxLength(request.getPhone(), PHONE_MAX_LENGTH, "Phone is too long");
    validateMaxLength(
        request.getTelegramChatId(), TELEGRAM_CHAT_ID_MAX_LENGTH, "Telegram chat id is too long");
  }

  private void validateLoginFieldLengths(String login, String password) {
    validateMaxLength(login, LOGIN_MAX_LENGTH, "Login is too long");
    validateMaxLength(password, PASSWORD_MAX_LENGTH, "Password is too long");
  }

  private void validateMaxLength(String value, int maxLength, String message) {
    if (value != null && value.length() > maxLength) {
      throw new IllegalArgumentException(message);
    }
  }

  private String normalizeLogin(String login) {
    return login.trim().toLowerCase(Locale.ROOT);
  }

  private String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
