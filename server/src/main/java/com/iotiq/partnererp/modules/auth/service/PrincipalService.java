package com.iotiq.partnererp.modules.auth.service;

import com.iotiq.partnererp.modules.auth.entity.User;
import com.iotiq.partnererp.modules.auth.entity.UserStatus;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import com.iotiq.partnererp.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Builds the request principal from the database, so deactivation, session revocation and permission
 * changes take effect on the next request.
 */
@Service
public class PrincipalService {

    private final UserRepository userRepository;
    private final PermissionService permissionService;

    public PrincipalService(UserRepository userRepository, PermissionService permissionService) {
        this.userRepository = userRepository;
        this.permissionService = permissionService;
    }

    /** The principal for a token, or empty if the user is gone, inactive or the session was revoked. */
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> loadActive(UUID userId, int sessionVersion) {
        return userRepository.findWithRoleById(userId)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .filter(u -> u.getSessionVersion() == sessionVersion)
                .filter(u -> u.getRole().isActive())
                .map(this::toPrincipal);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser toPrincipal(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getFullName(), user.getRole().getId(),
                user.getRole().getCode(), user.getRole().getDisplayName(), user.isMustChangePassword(),
                permissionService.resolve(user));
    }
}
