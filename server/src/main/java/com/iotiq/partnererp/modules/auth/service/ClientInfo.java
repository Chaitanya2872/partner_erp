package com.iotiq.partnererp.modules.auth.service;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Request metadata stored with refresh tokens.
 */
public record ClientInfo(String userAgent, String ipAddress) {

    public static ClientInfo from(HttpServletRequest request) {
        return new ClientInfo(truncate(request.getHeader("User-Agent"), 255), truncate(request.getRemoteAddr(), 45));
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
