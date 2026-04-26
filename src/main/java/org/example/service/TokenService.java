package org.example.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.example.dto.LoggedInUserResponse;
import org.example.exception.UnauthorizedException;
import org.example.model.User;
import org.example.repository.UserSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public final class TokenService {

  private static final String CREDENTIAL_VERSION_CLAIM = "credv";

  private final SecretKey secretKey;
  private final long expirationMinutes;
  private final String credentialVersionSalt;
  private final UserSessionRepository userSessionRepository;

  public TokenService(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.expiration-minutes}") long expirationMinutes,
      UserSessionRepository userSessionRepository) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expirationMinutes = expirationMinutes;
    this.credentialVersionSalt = secret;
    this.userSessionRepository = userSessionRepository;
  }

  public String generateToken(User user) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);
    String tokenId = UUID.randomUUID().toString();

    String token =
        Jwts.builder()
            .id(tokenId)
            .subject(user.getId().toString())
            .claim("login", user.getLogin())
            .claim("role", user.getRole().name())
            .claim(CREDENTIAL_VERSION_CLAIM, buildCredentialVersion(user))
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(secretKey)
            .compact();

    LocalDateTime loggedInAt = LocalDateTime.now();
    LocalDateTime expiresAtLocal = loggedInAt.plusMinutes(expirationMinutes);

    userSessionRepository.createSession(user.getId(), tokenId, loggedInAt, expiresAtLocal);

    return token;
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

  public boolean isSessionActive(String token) {
    Claims claims = parseClaims(token);
    String tokenId = claims.getId();
    return tokenId != null && userSessionRepository.isSessionActive(tokenId);
  }

  public void revokeToken(String token) {
    Claims claims = parseClaims(token);
    String tokenId = claims.getId();

    if (tokenId != null) {
      userSessionRepository.revokeSession(tokenId);
    }
  }

  public List<LoggedInUserResponse> getLoggedInUsers() {
    userSessionRepository.cleanupExpiredSessions();
    return userSessionRepository.findLoggedInUsers();
  }

  private Claims parseClaims(String token) {
    try {
      return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
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
