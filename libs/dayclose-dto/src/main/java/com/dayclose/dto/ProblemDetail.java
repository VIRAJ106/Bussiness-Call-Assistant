package com.dayclose.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;

/**
 * RFC 7807 Problem Detail response (§4.4).
 *
 * <p>All API errors use this format with a stable machine-readable {@code code}
 * and a human-readable {@code detail}. The {@code traceId} links to the
 * OpenTelemetry trace for debugging.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(
        @JsonProperty("type") URI type,
        @JsonProperty("title") String title,
        @JsonProperty("status") int status,
        @JsonProperty("detail") String detail,
        @JsonProperty("instance") URI instance,
        @JsonProperty("code") String code,
        @JsonProperty("trace_id") String traceId
) {

    public static ProblemDetail of(int status, String code, String detail, String traceId) {
        return new ProblemDetail(
                URI.create("about:blank"),
                httpStatusTitle(status),
                status, detail, null, code, traceId);
    }

    private static String httpStatusTitle(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 422 -> "Unprocessable Entity";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 503 -> "Service Unavailable";
            default -> "Error";
        };
    }
}
