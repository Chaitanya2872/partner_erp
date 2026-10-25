package com.iotiq.partnererp.security;

import com.iotiq.partnererp.modules.auth.service.PrincipalService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Turns a verified access token into a {@link UserAuthentication}. A token whose user is no longer active,
 * or whose session version is outdated (password change, deactivation), is rejected with 401.
 */
@Component
public class UserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final PrincipalService principalService;

    public UserJwtAuthenticationConverter(PrincipalService principalService) {
        this.principalService = principalService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId;
        Number sessionVersion;
        try {
            userId = UUID.fromString(jwt.getSubject());
            sessionVersion = jwt.getClaim(JwtService.SESSION_VERSION_CLAIM);
        } catch (IllegalArgumentException | ClassCastException e) {
            throw new InvalidBearerTokenException("Malformed access token");
        }
        if (sessionVersion == null) {
            throw new InvalidBearerTokenException("Malformed access token");
        }
        AuthenticatedUser user = principalService.loadActive(userId, sessionVersion.intValue())
                .orElseThrow(() -> new InvalidBearerTokenException("Session is no longer valid"));
        return new UserAuthentication(user, jwt);
    }
}
