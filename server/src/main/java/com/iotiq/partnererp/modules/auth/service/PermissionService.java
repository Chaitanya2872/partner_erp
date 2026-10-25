package com.iotiq.partnererp.modules.auth.service;

import com.iotiq.partnererp.common.exception.BusinessException;
import com.iotiq.partnererp.common.exception.ResourceNotFoundException;
import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.common.response.ApiResponse.FieldError;
import com.iotiq.partnererp.modules.audit.service.AuditActions;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.auth.dto.ModulePermissionDto;
import com.iotiq.partnererp.modules.auth.dto.RoleRef;
import com.iotiq.partnererp.modules.auth.dto.UpdateUserPermissionsRequest;
import com.iotiq.partnererp.modules.auth.dto.UserPermissionsResponse;
import com.iotiq.partnererp.modules.auth.entity.AppModule;
import com.iotiq.partnererp.modules.auth.entity.DataScope;
import com.iotiq.partnererp.modules.auth.entity.ModuleCode;
import com.iotiq.partnererp.modules.auth.entity.Permission;
import com.iotiq.partnererp.modules.auth.entity.RoleCode;
import com.iotiq.partnererp.modules.auth.entity.RolePermissionTemplate;
import com.iotiq.partnererp.modules.auth.entity.User;
import com.iotiq.partnererp.modules.auth.entity.UserActionPermission;
import com.iotiq.partnererp.modules.auth.entity.UserModulePermission;
import com.iotiq.partnererp.modules.auth.repository.RolePermissionTemplateRepository;
import com.iotiq.partnererp.modules.auth.repository.UserActionPermissionRepository;
import com.iotiq.partnererp.modules.auth.repository.UserModulePermissionRepository;
import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import com.iotiq.partnererp.modules.auth.service.ModuleCatalog.ActionDef;
import com.iotiq.partnererp.modules.auth.service.ModuleCatalog.ModuleDef;
import com.iotiq.partnererp.security.AuthenticatedUser;
import com.iotiq.partnererp.security.EffectivePermissions;
import com.iotiq.partnererp.security.ModuleAccess;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Resolves effective permissions and maintains the per-user permission matrix.
 *
 * <p>Rules (no privilege escalation):
 * <ul>
 *   <li>SUPER_ADMIN access is implicit and complete; it is never stored and cannot be edited.</li>
 *   <li>Only a Super Admin changes permissions, never their own (delegation is not enabled in this version).</li>
 *   <li>Super-Admin-only modules/actions (Role Management, MANAGE_PERMISSIONS) cannot be granted.</li>
 *   <li>USER accounts cannot hold User Management.</li>
 *   <li>Write needs Read; Delete needs Write; flags a module does not support are rejected; actions need
 *       Read on their module; data scope applies only to modules that support it.</li>
 * </ul>
 */
@Service
public class PermissionService {

    private final ModuleCatalog catalog;
    private final UserRepository userRepository;
    private final UserModulePermissionRepository modulePermissionRepository;
    private final UserActionPermissionRepository actionPermissionRepository;
    private final RolePermissionTemplateRepository templateRepository;
    private final AuditService auditService;
    private final EntityManager entityManager;

    public PermissionService(ModuleCatalog catalog, UserRepository userRepository,
                             UserModulePermissionRepository modulePermissionRepository,
                             UserActionPermissionRepository actionPermissionRepository,
                             RolePermissionTemplateRepository templateRepository, AuditService auditService,
                             EntityManager entityManager) {
        this.catalog = catalog;
        this.userRepository = userRepository;
        this.modulePermissionRepository = modulePermissionRepository;
        this.actionPermissionRepository = actionPermissionRepository;
        this.templateRepository = templateRepository;
        this.auditService = auditService;
        this.entityManager = entityManager;
    }

