package com.iotiq.partnererp.modules.auth.service;

import com.iotiq.partnererp.modules.auth.entity.PermissionKind;
import com.iotiq.partnererp.modules.auth.repository.AppModuleRepository;
import com.iotiq.partnererp.modules.auth.repository.PermissionRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Modules and action permissions are reference data that only migrations change, so they are read once
 * and kept in memory as plain records.
 */
@Component
public class ModuleCatalog {

    private final AppModuleRepository moduleRepository;
    private final PermissionRepository permissionRepository;
    private volatile Snapshot snapshot;

    public ModuleCatalog(AppModuleRepository moduleRepository, PermissionRepository permissionRepository) {
        this.moduleRepository = moduleRepository;
        this.permissionRepository = permissionRepository;
    }

    public List<ModuleDef> modules() {
        return snapshot().modules;
    }

    public Optional<ModuleDef> module(String code) {
        return Optional.ofNullable(snapshot().modulesByCode.get(code));
    }

    /** All active actions (GENERIC and MODULE), in catalogue order. */
    public List<ActionDef> actions() {
        return snapshot().actions;
    }

    /** Active module-specific actions, keyed by code. */
    public Optional<ActionDef> moduleAction(String code) {
        return Optional.ofNullable(snapshot().moduleActionsByCode.get(code));
    }

    public List<ActionDef> moduleActions() {
        return snapshot().moduleActionsByCode.values().stream().toList();
    }

    private Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current == null) {
            synchronized (this) {
                current = snapshot;
                if (current == null) {
                    current = load();
                    snapshot = current;
                }
            }
        }
        return current;
    }

    private Snapshot load() {
        List<ModuleDef> modules = moduleRepository.findAllByOrderBySortOrderAsc().stream()
                .map(m -> new ModuleDef(m.getId(), m.getCode(), m.getDisplayName(), m.getDescription(),
                        m.getSortOrder(), m.isSupportsWrite(), m.isSupportsDelete(), m.isSupportsExport(),
                        m.isSupportsApprove(), m.isSupportsScope(), m.isSuperAdminOnly()))
                .toList();
        Map<String, ModuleDef> byCode = new LinkedHashMap<>();
        modules.forEach(m -> byCode.put(m.code(), m));

        List<ActionDef> actions = permissionRepository.findAllByActiveTrueOrderBySortOrderAsc().stream()
                .map(p -> new ActionDef(p.getId(), p.getCode(), p.getDisplayName(), p.getDescription(), p.getKind(),
                        p.getModule() == null ? null : p.getModule().getCode(), p.isSuperAdminOnly()))
                .toList();
        Map<String, ActionDef> moduleActions = new LinkedHashMap<>();
        actions.stream().filter(a -> a.kind() == PermissionKind.MODULE).forEach(a -> moduleActions.put(a.code(), a));
        return new Snapshot(modules, byCode, actions, moduleActions);
    }

    public record ModuleDef(UUID id, String code, String displayName, String description, short sortOrder,
                            boolean supportsWrite, boolean supportsDelete, boolean supportsExport,
                            boolean supportsApprove, boolean supportsScope, boolean superAdminOnly) {
    }

    public record ActionDef(UUID id, String code, String displayName, String description, PermissionKind kind,
                            String moduleCode, boolean superAdminOnly) {
    }

    record Snapshot(List<ModuleDef> modules, Map<String, ModuleDef> modulesByCode, List<ActionDef> actions,
                    Map<String, ActionDef> moduleActionsByCode) {
    }
}
