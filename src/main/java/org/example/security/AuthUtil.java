package org.example.security;

import jakarta.servlet.http.HttpServletRequest;
import org.example.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

@Component
public class AuthUtil {

    private static final String BEARER_PREFIX = "Bearer ";

    public String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || authHeader.isBlank()) {
            throw new UnauthorizedException("Authorization header is required");
        }

        if (!authHeader.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("Invalid authorization format");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        if (token.isBlank()) {
            throw new UnauthorizedException("Token is required");
        }

        return token;
    }
}