    /** Whether a module can be granted to accounts of the given role. */
    public static boolean grantable(RoleCode role, ModuleDef module) {
        if (role == RoleCode.SUPER_ADMIN) {
            return true;
        }
        if (module.superAdminOnly()) {
            return false;
        }
        return !(role == RoleCode.USER && ModuleCode.USER_MANAGEMENT.name().equals(module.code()));
    }

    /** Effective permissions of a user whose role is loaded. */
    @Transactional(readOnly = true)
    public EffectivePermissions resolve(User user) {
        if (user.getRole().isSuperAdmin()) {
            return superAdminPermissions();
        }
        RoleCode role = user.getRole().roleCode().orElse(null);
        if (role == null || !user.getRole().isActive()) {
            return new EffectivePermissions(false, Map.of(), Set.of());
        }
        Map<String, ModuleAccess> modules = new LinkedHashMap<>();
        for (UserModulePermission row : modulePermissionRepository.findByUserId(user.getId())) {
            catalog.module(row.getModule().getCode())
                    .filter(def -> grantable(role, def))
                    .ifPresent(def -> modules.put(def.code(), new ModuleAccess(
                            row.isCanWrite() && def.supportsWrite(),
                            row.isCanDelete() && row.isCanWrite() && def.supportsDelete(),
                            row.isCanExport() && def.supportsExport(),
                            row.isCanApprove() && def.supportsApprove(),
                            def.supportsScope() ? row.getDataScope() : DataScope.ALL)));
        }
        Set<String> actions = new LinkedHashSet<>();
        for (UserActionPermission grant : actionPermissionRepository.findByIdUserId(user.getId())) {
            catalog.moduleAction(grant.getPermission().getCode())
                    .filter(a -> !a.superAdminOnly() && modules.containsKey(a.moduleCode()))
                    .ifPresent(a -> actions.add(EffectivePermissions.authority(a.moduleCode(), a.code())));
        }
        return new EffectivePermissions(false, sortByCatalog(modules), actions);
    }

    /** Full access to every module and action the catalogue defines. */
    public EffectivePermissions superAdminPermissions() {
        Map<String, ModuleAccess> modules = new LinkedHashMap<>();
        for (ModuleDef def : catalog.modules()) {
            modules.put(def.code(), new ModuleAccess(def.supportsWrite(), def.supportsDelete(), def.supportsExport(),
                    def.supportsApprove(), DataScope.ALL));
        }
        Set<String> actions = new LinkedHashSet<>();
        catalog.moduleActions().forEach(a -> actions.add(EffectivePermissions.authority(a.moduleCode(), a.code())));
        return new EffectivePermissions(true, modules, actions);
    }

    /** The permission matrix of {@code target} as shown to {@code actor}. */
    @Transactional(readOnly = true)
    public UserPermissionsResponse view(User target, AuthenticatedUser actor) {
        EffectivePermissions effective = resolve(target);
        List<ModulePermissionDto> rows = catalog.modules().stream().map(def -> {
            ModuleAccess access = effective.modules().get(def.code());
            if (access == null) {
                return new ModulePermissionDto(def.code(), false, false, false, false, false, null);
            }
            return new ModulePermissionDto(def.code(), true, access.write(), access.delete(), access.export(),
                    access.approve(), def.supportsScope() ? access.scope() : null);
        }).toList();
        List<String> actions = catalog.moduleActions().stream()
                .filter(a -> effective.actionCodes().contains(EffectivePermissions.authority(a.moduleCode(), a.code())))
                .map(ActionDef::code)
                .toList();
        boolean superAdmin = target.getRole().isSuperAdmin();
        boolean editable = !superAdmin && actor.isSuperAdmin() && !actor.id().equals(target.getId());
        return new UserPermissionsResponse(target.getId(), RoleRef.of(target.getRole()), superAdmin, editable, rows,
                actions);
    }

