package com.dayclose.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Standard paginated response wrapper.
 *
 * @param data the page of results
 * @param page pagination metadata
 */
public record PagedResponse<T>(
        @JsonProperty("data") List<T> data,
        @JsonProperty("page") PageMeta page
) {
}
