package com.dayclose.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cursor-based pagination metadata (§4.4).
 *
 * <p>All list endpoints return this alongside the data. Max limit is 100.
 *
 * @param cursor the opaque cursor for the next page (null if no more pages)
 * @param limit  the requested page size
 * @param hasMore whether more results exist beyond the current page
 */
public record PageMeta(
        @JsonProperty("cursor") String cursor,
        @JsonProperty("limit") int limit,
        @JsonProperty("has_more") boolean hasMore
) {

    public static final int MAX_LIMIT = 100;
    public static final int DEFAULT_LIMIT = 20;

    public static int clampLimit(Integer requested) {
        if (requested == null || requested <= 0) return DEFAULT_LIMIT;
        return Math.min(requested, MAX_LIMIT);
    }
}
