package com.dayclose.auth.service;

import com.dayclose.auth.config.JwtKeyProvider;
import com.dayclose.auth.domain.RefreshToken;
import com.dayclose.auth.domain.User;
import com.dayclose.auth.infra.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthCryptoTest {

    private PasswordService passwordService;
    private TokenService tokenService;
    private JwtKeyProvider keyProvider;
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService();
        keyProvider = new JwtKeyProvider();
        refreshTokenRepository = Mockito.mock(RefreshTokenRepository.class);
        tokenService = new TokenService(
                keyProvider,
                refreshTokenRepository,
                "dayclose-auth-service",
                900,
                604800
        );
    }

    @Test
    @DisplayName("Argon2id hashes and verifies passwords correctly")
    void testPasswordHashing() {
        String raw = "superSecurePass123!";
        String hash = passwordService.hash(raw);

        assertNotNull(hash);
        assertTrue(hash.startsWith("$argon2id$"));
        assertTrue(passwordService.verify(hash, raw));
        assertFalse(passwordService.verify(hash, "wrongPass"));
    }

    @Test
    @DisplayName("RS256 JWT access token contains verified claims (ADR-0002)")
    void testAccessTokenCreation() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String role = "OWNER";

        String token = tokenService.createAccessToken(userId, tenantId, role);
        assertNotNull(token);

        Claims claims = Jwts.parser()
                .verifyWith(keyProvider.getPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals(userId.toString(), claims.getSubject());
        assertEquals(tenantId.toString(), claims.get("tenant_id"));
        assertEquals(List.of("OWNER"), claims.get("roles"));
        assertNotNull(claims.getId());
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("Refresh token rotation revokes family on reuse detection (ADR-0002)")
    void testRefreshTokenReuseDetection() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = new User(userId, tenantId, "owner@shop.com", "hash", "OWNER", Instant.now());

        String rawToken = "test-raw-token";
        String tokenHash = TokenService.hashToken(rawToken);
        UUID familyId = UUID.randomUUID();

        // Stored token is already revoked
        RefreshToken revokedToken = new RefreshToken(
                UUID.randomUUID(), userId, tokenHash, familyId, true,
                Instant.now().plusSeconds(3600), Instant.now()
        );

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(revokedToken));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                tokenService.rotateRefreshToken(rawToken, user)
        );

        assertTrue(ex.getMessage().contains("Refresh token reuse detected"));
        verify(refreshTokenRepository).revokeFamily(familyId);
    }
}
