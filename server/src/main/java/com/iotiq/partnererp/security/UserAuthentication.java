package com.iotiq.partnererp.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.stream.Stream;

/**
 * Authentication created from a valid access token. Authorities are the user's effective permissions
 * plus {@code ROLE_<code>}.
 */
public class UserAuthentication extends AbstractAuthenticationToken {

    private final AuthenticatedUser user;
    private final transient Jwt token;

    public UserAuthentication(AuthenticatedUser user, Jwt token) {
        super(Stream.concat(user.permissions().authorities().stream(), Stream.of("ROLE_" + user.roleCode()))
                .map(SimpleGrantedAuthority::new)
                .toList());
        this.user = user;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return user;
    }

    @Override
    public Jwt getCredentials() {
        return token;
    }

    @Override
    public String getName() {
        return user.id().toString();
    }
}
