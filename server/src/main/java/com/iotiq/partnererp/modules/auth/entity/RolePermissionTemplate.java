package com.iotiq.partnererp.modules.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Default module access copied to a new account (or on a role change) for its role. A row grants read.
 */
@Entity
@Immutable
@Table(name = "role_permission_templates")
public class RolePermissionTemplate {

    @EmbeddedId
    private Key id;

    @MapsId("moduleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id")
    private AppModule module;

    @Column(name = "can_write", nullable = false)
    private boolean canWrite;

    @Column(name = "can_delete", nullable = false)
    private boolean canDelete;

    @Column(name = "can_export", nullable = false)
    private boolean canExport;

    @Column(name = "can_approve", nullable = false)
    private boolean canApprove;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_scope", nullable = false, length = 10)
    private DataScope dataScope;

    public Key getId() {
        return id;
    }

    public AppModule getModule() {
        return module;
    }

    public boolean isCanWrite() {
        return canWrite;
    }

    public boolean isCanDelete() {
        return canDelete;
    }

    public boolean isCanExport() {
        return canExport;
    }

    public boolean isCanApprove() {
        return canApprove;
    }

    public DataScope getDataScope() {
        return dataScope;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "role_id", nullable = false)
        private UUID roleId;

        @Column(name = "module_id", nullable = false)
        private UUID moduleId;

        protected Key() {
        }

        public UUID getRoleId() {
            return roleId;
        }

        public UUID getModuleId() {
            return moduleId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(roleId, k.roleId) && Objects.equals(moduleId, k.moduleId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roleId, moduleId);
        }
    }
}
