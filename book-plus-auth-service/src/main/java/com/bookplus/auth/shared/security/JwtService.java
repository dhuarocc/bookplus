package com.bookplus.auth.shared.security;

import com.bookplus.auth.domain.model.RefreshToken;
import com.bookplus.auth.domain.model.User;
import com.bookplus.auth.domain.model.UserId;
import com.bookplus.auth.domain.model.UserRole;
import com.bookplus.auth.shared.security.JwtConfig.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Servicio JWT — genera, valida y gestiona tokens RS256.
 * También gestiona la blacklist de access tokens en Redis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private static final String CLAIM_ROLES    = "roles";
    private static final String CLAIM_EMAIL    = "email";
    private static final String CLAIM_USERNAME = "username";
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    /** SecureRandom es costoso de inicializar; se crea una vez y se reutiliza (thread-safe). */
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    private final JwtProperties          jwtProperties;
    private final StringRedisTemplate    redisTemplate;

    // ── Generación ────────────────────────────────────────────────────────

    public String generateAccessToken(User user) {
        Instant now        = Instant.now();
        Instant expiration = now.plusMillis(jwtProperties.accessTokenExpiration());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_EMAIL,    user.getEmail().value())
                .claim(CLAIM_ROLES,    user.getRoles().stream()
                        .map(UserRole::authority).collect(Collectors.toList()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(jwtProperties.privateKey(), Jwts.SIG.RS256)
                .compact();
    }

    public String generateRefreshTokenValue() {
        byte[] bytes = new byte[64];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public RefreshToken buildRefreshToken(User user, String plainToken,
                                          String ipAddress, String userAgent) {
        String tokenHash = hashToken(plainToken);
        Instant expiresAt = Instant.now().plusMillis(jwtProperties.refreshTokenExpiration());
        return RefreshToken.create(user.getId(), tokenHash, expiresAt, ipAddress, userAgent);
    }

    // ── Validación ────────────────────────────────────────────────────────

    public Claims validateAndParseToken(String token) {
        return Jwts.parser()
                .verifyWith(jwtProperties.publicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = validateAndParseToken(token);
            // Verificar blacklist
            return !isBlacklisted(claims.getId());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public UserId extractUserId(String token) {
        return UserId.of(validateAndParseToken(token).getSubject());
    }

    @SuppressWarnings("unchecked")
    public Set<UserRole> extractRoles(String token) {
        List<String> roles = validateAndParseToken(token).get(CLAIM_ROLES, List.class);
        return roles.stream()
                .map(UserRole::valueOf)
                .collect(Collectors.toSet());
    }

    // ── Blacklist (Redis) ─────────────────────────────────────────────────

    public void blacklistToken(String token) {
        try {
            Claims claims = validateAndParseToken(token);
            long ttlMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (ttlMillis > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_PREFIX + claims.getId(),
                        "revoked",
                        ttlMillis,
                        TimeUnit.MILLISECONDS
                );
                log.debug("Token blacklisted: jti={}", claims.getId());
            }
        } catch (JwtException e) {
            log.debug("Cannot blacklist expired/invalid token: {}", e.getMessage());
        }
    }

    private boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }

    // ── Utilidades ────────────────────────────────────────────────────────

    /**
     * Hash SHA-256 del token para almacenar en BD sin guardar el valor en claro.
     */
    public String hashToken(String plainToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public long getAccessTokenExpirationSeconds() {
        return jwtProperties.accessTokenExpiration() / 1000;
    }
}
