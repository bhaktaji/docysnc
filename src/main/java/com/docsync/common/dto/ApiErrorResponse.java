package com.docsync.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standard API error response format.
 *
 * <p>All error responses from the DocSync API use this consistent structure.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String code,
        String message,
        List<FieldError> details,
        String traceId,
        Instant timestamp
) {

    public ApiErrorResponse(String code, String message) {
        this(code, message, null, null, Instant.now());
    }

    public ApiErrorResponse(String code, String message, String traceId) {
        this(code, message, null, traceId, Instant.now());
    }

    public ApiErrorResponse(String code, String message, List<FieldError> details, String traceId) {
        this(code, message, details, traceId, Instant.now());
    }

    /**
     * Represents a single field-level validation error.
     */
    public record FieldError(
            String field,
            String message,
            Object rejectedValue
    ) {}
}
