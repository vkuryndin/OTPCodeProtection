package org.example.security;

import jakarta.servlet.http.HttpServletRequest;
import org.example.model.Role;
import org.example.service.TokenService;
import org.springframework.stereotype.Component;

@Component
public class RequestAuthService {

    private final AuthUtil authUtil;
    private final TokenService tokenService;

    public RequestAuthService(AuthUtil authUtil, TokenService tokenService) {
        this.authUtil = authUtil;
        this.tokenService = tokenService;
    }

    public RequestUserContext read(HttpServletRequest request) {
        String token = authUtil.extractToken(request);
        Long userId = tokenService.extractUserId(token);
        String role = tokenService.extractRole(token);

        return new RequestUserContext(token, userId, role);
    }

    public Long extractUserId(HttpServletRequest request) {
        return read(request).userId();
    }

    public Long requireAdminUserId(HttpServletRequest request) {
        RequestUserContext context = read(request);

        if (!Role.ADMIN.name().equals(context.role())) {
            throw new SecurityException("Access denied");
        }

        return context.userId();
    }

    public record RequestUserContext(
            String token,
            Long userId,
            String role
    ) {
    }
}