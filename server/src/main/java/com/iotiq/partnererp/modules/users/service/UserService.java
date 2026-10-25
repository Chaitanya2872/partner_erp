package com.iotiq.partnererp.modules.users.service;

import com.iotiq.partnererp.common.exception.BusinessException;
import com.iotiq.partnererp.common.exception.ResourceNotFoundException;
import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.modules.audit.service.AuditActions;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.auth.dto.UpdateUserPermissionsRequest;
import com.iotiq.partnererp.modules.auth.dto.UserPermissionsResponse;
import com.iotiq.partnererp.modules.auth.entity.Role;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.iotiq.partnererp.modules.auth.entity.User;
import com.iotiq.partnererp.modules.auth.entity.UserStatus;
import com.iotiq.partnererp.modules.auth.repository.RoleRepository;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import com.iotiq.partnererp.modules.auth.service.PasswordPolicy;
import com.iotiq.partnererp.modules.auth.service.PermissionService;
import com.iotiq.partnererp.modules.auth.service.SessionService;
import com.iotiq.partnererp.modules.users.dto.CreateUserRequest;
import com.iotiq.partnererp.modules.users.dto.UpdateUserRequest;
import com.iotiq.partnererp.modules.users.dto.UpdateUserStatusRequest;
import com.iotiq.partnererp.modules.users.dto.UserResponse;
import com.iotiq.partnererp.security.AuthenticatedUser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * User account management.
 *
 * <p>Rules: a non-Super-Admin may only create and manage USER accounts and may not change roles; nobody
 * changes their own role or status; a role change resets the account's permissions to the new role's
 * template; the last active Super Admin cannot be deactivated or demoted.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionService permissionService;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                       PermissionService permissionService, SessionService sessionService,
                       PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy, AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionService = permissionService;
        this.sessionService = sessionService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> search(String query, UUID roleId, UserStatus status, Pageable pageable) {
        Specification<User> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%")
                        .replace("_", "\\_") + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("fullName")), like, '\\'),
                        cb.like(cb.lower(root.get("email")), like, '\\')));
            }
            if (roleId != null) {
                predicates.add(cb.equal(root.get("role").get("id"), roleId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (cq != null && Long.class != cq.getResultType()) {
                root.fetch("role");
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return userRepository.findAll(spec, pageable).map(UserResponse::of);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return UserResponse.of(load(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, AuthenticatedUser actor) {
        Role role = activeRole(request.roleId());
        if (!actor.isSuperAdmin() && !RoleCode.USER.name().equals(role.getCode())) {
            throw forbidden("You can only create accounts with the " + displayNameOf(RoleCode.USER) + " role.");
        }
        String email = normaliseEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw BusinessException.conflict("EMAIL_IN_USE", "Another account already uses this email address.");
        }
        passwordPolicy.validate("temporaryPassword", request.temporaryPassword(), email);

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.temporaryPassword()));
        user.setRole(role);
        user.setMustChangePassword(true);
        userRepository.saveAndFlush(user);
        permissionService.applyRoleTemplate(user, actor.id());

        auditService.record(AuditActions.USER_CREATED, AuditActions.ENTITY_USER, user.getId(), null,
                AuditService.values("fullName", user.getFullName(), "email", email, "role", role.getCode(),
                        "permissions", permissionService.snapshot(permissionService.resolve(user))));
        return UserResponse.of(user);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request, AuthenticatedUser actor) {
        User user = userRepository.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        checkVersion(user, request.version());
        assertCanManage(actor, user);
        Role newRole = activeRole(request.roleId());
        boolean roleChanged = !newRole.getId().equals(user.getRole().getId());
        if (roleChanged) {
            if (!actor.isSuperAdmin()) {
                throw forbidden("Only a Super Admin can change roles.");
            }
            if (actor.id().equals(user.getId())) {
                throw forbidden("You cannot change your own role.");
            }
            if (user.getRole().isSuperAdmin() && user.getStatus() == UserStatus.ACTIVE) {
                assertNotLastActiveSuperAdmin(user);
            }
        }
        String email = normaliseEmail(request.email());
        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmailIgnoreCase(email)) {
            throw BusinessException.conflict("EMAIL_IN_USE", "Another account already uses this email address.");
        }

        Map<String, Object> before = AuditService.values("fullName", user.getFullName(), "email", user.getEmail(),
                "role", user.getRole().getCode());
        String oldRole = user.getRole().getCode();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        if (roleChanged) {
            Map<String, Object> oldPermissions = permissionService.snapshot(permissionService.resolve(user));
            user.setRole(newRole);
            userRepository.saveAndFlush(user);
            permissionService.applyRoleTemplate(user, actor.id());
            auditService.record(AuditActions.USER_ROLE_CHANGED, AuditActions.ENTITY_USER, user.getId(),
                    AuditService.values("role", oldRole, "permissions", oldPermissions),
                    AuditService.values("role", newRole.getCode(),
                            "permissions", permissionService.snapshot(permissionService.resolve(user))));
        }
        userRepository.saveAndFlush(user);
        Map<String, Object> after = AuditService.values("fullName", user.getFullName(), "email", user.getEmail(),
                "role", user.getRole().getCode());
        if (!before.equals(after)) {
            auditService.record(AuditActions.USER_UPDATED, AuditActions.ENTITY_USER, user.getId(), before, after);
        }
        return UserResponse.of(user);
    }

    @Transactional
    public UserResponse updateStatus(UUID id, UpdateUserStatusRequest request, AuthenticatedUser actor) {
        if (request.status() == UserStatus.LOCKED) {
            throw ValidationException.of("status", "Status must be ACTIVE or DISABLED.");
        }
        User user = userRepository.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        checkVersion(user, request.version());
        if (actor.id().equals(user.getId())) {
            throw forbidden("You cannot change the status of your own account.");
        }
        assertCanManage(actor, user);
        UserStatus old = user.getStatus();
        if (old == request.status()) {
            return UserResponse.of(user);
        }
        if (request.status() == UserStatus.DISABLED) {
            if (user.getRole().isSuperAdmin()) {
                assertNotLastActiveSuperAdmin(user);
            }
            sessionService.revokeAll(user);
        } else {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
        }
        user.setStatus(request.status());
        userRepository.saveAndFlush(user);
        auditService.record(AuditActions.USER_STATUS_CHANGED, AuditActions.ENTITY_USER, user.getId(),
                AuditService.values("status", old), AuditService.values("status", user.getStatus()));
        return UserResponse.of(user);
    }

    @Transactional(readOnly = true)
    public UserPermissionsResponse permissions(UUID id, AuthenticatedUser actor) {
        return permissionService.view(load(id), actor);
    }

    @Transactional
    public UserPermissionsResponse replacePermissions(UUID id, UpdateUserPermissionsRequest request,
                                                      AuthenticatedUser actor) {
        return permissionService.replace(id, request, actor);
    }

    private User load(UUID id) {
        return userRepository.findWithRoleById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private Role activeRole(UUID roleId) {
        return roleRepository.findByIdAndActiveTrue(roleId)
                .orElseThrow(() -> ValidationException.of("roleId", "Select an active role."));
    }

    /** Non-Super-Admins manage USER accounts only. */
    private void assertCanManage(AuthenticatedUser actor, User target) {
        if (!actor.isSuperAdmin() && !RoleCode.USER.name().equals(target.getRole().getCode())) {
            throw forbidden("You can only manage accounts with the " + displayNameOf(RoleCode.USER) + " role.");
        }
    }

    private void assertNotLastActiveSuperAdmin(User target) {
        List<User> active = userRepository.lockSuperAdmins(UserStatus.ACTIVE);
        boolean targetIsActive = active.stream().anyMatch(u -> u.getId().equals(target.getId()));
        if (targetIsActive && active.size() <= 1) {
            throw BusinessException.conflict("LAST_SUPER_ADMIN",
                    "This is the last active Super Admin. Activate or appoint another Super Admin first.");
        }
    }

    private static void checkVersion(User user, Long version) {
        if (version == null || version != user.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(User.class, user.getId());
        }
    }

    private String displayNameOf(RoleCode code) {
        return roleRepository.findByCode(code.name()).map(Role::getDisplayName).orElse(code.name());
    }

    private static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static BusinessException forbidden(String message) {
        return new BusinessException("FORBIDDEN_OPERATION", message, HttpStatus.FORBIDDEN);
    }
}
