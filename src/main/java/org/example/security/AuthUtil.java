package org.example.security;

import jakarta.servlet.http.HttpServletRequest;
import org.example.model.Role;
import org.springframework.stereotype.Component;

import org.example.service.TokenService;

@Component
public class AuthUtil {

    private final TokenService tokenService;

    public AuthUtil(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    public String extractToken(HttpServletRequest request) {
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

    public Long extractUserId(HttpServletRequest request) {
        String token = extractToken(request);
        return tokenService.extractUserId(token);
    }

    public String extractRole(HttpServletRequest request) {
        String token = extractToken(request);
        return tokenService.extractRole(token);
    }

    public void requireAdmin(HttpServletRequest request) {
        String role = extractRole(request);

        if (!Role.ADMIN.name().equals(role)) {
            throw new SecurityException("Access denied");
        }
    }
}