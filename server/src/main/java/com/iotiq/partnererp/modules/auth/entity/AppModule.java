package com.iotiq.partnererp.modules.auth.entity;

import com.iotiq.partnererp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Permission-controlled application module (reference data seeded by V12). The {@code supports*} flags
 * say which permission columns apply to it.
 */
@Entity
@Immutable
@Table(name = "modules")
public class AppModule extends BaseEntity {

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "description")
    private String description;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(name = "supports_write", nullable = false)
    private boolean supportsWrite;

    @Column(name = "supports_delete", nullable = false)
    private boolean supportsDelete;

    @Column(name = "supports_export", nullable = false)
    private boolean supportsExport;

    @Column(name = "supports_approve", nullable = false)
    private boolean supportsApprove;

    @Column(name = "supports_scope", nullable = false)
    private boolean supportsScope;

    @Column(name = "super_admin_only", nullable = false)
    private boolean superAdminOnly;

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public short getSortOrder() {
        return sortOrder;
    }

    public boolean isSupportsWrite() {
        return supportsWrite;
    }

    public boolean isSupportsDelete() {
        return supportsDelete;
    }

    public boolean isSupportsExport() {
        return supportsExport;
    }

    public boolean isSupportsApprove() {
        return supportsApprove;
    }

    public boolean isSupportsScope() {
        return supportsScope;
    }

    public boolean isSuperAdminOnly() {
        return superAdminOnly;
    }
}
