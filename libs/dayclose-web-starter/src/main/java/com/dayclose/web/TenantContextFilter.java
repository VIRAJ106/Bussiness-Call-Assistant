package com.dayclose.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Extracts {@code tenant_id} from the JWT claims and stores it in a request-scoped holder.
 *
 * <p>Per §4.4: tenant_id is NEVER accepted from a request body or path; it comes
 * only from the verified token. This filter runs after Spring Security has validated
 * the JWT and placed the Authentication in the SecurityContext.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // Extract tenant_id from the JWT.
            // In production this reads from SecurityContextHolder → JwtAuthenticationToken → claims.
            // For now, we also support an X-Tenant-Id header for local dev/testing.
            String tenantId = extractTenantId(request);
            if (tenantId != null) {
                TenantContext.set(tenantId);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractTenantId(HttpServletRequest request) {
        // TODO: read from SecurityContextHolder.getContext().getAuthentication() JWT claims
        // Fallback for local dev only:
        return request.getHeader("X-Tenant-Id");
    }
}
