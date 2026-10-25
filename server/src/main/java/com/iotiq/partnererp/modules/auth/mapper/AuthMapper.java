package com.iotiq.partnererp.modules.auth.mapper;

import com.iotiq.partnererp.modules.auth.dto.MeResponse;
import com.iotiq.partnererp.modules.auth.dto.ModulePermissionDto;
import com.iotiq.partnererp.modules.auth.dto.RoleRef;
import com.iotiq.partnererp.modules.auth.entity.User;
import com.iotiq.partnererp.modules.auth.service.ModuleCatalog;
import com.iotiq.partnererp.security.EffectivePermissions;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuthMapper {

    private final ModuleCatalog catalog;

    public AuthMapper(ModuleCatalog catalog) {
        this.catalog = catalog;
    }

    public MeResponse toMe(User user, EffectivePermissions permissions) {
        List<ModulePermissionDto> modules = permissions.modules().entrySet().stream()
                .map(e -> {
                    boolean scoped = catalog.module(e.getKey()).map(ModuleCatalog.ModuleDef::supportsScope).orElse(false);
                    return new ModulePermissionDto(e.getKey(), true, e.getValue().write(), e.getValue().delete(),
                            e.getValue().export(), e.getValue().approve(), scoped ? e.getValue().scope() : null);
                })
                .toList();
        return new MeResponse(user.getId(), user.getFullName(), user.getEmail(), RoleRef.of(user.getRole()),
                permissions.superAdmin(), user.isMustChangePassword(), user.getLastLoginAt(), modules,
                List.copyOf(permissions.authorities()));
    }
}
