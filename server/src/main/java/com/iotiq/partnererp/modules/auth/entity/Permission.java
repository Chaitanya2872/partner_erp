package com.iotiq.partnererp.modules.auth.entity;

import com.iotiq.partnererp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Action permission catalogue (V12). Inactive rows are the retired Phase 2 proposal codes.
 */
@Entity
@Immutable
@Table(name = "permissions")
public class Permission extends BaseEntity {

    @Column(name = "code", nullable = false, length = 60, unique = true)
    private String code;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private PermissionKind kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id")
    private AppModule module;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "super_admin_only", nullable = false)
    private boolean superAdminOnly;

    @Column(name = "sort_order")
    private Short sortOrder;

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public PermissionKind getKind() {
        return kind;
    }

    public AppModule getModule() {
        return module;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isSuperAdminOnly() {
        return superAdminOnly;
    }

    public Short getSortOrder() {
        return sortOrder;
    }
}
