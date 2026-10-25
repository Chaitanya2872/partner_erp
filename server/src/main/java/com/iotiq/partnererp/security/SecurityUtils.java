package com.iotiq.partnererp.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * Access to the signed-in user for services that need the actor (audit, self-edit rules).
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof UserAuthentication userAuthentication) {
            return Optional.of(userAuthentication.getPrincipal());
        }
        return Optional.empty();
    }

    public static AuthenticatedUser requireCurrentUser() {
        return currentUser().orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Not signed in"));
    }

    public static Optional<UUID> currentUserId() {
        return currentUser().map(AuthenticatedUser::id);
    }
}
