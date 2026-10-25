package com.iotiq.partnererp.modules.auth.dto;

import com.iotiq.partnererp.modules.auth.entity.DataScope;
import jakarta.validation.constraints.NotBlank;

/**
 * One row of the permission matrix. {@code read=false} means no access (all other flags must be false).
 * {@code dataScope} applies only to modules that support scoping; it defaults to ALL. Omitted flags are false.
 */
public record ModulePermissionDto(@NotBlank String moduleCode, Boolean read, Boolean write, Boolean delete,
                                  Boolean export, Boolean approve, DataScope dataScope) {

    public ModulePermissionDto {
        read = Boolean.TRUE.equals(read);
        write = Boolean.TRUE.equals(write);
        delete = Boolean.TRUE.equals(delete);
        export = Boolean.TRUE.equals(export);
        approve = Boolean.TRUE.equals(approve);
    }
}
