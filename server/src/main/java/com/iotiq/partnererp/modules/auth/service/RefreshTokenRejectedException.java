package com.iotiq.partnererp.modules.auth.service;

/**
 * The presented refresh token is unknown, expired, revoked or belongs to an inactive account. Raised
 * without rolling back, so a reuse-triggered family revocation is kept.
 */
public class RefreshTokenRejectedException extends RuntimeException {

    public RefreshTokenRejectedException(String message) {
        super(message);
    }
}
