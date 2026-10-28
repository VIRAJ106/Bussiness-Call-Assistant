package com.dayclose.auth.api;

import com.dayclose.auth.api.dto.ClientCredentialsRequest;
import com.dayclose.auth.api.dto.LoginRequest;
import com.dayclose.auth.api.dto.RefreshRequest;
import com.dayclose.auth.api.dto.RegisterRequest;
import com.dayclose.auth.api.dto.TokenResponse;
import com.dayclose.auth.config.JwtKeyProvider;
import com.dayclose.auth.domain.ServiceCredential;
import com.dayclose.auth.domain.Tenant;
import com.dayclose.auth.domain.User;
import com.dayclose.auth.infra.RefreshTokenRepository;
import com.dayclose.auth.infra.ServiceCredentialRepository;
import com.dayclose.auth.infra.TenantRepository;
import com.dayclose.auth.infra.UserRepository;
import com.dayclose.auth.service.PasswordService;
import com.dayclose.auth.service.TokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final ServiceCredentialRepository serviceCredentialRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordService passwordService;
    private final TokenService tokenService;
    private final JwtKeyProvider keyProvider;

    public AuthController(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            ServiceCredentialRepository serviceCredentialRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordService passwordService,
            TokenService tokenService,
            JwtKeyProvider keyProvider
    ) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.serviceCredentialRepository = serviceCredentialRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
        this.keyProvider = keyProvider;
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalArgumentException("User with email already exists");
        }

        Tenant tenant = tenantRepository.create(request.tenantName());
        String role = (request.role() != null && !request.role().isBlank()) ? request.role() : "OWNER";
        String passwordHash = passwordService.hash(request.password());
        User user = userRepository.create(tenant.id(), request.email(), passwordHash, role);

        TokenService.TokenPair pair = tokenService.issueInitialTokens(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TokenResponse.of(pair.accessToken(), pair.refreshToken(), pair.expiresIn()));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordService.verify(user.passwordHash(), request.password())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        TokenService.TokenPair pair = tokenService.issueInitialTokens(user);
        return ResponseEntity.ok(TokenResponse.of(pair.accessToken(), pair.refreshToken(), pair.expiresIn()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        String tokenHash = TokenService.hashToken(request.refreshToken());
        var stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        User user = userRepository.findById(stored.userId())
                .orElseThrow(() -> new IllegalStateException("User not found for token"));

        TokenService.TokenPair pair = tokenService.rotateRefreshToken(request.refreshToken(), user);
        return ResponseEntity.ok(TokenResponse.of(pair.accessToken(), pair.refreshToken(), pair.expiresIn()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        tokenService.revokeToken(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> serviceToken(@Valid @RequestBody ClientCredentialsRequest request) {
        ServiceCredential credential = serviceCredentialRepository.findByClientId(request.clientId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid client credentials"));

        if (!passwordService.verify(credential.secretHash(), request.clientSecret())) {
            throw new IllegalArgumentException("Invalid client credentials");
        }

        String token = tokenService.createServiceToken(credential.clientId(), credential.tenantId(), credential.scopes());
        return ResponseEntity.ok(TokenResponse.of(token, 900));
    }

    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok(keyProvider.getJwks());
    }
}