    /** Replaces a user's permissions (Super Admin only). */
    @Transactional
    public UserPermissionsResponse replace(UUID userId, UpdateUserPermissionsRequest request,
                                           AuthenticatedUser actor) {
        if (!actor.isSuperAdmin()) {
            throw new AccessDeniedException("Only a Super Admin can change permissions");
        }
        User target = userRepository.findForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (target.getId().equals(actor.id())) {
            throw new BusinessException("SELF_PERMISSION_CHANGE", "You cannot change your own permissions.",
                    HttpStatus.FORBIDDEN);
        }
        if (target.getRole().isSuperAdmin()) {
            throw new BusinessException("SUPER_ADMIN_PERMISSIONS_FIXED",
                    "A Super Admin always has full access; their permissions cannot be edited.");
        }
        RoleCode role = target.getRole().roleCode()
                .orElseThrow(() -> new BusinessException("ROLE_RETIRED", "The user's role is retired."));

        Validated validated = validate(request, role);
        Map<String, Object> before = snapshot(resolve(target));
        replaceRows(target, validated.modules(), validated.actions(), actor.id());
        Map<String, Object> after = snapshot(resolve(target));
        if (!before.equals(after)) {
            auditService.record(AuditActions.PERMISSIONS_CHANGED, AuditActions.ENTITY_USER, target.getId(),
                    before, after);
        }
        return view(target, actor);
    }

    /**
     * Resets a user's permissions to their role's template (new accounts and role changes). Super Admins
     * get no rows because their access is implicit.
     */
    @Transactional
    public void applyRoleTemplate(User user, UUID actorId) {
        RoleCode role = user.getRole().roleCode().orElse(null);
        List<ModulePermissionDto> rows = new ArrayList<>();
        if (role != null && role != RoleCode.SUPER_ADMIN) {
            for (RolePermissionTemplate t : templateRepository.findByIdRoleId(user.getRole().getId())) {
                catalog.module(t.getModule().getCode()).filter(def -> grantable(role, def)).ifPresent(def ->
                        rows.add(new ModulePermissionDto(def.code(), true, t.isCanWrite() && def.supportsWrite(),
                                t.isCanDelete() && t.isCanWrite() && def.supportsDelete(),
                                t.isCanExport() && def.supportsExport(), t.isCanApprove() && def.supportsApprove(),
                                def.supportsScope() ? t.getDataScope() : DataScope.ALL)));
            }
        }
        replaceRows(user, rows, List.of(), actorId);
    }

    /** Permissions as stored for the audit trail: module -> flags, plus granted actions. */
    public Map<String, Object> snapshot(EffectivePermissions permissions) {
        Map<String, Object> modules = new LinkedHashMap<>();
        permissions.modules().forEach((code, a) -> modules.put(code, AuditService.values(
                "write", a.write(), "delete", a.delete(), "export", a.export(), "approve", a.approve(),
                "scope", a.scope())));
        return AuditService.values("modules", modules, "actions", new TreeSet<>(permissions.actionCodes()));
    }

    private void replaceRows(User user, List<ModulePermissionDto> modules, List<String> actions, UUID actorId) {
        modulePermissionRepository.deleteByUserId(user.getId());
        for (ModulePermissionDto dto : modules) {
            ModuleDef def = catalog.module(dto.moduleCode()).orElseThrow();
            UserModulePermission row = new UserModulePermission(user, entityManager.getReference(AppModule.class, def.id()));
            row.setCanWrite(dto.write());
            row.setCanDelete(dto.delete());
            row.setCanExport(dto.export());
            row.setCanApprove(dto.approve());
            row.setDataScope(dto.dataScope() == null ? DataScope.ALL : dto.dataScope());
            modulePermissionRepository.save(row);
        }
        // Action grants have a composite key, so they are diffed rather than deleted and re-inserted
        // (a re-insert would clash with the instance already in the persistence context).
        Map<String, UserActionPermission> existing = new LinkedHashMap<>();
        actionPermissionRepository.findByIdUserId(user.getId())
                .forEach(grant -> existing.put(grant.getPermission().getCode(), grant));
        existing.forEach((code, grant) -> {
            if (!actions.contains(code)) {
                actionPermissionRepository.delete(grant);
            }
        });
        for (String code : actions) {
            if (!existing.containsKey(code)) {
                ActionDef action = catalog.moduleAction(code).orElseThrow();
                entityManager.persist(new UserActionPermission(user.getId(),
                        entityManager.getReference(Permission.class, action.id()), actorId));
            }
        }
        entityManager.flush();
    }

