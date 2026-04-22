package org.example.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.exception.UnauthorizedException;
import org.example.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenService {

    private final String secret;
    private final long expirationMinutes;
    private final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();

    private volatile SecretKey secretKey;

    public TokenService(@Value("${jwt.secret}") String secret,
                        @Value("${jwt.expiration-minutes}") long expirationMinutes) {
        this.secret = secret;
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(User user) {
        validateExpirationMinutes();

        Instant now = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("login", user.getLogin())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(getSecretKey())
                .compact();
    }

    public Long extractUserId(String token) {
        Claims claims = parseClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    public String extractRole(String token) {
        Claims claims = parseClaims(token);
        return claims.get("role", String.class);
    }

    public void revokeToken(String token) {
        revokedTokens.add(token);
    }

    private Claims parseClaims(String token) {
        ensureTokenNotRevoked(token);

        try {
            return Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid or expired token");
        }
    }

    private void ensureTokenNotRevoked(String token) {
        if (revokedTokens.contains(token)) {
            throw new UnauthorizedException("Invalid or expired token");
        }
    }

    private SecretKey getSecretKey() {
        SecretKey localKey = secretKey;

        if (localKey == null) {
            synchronized (this) {
                localKey = secretKey;
                if (localKey == null) {
                    try {
                        localKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
                        secretKey = localKey;
                    } catch (Exception e) {
                        throw new IllegalStateException("JWT secret is invalid", e);
                    }
                }
            }
        }

        return localKey;
    }

    private void validateExpirationMinutes() {
        if (expirationMinutes <= 0) {
            throw new IllegalStateException("JWT expiration must be greater than 0");
        }
    }
}