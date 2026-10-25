package com.iotiq.partnererp.common.exception;

import com.iotiq.partnererp.common.response.ApiResponse.FieldError;

import java.util.List;

/**
 * Field-level validation that cannot be expressed with Bean Validation annotations (cross-field rules,
 * password policy). Rendered as 400 VALIDATION_ERROR with {@code fieldErrors}.
 */
public class ValidationException extends RuntimeException {

    private final transient List<FieldError> fieldErrors;

    public ValidationException(List<FieldError> fieldErrors) {
        super("Validation failed");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public static ValidationException of(String field, String message) {
        return new ValidationException(List.of(new FieldError(field, message)));
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
