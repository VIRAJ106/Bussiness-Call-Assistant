package com.dayclose.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.math.BigInteger;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates JWT on incoming API requests and propagates verified identity headers (ADR-0002).
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, PublicKey> keyCache = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Bypass auth service and actuator endpoints
        if (path.startsWith("/v1/auth/") || path.startsWith("/actuator/")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return onError(exchange, HttpStatus.UNAUTHORIZED, "missing_token", "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7).trim();

        try {
            // Parse untrusted header to get kid
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return onError(exchange, HttpStatus.UNAUTHORIZED, "invalid_token", "Malformed JWT");
            }

            Map<?, ?> header = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Map.class);
            String kid = (String) header.get("kid");
            PublicKey key = getPublicKey(kid);

            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String tenantId = claims.get("tenant_id", String.class);
            String userId = claims.getSubject();
            Object rolesObj = claims.get("roles");
            String roles = rolesObj != null ? rolesObj.toString() : "";

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-Tenant-Id", tenantId != null ? tenantId : "")
                    .header("X-User-Id", userId != null ? userId : "")
                    .header("X-User-Roles", roles)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (JwtException | JsonProcessingException e) {
            return onError(exchange, HttpStatus.UNAUTHORIZED, "invalid_token", "JWT signature or format invalid: " + e.getMessage());
        } catch (Exception e) {
            return onError(exchange, HttpStatus.UNAUTHORIZED, "auth_error", "Authentication error: " + e.getMessage());
        }
    }

    private PublicKey getPublicKey(String kid) throws Exception {
        if (kid != null && keyCache.containsKey(kid)) {
            return keyCache.get(kid);
        }

        // Fetch JWKS from auth-service
        String jwksUrl = System.getenv().getOrDefault("AUTH_JWKS_URL", "http://localhost:8081/v1/auth/.well-known/jwks.json");
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(jwksUrl)).GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() == 200) {
            Map<?, ?> jwks = objectMapper.readValue(resp.body(), Map.class);
            List<?> keys = (List<?>) jwks.get("keys");
            for (Object k : keys) {
                Map<?, ?> keyMap = (Map<?, ?>) k;
                String kId = (String) keyMap.get("kid");
                String nStr = (String) keyMap.get("n");
                String eStr = (String) keyMap.get("e");

                BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(nStr));
                BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(eStr));
                RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
                KeyFactory factory = KeyFactory.getInstance("RSA");
                PublicKey pubKey = factory.generatePublic(spec);

                if (kId != null) {
                    keyCache.put(kId, pubKey);
                }
            }
        }

        if (kid != null && keyCache.containsKey(kid)) {
            return keyCache.get(kid);
        }

        throw new IllegalStateException("Public key for kid '" + kid + "' not found in JWKS");
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        Map<String, Object> problem = Map.of(
                "type", "about:blank",
                "title", status.getReasonPhrase(),
                "status", status.value(),
                "code", code,
                "detail", message
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(problem);
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":" + status.value() + ",\"detail\":\"" + message + "\"}").getBytes();
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
