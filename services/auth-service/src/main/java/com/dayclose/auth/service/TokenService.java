package com.dayclose.auth.service;

import com.dayclose.auth.config.JwtKeyProvider;
import com.dayclose.auth.domain.RefreshToken;
import com.dayclose.auth.domain.User;
import com.dayclose.auth.infra.RefreshTokenRepository;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues and validates RS256 JWT tokens and manages refresh token families (ADR-0002).
 */
@Service
public class TokenService {

    private final JwtKeyProvider keyProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final String issuer;
    private final long accessTokenValiditySeconds;
    private final long refreshTokenValiditySeconds;

    public TokenService(
            JwtKeyProvider keyProvider,
            RefreshTokenRepository refreshTokenRepository,
            @Value("${dayclose.jwt.issuer:dayclose-auth-service}") String issuer,
            @Value("${dayclose.jwt.access-token-validity-seconds:900}") long accessTokenValiditySeconds,
            @Value("${dayclose.jwt.refresh-token-validity-seconds:604800}") long refreshTokenValiditySeconds
    ) {
        this.keyProvider = keyProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.issuer = issuer;
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresIn) {}

    /**
     * Issues an RS256 access token for a user.
     */
    public String createAccessToken(UUID userId, UUID tenantId, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessTokenValiditySeconds, ChronoUnit.SECONDS);

        return Jwts.builder()
                .header()
                .keyId(keyProvider.getKeyId())
                .and()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("tenant_id", tenantId.toString())
                .claim("roles", List.of(role))
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(keyProvider.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Issues an RS256 client-credentials token for service-to-service communication.
     */
    public String createServiceToken(String clientId, UUID tenantId, String scopes) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessTokenValiditySeconds, ChronoUnit.SECONDS);

        return Jwts.builder()
                .header()
                .keyId(keyProvider.getKeyId())
                .and()
                .issuer(issuer)
                .subject("service:" + clientId)
                .claim("tenant_id", tenantId.toString())
                .claim("roles", List.of("SERVICE"))
                .claim("scopes", scopes)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(keyProvider.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Creates a new access and refresh token pair starting a new refresh token family.
     */
    @Transactional
    public TokenPair issueInitialTokens(User user) {
        String accessToken = createAccessToken(user.id(), user.tenantId(), user.role());
        String rawRefreshToken = generateSecureToken();
        String hash = hashToken(rawRefreshToken);
        UUID familyId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(refreshTokenValiditySeconds, ChronoUnit.SECONDS);

        refreshTokenRepository.create(user.id(), hash, familyId, expiresAt);
        return new TokenPair(accessToken, rawRefreshToken, accessTokenValiditySeconds);
    }

    /**
     * Rotates a refresh token. If a previously used (revoked) token is presented,
     * the entire token family is revoked (reuse detection per ADR-0002).
     */
    @Transactional
    public TokenPair rotateRefreshToken(String rawRefreshToken, User user) {
        String hash = hashToken(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (stored.revoked() || stored.expiresAt().isBefore(Instant.now())) {
            // REUSE DETECTED! Revoke the entire family
            refreshTokenRepository.revokeFamily(stored.familyId());
            throw new IllegalStateException("Refresh token reuse detected or token expired; family revoked");
        }

        // Revoke current token
        refreshTokenRepository.revoke(stored.id());

        // Issue new token in same family
        String newRawRefreshToken = generateSecureToken();
        String newHash = hashToken(newRawRefreshToken);
        Instant newExpiry = Instant.now().plus(refreshTokenValiditySeconds, ChronoUnit.SECONDS);
        refreshTokenRepository.create(user.id(), newHash, stored.familyId(), newExpiry);

        String newAccessToken = createAccessToken(user.id(), user.tenantId(), user.role());
        return new TokenPair(newAccessToken, newRawRefreshToken, accessTokenValiditySeconds);
    }

    @Transactional
    public void revokeToken(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            refreshTokenRepository.revoke(token.id());
        });
    }

    private String generateSecureToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
