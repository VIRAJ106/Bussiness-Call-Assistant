package com.dayclose.web;

import java.util.UUID;

/**
 * Thread-local holder for the current request's tenant id.
 *
 * <p>Set by {@link TenantContextFilter} from the verified JWT. Every service-layer
 * method that writes data should call {@link #require()} to get the tenant id,
 * never accept it as a parameter from the API layer.
 */
public final class TenantContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String tenantId) {
        HOLDER.set(tenantId);
    }

    public static String get() {
        return HOLDER.get();
    }

    /**
     * Returns the tenant id or throws if not set (programming error or unauthenticated request).
     */
    public static UUID require() {
        String val = HOLDER.get();
        if (val == null || val.isBlank()) {
            throw new IllegalStateException("tenant_id not set in TenantContext — is the request authenticated?");
        }
        return UUID.fromString(val);
    }

    public static void clear() {
        HOLDER.remove();
    }
}
