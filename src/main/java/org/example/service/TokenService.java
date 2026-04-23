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
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public final class TokenService {

    private static final String CREDENTIAL_VERSION_CLAIM = "credv";

    private final SecretKey secretKey;
    private final long expirationMinutes;
    private final String credentialVersionSalt;
    private final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();

    public TokenService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-minutes}") long expirationMinutes
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("jwt.secret must not be blank");
        }
        if (expirationMinutes <= 0) {
            throw new IllegalArgumentException("jwt.expiration-minutes must be greater than 0");
        }

        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
        this.credentialVersionSalt = secret;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("login", user.getLogin())
                .claim("role", user.getRole().name())
                .claim(CREDENTIAL_VERSION_CLAIM, buildCredentialVersion(user))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
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

    public boolean isTokenCurrentForUser(String token, User user) {
        Claims claims = parseClaims(token);
        String tokenCredentialVersion = claims.get(CREDENTIAL_VERSION_CLAIM, String.class);
        return buildCredentialVersion(user).equals(tokenCredentialVersion);
    }

    public void revokeToken(String token) {
        revokedTokens.add(token);
    }

    private Claims parseClaims(String token) {
        if (revokedTokens.contains(token)) {
            throw new UnauthorizedException("Invalid or expired token");
        }

        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid or expired token");
        }
    }

    private String buildCredentialVersion(User user) {
        return buildCredentialVersion(user.getPasswordHash());
    }

    private String buildCredentialVersion(String passwordHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] raw = (credentialVersionSalt + ":" + passwordHash).getBytes(StandardCharsets.UTF_8);
            byte[] hash = digest.digest(raw);

            byte[] shortHash = new byte[16];
            System.arraycopy(hash, 0, shortHash, 0, shortHash.length);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(shortHash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build credential version", e);
        }
    }
}