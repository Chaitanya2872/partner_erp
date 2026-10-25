package com.iotiq.partnererp.modules.auth.controller;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.response.ApiResponse;
import com.iotiq.partnererp.modules.auth.dto.ActionResponse;
import com.iotiq.partnererp.modules.auth.dto.ModuleResponse;
import com.iotiq.partnererp.modules.auth.entity.PermissionKind;
import com.iotiq.partnererp.modules.auth.service.ModuleCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Module and action catalogue (reference data: names and which permission columns apply). Available to
 * every signed-in user because navigation labels come from it; it contains no user data.
 */
@Tag(name = "Permission management")
@RestController
@RequestMapping(AppConstants.API_V1)
public class AccessCatalogController {

    private final ModuleCatalog catalog;

    public AccessCatalogController(ModuleCatalog catalog) {
        this.catalog = catalog;
    }

    @Operation(summary = "Permission-controlled modules")
    @GetMapping("/modules")
    public ApiResponse<List<ModuleResponse>> modules() {
        return ApiResponse.ok(catalog.modules().stream()
                .map(m -> ModuleResponse.of(m, catalog.moduleActions().stream()
                        .filter(a -> m.code().equals(a.moduleCode()))
                        .map(ActionResponse::of)
                        .toList()))
                .toList());
    }

    @Operation(summary = "Action permissions (generic and module-specific)")
    @GetMapping("/permissions")
    public ApiResponse<List<ActionResponse>> permissions() {
        return ApiResponse.ok(catalog.actions().stream()
                .sorted((a, b) -> Boolean.compare(a.kind() == PermissionKind.MODULE, b.kind() == PermissionKind.MODULE))
                .map(ActionResponse::of)
                .toList());
    }
}
