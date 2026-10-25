package com.iotiq.partnererp.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A request that is well-formed but violates a business rule (e.g. paying out without PAN/bank details).
 * Defaults to 422; use {@link #conflict} for state conflicts (409).
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message) {
        this(code, message, HttpStatus.UNPROCESSABLE_CONTENT);
    }

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
