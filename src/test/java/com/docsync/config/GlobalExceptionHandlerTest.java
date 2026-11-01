package com.docsync.config;

import com.docsync.common.dto.ApiErrorResponse;
import com.docsync.common.exception.AccessDeniedException;
import com.docsync.common.exception.DuplicateResourceException;
import com.docsync.common.exception.ExternalServiceException;
import com.docsync.common.exception.ResourceNotFoundException;
import com.docsync.common.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GlobalExceptionHandler.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleResourceNotFound_returns404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Repository", "123");

        ResponseEntity<ApiErrorResponse> response = handler.handleResourceNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("RESOURCE_NOT_FOUND", response.getBody().code());
        assertTrue(response.getBody().message().contains("Repository"));
        assertTrue(response.getBody().message().contains("123"));
    }

    @Test
    void handleAccessDenied_returns403() {
        AccessDeniedException ex = new AccessDeniedException("Repository", "456");

        ResponseEntity<ApiErrorResponse> response = handler.handleAccessDenied(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ACCESS_DENIED", response.getBody().code());
    }

    @Test
    void handleValidation_returns422() {
        ValidationException ex = new ValidationException("name", "must not be blank");

        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_ERROR", response.getBody().code());
    }

    @Test
    void handleRateLimited_returns503WithRetryAfter() {
        java.time.Instant retryAt = java.time.Instant.now().plusSeconds(30);
        com.docsync.github.RateLimitedException ex =
                new com.docsync.github.RateLimitedException(retryAt, java.time.Duration.ofSeconds(30));

        ResponseEntity<ApiErrorResponse> response = handler.handleRateLimited(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ERR_GITHUB_RATE_LIMITED", response.getBody().code());
        assertNotNull(response.getHeaders().getFirst("Retry-After"));
        assertTrue(Long.parseLong(response.getHeaders().getFirst("Retry-After")) >= 1);
    }

    @Test
    void handleDuplicate_returns409() {
        DuplicateResourceException ex = new DuplicateResourceException("Repository", "owner/repo");

        ResponseEntity<ApiErrorResponse> response = handler.handleDuplicate(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DUPLICATE_RESOURCE", response.getBody().code());
    }

    @Test
    void handleExternalService_returns502_withoutInternalDetails() {
        ExternalServiceException ex = new ExternalServiceException("GitHub",
                "rate limit exceeded - token: ghp_secret123");

        ResponseEntity<ApiErrorResponse> response = handler.handleExternalService(ex);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("EXTERNAL_SERVICE_ERROR", response.getBody().code());
        // Must NOT expose the internal error message with secrets
        assertFalse(response.getBody().message().contains("ghp_secret123"));
        assertFalse(response.getBody().message().contains("rate limit"));
    }

    @Test
    void apiErrorResponse_includesTimestamp() {
        ApiErrorResponse error = new ApiErrorResponse("TEST", "test message");

        assertNotNull(error.timestamp());
        assertEquals("TEST", error.code());
        assertEquals("test message", error.message());
    }

    @Test
    void apiErrorResponse_fieldError_containsDetails() {
        ApiErrorResponse.FieldError fieldError = new ApiErrorResponse.FieldError(
                "email", "must be valid", "not-an-email");

        assertEquals("email", fieldError.field());
        assertEquals("must be valid", fieldError.message());
        assertEquals("not-an-email", fieldError.rejectedValue());
    }
}
