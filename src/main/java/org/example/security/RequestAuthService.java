package org.example.security;

import jakarta.servlet.http.HttpServletRequest;
import org.example.exception.UnauthorizedException;
import org.example.model.Role;
import org.example.model.User;
import org.example.repository.UserRepository;
import org.example.service.TokenService;
import org.springframework.stereotype.Service;

@Service
public class RequestAuthService {

    private final AuthUtil authUtil;
    private final TokenService tokenService;
    private final UserRepository userRepository;

    public RequestAuthService(AuthUtil authUtil,
                              TokenService tokenService,
                              UserRepository userRepository) {
        this.authUtil = authUtil;
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    public RequestUserContext read(HttpServletRequest request) {
        String token = authUtil.extractToken(request);

        Long userId = tokenService.extractUserId(token);
        User user = userRepository.findById(userId);

        if (user == null) {
            throw new UnauthorizedException("Invalid or expired token");
        }

        if (!tokenService.isTokenCurrentForUser(token, user)) {
            throw new UnauthorizedException("Invalid or expired token");
        }

        return new RequestUserContext(userId, token, user);
    }

    public Long extractUserId(HttpServletRequest request) {
        return read(request).userId();
    }

    public Long requireAdminUserId(HttpServletRequest request) {
        RequestUserContext context = read(request);

        if (context.role() != Role.ADMIN) {
            throw new SecurityException("Access denied");
        }

        return context.userId();
    }

    public static final class RequestUserContext {
        private final Long userId;
        private final String token;
        private final User user;

        public RequestUserContext(Long userId, String token, User user) {
            this.userId = userId;
            this.token = token;
            this.user = user;
        }

        public Long userId() {
            return userId;
        }

        public String token() {
            return token;
        }

        public User user() {
            return user;
        }

        public Role role() {
            return user.getRole();
        }

        public Long getUserId() {
            return userId;
        }

        public String getToken() {
            return token;
        }

        public User getUser() {
            return user;
        }

        public Role getRole() {
            return user.getRole();
        }
    }
}