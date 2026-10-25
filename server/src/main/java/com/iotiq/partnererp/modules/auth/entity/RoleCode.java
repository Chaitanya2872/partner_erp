package com.iotiq.partnererp.modules.auth.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * Immutable codes of the active system roles (V12). Retired proposal roles have no constant.
 */
public enum RoleCode {

    SUPER_ADMIN,
    ADMIN,
    USER;

    public static Optional<RoleCode> from(String code) {
        return Arrays.stream(values()).filter(c -> c.name().equals(code)).findFirst();
    }
}
