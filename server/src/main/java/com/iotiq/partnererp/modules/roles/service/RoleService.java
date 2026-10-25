package com.iotiq.partnererp.modules.roles.service;

import com.iotiq.partnererp.common.exception.BusinessException;
import com.iotiq.partnererp.common.exception.ResourceNotFoundException;
import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.common.response.ApiResponse.FieldError;
import com.iotiq.partnererp.modules.audit.service.AuditActions;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.auth.entity.Role;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.iotiq.partnererp.modules.auth.repository.RoleRepository;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import com.iotiq.partnererp.modules.roles.dto.RoleResponse;
import com.iotiq.partnererp.modules.roles.dto.UpdateRoleRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The three active system roles. Display names and descriptions are editable (Super Admin only, enforced
 * by the controller); codes are immutable here, in the entity mapping and by a database trigger.
 */
@Service
public class RoleService {

    private static final int MIN_NAME = 2;
    private static final int MAX_NAME = 60;
    private static final int MAX_DESCRIPTION = 255;

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public RoleService(RoleRepository roleRepository, UserRepository userRepository, AuditService auditService) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /** Active roles in hierarchy order (SUPER_ADMIN, ADMIN, USER). */
    @Transactional(readOnly = true)
    public List<RoleResponse> list(boolean includeUserCounts) {
        Map<UUID, Long> counts = includeUserCounts ? userCounts() : Map.of();
        return roleRepository.findAllByActiveTrue().stream()
                .sorted(Comparator.comparingInt(RoleService::rank))
                .map(r -> RoleResponse.of(r, includeUserCounts ? counts.getOrDefault(r.getId(), 0L) : null))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse get(UUID id, boolean includeUserCount) {
        Role role = load(id);
        return RoleResponse.of(role, includeUserCount ? userCounts().getOrDefault(id, 0L) : null);
    }

    @Transactional
    public RoleResponse update(UUID id, UpdateRoleRequest request) {
        Role role = load(id);
        List<FieldError> errors = new ArrayList<>();
        if (request.code() != null && !request.code().equals(role.getCode())) {
            errors.add(new FieldError("code", "Role codes cannot be changed."));
        }
        if (request.id() != null && !request.id().equals(role.getId())) {
            errors.add(new FieldError("id", "Role ids cannot be changed."));
        }
        String name = request.displayName() == null ? null : request.displayName().trim().replaceAll("\\s+", " ");
        if (name != null && (name.length() < MIN_NAME || name.length() > MAX_NAME)) {
            errors.add(new FieldError("displayName", "Role name must be " + MIN_NAME + " to " + MAX_NAME + " characters."));
        }
        String description = request.description() == null ? null : request.description().trim();
        if (description != null && description.length() > MAX_DESCRIPTION) {
            errors.add(new FieldError("description", "Description must be at most " + MAX_DESCRIPTION + " characters."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        if (request.version() != role.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Role.class, id);
        }
        if (name != null && roleRepository.existsActiveDisplayName(name, role.getId())) {
            throw BusinessException.conflict("DUPLICATE_ROLE_NAME", "Another role is already named \"" + name + "\".");
        }

        Map<String, Object> before = AuditService.values("displayName", role.getDisplayName(),
                "description", role.getDescription());
        if (name != null) {
            role.setDisplayName(name);
        }
        if (description != null) {
            role.setDescription(description.isEmpty() ? null : description);
        }
        Map<String, Object> after = AuditService.values("displayName", role.getDisplayName(),
                "description", role.getDescription());
        if (!Objects.equals(before, after)) {
            roleRepository.saveAndFlush(role);
            auditService.record(AuditActions.ROLE_UPDATED, AuditActions.ENTITY_ROLE, role.getId(),
                    withCode(before, role), withCode(after, role));
        }
        return RoleResponse.of(role, userCounts().getOrDefault(id, 0L));
    }

    private Role load(UUID id) {
        return roleRepository.findByIdAndActiveTrue(id).orElseThrow(() -> new ResourceNotFoundException("Role", id));
    }

    private Map<UUID, Long> userCounts() {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : userRepository.countByRole()) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    private static Map<String, Object> withCode(Map<String, Object> values, Role role) {
        Map<String, Object> copy = AuditService.values("code", role.getCode());
        copy.putAll(values);
        return copy;
    }

    private static int rank(Role role) {
        return role.roleCode().map(RoleCode::ordinal).orElse(Integer.MAX_VALUE);
    }
}
