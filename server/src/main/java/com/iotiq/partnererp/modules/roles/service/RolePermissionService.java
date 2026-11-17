package com.iotiq.partnererp.modules.roles.service;

import com.iotiq.partnererp.common.exception.BusinessException;
import com.iotiq.partnererp.common.exception.ResourceNotFoundException;
import com.iotiq.partnererp.modules.audit.service.AuditActions;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.auth.dto.ModulePermissionDto;
import com.iotiq.partnererp.modules.auth.entity.DataScope;
import com.iotiq.partnererp.modules.auth.entity.Role;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.iotiq.partnererp.modules.auth.repository.RoleRepository;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import com.iotiq.partnererp.modules.auth.service.ModuleCatalog;
import com.iotiq.partnererp.modules.auth.service.PermissionService;
import com.iotiq.partnererp.modules.roles.dto.RolePermissionsResponse;
import com.iotiq.partnererp.modules.roles.dto.UpdateRolePermissionsRequest;
import com.iotiq.partnererp.security.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Role default permissions (role_permission_templates): the matrix new accounts and accounts moved to the role start
 * with. Needs Super Admin or ROLE_MANAGEMENT write access. The same validation as for users applies, so
 * a role default can't hold what the role may not be granted (e.g. User Management for USER). Existing accounts are
 * never changed by a template edit: their permissions are changed one by one, and audited one by one.
 */
@Service
public class RolePermissionService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final ModuleCatalog catalog;
    private final AuditService auditService;
    private final NamedParameterJdbcTemplate jdbc;
    private final EntityManager entityManager;

    public RolePermissionService(RoleRepository roleRepository, UserRepository userRepository,
                                 PermissionService permissionService, ModuleCatalog catalog, AuditService auditService,
                                 NamedParameterJdbcTemplate jdbc, EntityManager entityManager) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.catalog = catalog;
        this.auditService = auditService;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public RolePermissionsResponse get(UUID id, AuthenticatedUser actor) {
        Role role = load(id);
        return response(role, permissionService.templateRows(role), actor);
    }

    @Transactional
    public RolePermissionsResponse replace(UUID id, UpdateRolePermissionsRequest request, AuthenticatedUser actor) {
        if (!canEdit(actor)) {
            throw new AccessDeniedException("You are not allowed to change role permissions");
        }
        Role role = load(id);
        if (role.isSuperAdmin()) {
            throw new BusinessException("SUPER_ADMIN_PERMISSIONS_FIXED",
                    "A Super Admin always has full access; the role's permissions cannot be edited.");
        }
        if (request.version() != role.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Role.class, id);
        }
        RoleCode code = role.roleCode()
                .orElseThrow(() -> new BusinessException("ROLE_RETIRED", "This role is retired."));
        List<ModulePermissionDto> rows = permissionService.validateTemplate(request.modules(), code);
        List<ModulePermissionDto> before = permissionService.templateRows(role);
        Map<String, Object> oldValues = snapshot(before);
        Map<String, Object> newValues = snapshot(rows);
        if (!oldValues.equals(newValues)) {
            // Locks the role row and increments its version (failing if someone changed it since it was read), so
            // concurrent template edits can't interleave.
            entityManager.lock(role, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
            jdbc.update("DELETE FROM role_permission_templates WHERE role_id = :role",
                    new MapSqlParameterSource("role", role.getId()));
            for (ModulePermissionDto r : rows) {
                jdbc.update("""
                        INSERT INTO role_permission_templates
                               (role_id, module_id, can_write, can_delete, can_export, can_approve, data_scope)
                        VALUES (:role, :module, :write, :delete, :export, :approve, :scope)
                        """, new MapSqlParameterSource("role", role.getId())
                        .addValue("module", catalog.module(r.moduleCode()).orElseThrow().id())
                        .addValue("write", r.write()).addValue("delete", r.delete()).addValue("export", r.export())
                        .addValue("approve", r.approve())
                        .addValue("scope", (r.dataScope() == null ? DataScope.ALL : r.dataScope()).name()));
            }
            auditService.record(AuditActions.ROLE_PERMISSIONS_CHANGED, AuditActions.ENTITY_ROLE, role.getId(),
                    AuditService.values("code", role.getCode(), "modules", oldValues),
                    AuditService.values("code", role.getCode(), "modules", newValues));
        }
        entityManager.flush();
        return response(role, rows, actor);
    }

    private RolePermissionsResponse response(Role role, List<ModulePermissionDto> rows, AuthenticatedUser actor) {
        long users = 0;
        for (Object[] row : userRepository.countByRole()) {
            if (role.getId().equals(row[0])) {
                users = (Long) row[1];
            }
        }
        boolean superAdmin = role.isSuperAdmin();
        List<ModulePermissionDto> matrix = superAdmin
                ? catalog.modules().stream().map(def -> new ModulePermissionDto(def.code(), true, def.supportsWrite(),
                        def.supportsDelete(), def.supportsExport(), def.supportsApprove(),
                        def.supportsScope() ? DataScope.ALL : null)).toList()
                : permissionService.fullMatrix(rows);
        return new RolePermissionsResponse(role.getId(), role.getCode(), role.getDisplayName(), superAdmin,
                !superAdmin && canEdit(actor), matrix, users, role.getVersion());
    }

    private static boolean canEdit(AuthenticatedUser actor) {
        return actor.isSuperAdmin() || actor.permissions().canWrite("ROLE_MANAGEMENT");
    }

    private static Map<String, Object> snapshot(List<ModulePermissionDto> rows) {
        Map<String, Object> modules = new LinkedHashMap<>();
        rows.forEach(r -> modules.put(r.moduleCode(), AuditService.values("write", r.write(), "delete", r.delete(),
                "export", r.export(), "approve", r.approve(),
                "scope", r.dataScope() == null ? DataScope.ALL : r.dataScope())));
        return modules;
    }

    private Role load(UUID id) {
        return roleRepository.findByIdAndActiveTrue(id).orElseThrow(() -> new ResourceNotFoundException("Role", id));
    }
}
