package com.iotiq.partnererp.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standard envelope for every JSON response (see docs/architecture/API_DESIGN.md §1).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, Object meta, ApiError error, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data, Object meta) {
        return new ApiResponse<>(true, data, meta, null, Instant.now());
    }

    public static ApiResponse<Void> failure(ApiError error) {
        return new ApiResponse<>(false, null, null, error, Instant.now());
    }

    /** Problem-details style error body (RFC 9457 field names plus an application code). */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ApiError(String type, String title, int status, String code, String detail,
                           List<FieldError> fieldErrors, String requestId) {
    }

    public record FieldError(String field, String message) {
    }
}
