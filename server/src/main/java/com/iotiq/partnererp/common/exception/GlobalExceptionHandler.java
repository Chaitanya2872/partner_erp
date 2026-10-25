package com.iotiq.partnererp.common.exception;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.common.response.ApiResponse.ApiError;
import com.iotiq.partnererp.common.response.ApiResponse.FieldError;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Stream;

/**
 * Maps exceptions to the standard error envelope. Messages for 5xx are never exposed to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        List<FieldError> global = ex.getBindingResult().getGlobalErrors().stream()
                .map(e -> new FieldError(e.getObjectName(), e.getDefaultMessage()))
                .toList();
        return validation(concat(fields, global));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        List<FieldError> fields = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new FieldError(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return validation(fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldError> fields = ex.getConstraintViolations().stream()
                .map(v -> new FieldError(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return validation(fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed request",
                "The request body could not be read. Check the JSON syntax and field types.", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return validation(List.of(new FieldError(ex.getName(), "Invalid value.")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return validation(List.of(new FieldError(ex.getParameterName(), "This parameter is required.")));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found", ex.getMessage(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found", "No endpoint matches this path.", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed",
                "HTTP method " + ex.getMethod() + " is not supported for this path.", null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Unsupported media type",
                "Use Content-Type: application/json.", null);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        String title = switch (ex.getStatus()) {
            case CONFLICT -> "Conflict";
            case UNPROCESSABLE_CONTENT -> "Business rule violated";
            default -> ex.getStatus().getReasonPhrase();
        };
        return build(ex.getStatus(), ex.getCode(), title, ex.getMessage(), null);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(ValidationException ex) {
        return validation(ex.getFieldErrors());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "Conflict",
                "This record was changed by someone else. Reload it and try again.", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException ex) {
        // The driver message can echo column values (PostgreSQL: "Failing row contains (...)"), which may include
        // PAN, GSTIN or bank details. Log only the constraint name and SQL state.
        String constraint = ex.getCause() instanceof org.hibernate.exception.ConstraintViolationException cve
                ? cve.getConstraintName() : null;
        String sqlState = ex.getMostSpecificCause() instanceof java.sql.SQLException sql ? sql.getSQLState() : null;
        log.warn("Data integrity violation: constraint={}, sqlState={}", constraint, sqlState);
        return build(HttpStatus.CONFLICT, "DATA_CONFLICT", "Conflict",
                "The change conflicts with existing data (for example a duplicate value).", null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Unauthenticated",
                "Authentication is required.", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Forbidden",
                "You do not have permission to perform this action.", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error",
                "An unexpected error occurred. Quote the request id when reporting it.", null);
    }

    public static ApiError error(HttpStatus status, String code, String title, String detail,
                                 List<FieldError> fieldErrors) {
        return new ApiError("about:blank", title, status.value(), code, detail, fieldErrors,
                MDC.get(AppConstants.REQUEST_ID_MDC_KEY));
    }

    private ResponseEntity<ApiResponse<Void>> validation(List<FieldError> fields) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed",
                "One or more fields are invalid.", fields);
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code, String title, String detail,
                                                    List<FieldError> fields) {
        return ResponseEntity.status(status).body(ApiResponse.failure(error(status, code, title, detail, fields)));
    }

    private static List<FieldError> concat(List<FieldError> a, List<FieldError> b) {
        if (b.isEmpty()) {
            return a;
        }
        return Stream.concat(a.stream(), b.stream()).toList();
    }

    private static String lastNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }
}