    private Validated validate(UpdateUserPermissionsRequest request, RoleCode role) {
        List<FieldError> errors = new ArrayList<>();
        Map<String, ModulePermissionDto> granted = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (ModulePermissionDto dto : request.modules()) {
            String field = "modules[" + dto.moduleCode() + "]";
            if (!seen.add(dto.moduleCode())) {
                errors.add(new FieldError(field, "Module is listed more than once."));
                continue;
            }
            ModuleDef def = catalog.module(dto.moduleCode()).orElse(null);
            if (def == null) {
                errors.add(new FieldError(field, "Unknown module."));
                continue;
            }
            boolean anyFlag = dto.write() || dto.delete() || dto.export() || dto.approve();
            if (!dto.read()) {
                if (anyFlag) {
                    errors.add(new FieldError(field, "Read access is required for any other permission."));
                }
                continue;
            }
            if (!grantable(role, def)) {
                errors.add(new FieldError(field, def.displayName() + " cannot be granted to the "
                        + role.name() + " role."));
                continue;
            }
            unsupported(errors, field + ".write", dto.write(), def.supportsWrite(), def, "Write");
            unsupported(errors, field + ".delete", dto.delete(), def.supportsDelete(), def, "Delete");
            unsupported(errors, field + ".export", dto.export(), def.supportsExport(), def, "Export");
            unsupported(errors, field + ".approve", dto.approve(), def.supportsApprove(), def, "Approve");
            if (dto.delete() && !dto.write()) {
                errors.add(new FieldError(field + ".delete", "Delete requires Write."));
            }
            DataScope scope = dto.dataScope() == null ? DataScope.ALL : dto.dataScope();
            if (!def.supportsScope() && scope != DataScope.ALL) {
                errors.add(new FieldError(field + ".dataScope", def.displayName() + " does not support data scopes."));
            }
            granted.put(def.code(), new ModulePermissionDto(def.code(), true, dto.write(), dto.delete(), dto.export(),
                    dto.approve(), def.supportsScope() ? scope : DataScope.ALL));
        }
        List<String> actions = new ArrayList<>();
        for (String code : new LinkedHashSet<>(request.actions())) {
            String field = "actions[" + code + "]";
            ActionDef action = catalog.moduleAction(code).orElse(null);
            if (action == null) {
                errors.add(new FieldError(field, "Unknown action."));
            } else if (action.superAdminOnly()) {
                errors.add(new FieldError(field, action.displayName() + " is reserved for Super Admins."));
            } else if (!granted.containsKey(action.moduleCode())) {
                errors.add(new FieldError(field, action.displayName() + " requires read access to "
                        + catalog.module(action.moduleCode()).map(ModuleDef::displayName).orElse(action.moduleCode())
                        + "."));
            } else {
                actions.add(code);
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new Validated(List.copyOf(granted.values()), actions);
    }

    private static void unsupported(List<FieldError> errors, String field, boolean requested, boolean supported,
                                    ModuleDef def, String label) {
        if (requested && !supported) {
            errors.add(new FieldError(field, def.displayName() + " does not support " + label + "."));
        }
    }

    private Map<String, ModuleAccess> sortByCatalog(Map<String, ModuleAccess> modules) {
        Map<String, ModuleAccess> sorted = new LinkedHashMap<>();
        catalog.modules().stream().map(ModuleDef::code).filter(modules::containsKey)
                .forEach(code -> sorted.put(code, Objects.requireNonNull(modules.get(code))));
        return sorted;
    }

    private record Validated(List<ModulePermissionDto> modules, List<String> actions) {
    }
}
