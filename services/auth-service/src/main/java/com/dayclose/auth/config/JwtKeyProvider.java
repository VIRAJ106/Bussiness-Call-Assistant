package com.dayclose.auth.config;

import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the RS256 KeyPair and provides JWKS representation (ADR-0002).
 */
@Component
public class JwtKeyProvider {

    private final String keyId = "dayclose-key-" + UUID.randomUUID().toString().substring(0, 8);
    private final KeyPair keyPair;

    public JwtKeyProvider() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            this.keyPair = generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm not available", e);
        }
    }

    public RSAPrivateKey getPrivateKey() {
        return (RSAPrivateKey) keyPair.getPrivate();
    }

    public RSAPublicKey getPublicKey() {
        return (RSAPublicKey) keyPair.getPublic();
    }

    public String getKeyId() {
        return keyId;
    }

    /**
     * Produces RFC 7517 compliant JWKS response.
     */
    public Map<String, Object> getJwks() {
        RSAPublicKey pub = getPublicKey();
        String n = Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getModulus().toByteArray());
        String e = Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getPublicExponent().toByteArray());

        Map<String, Object> jwk = Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", keyId,
                "n", n,
                "e", e
        );

        return Map.of("keys", List.of(jwk));
    }
}
