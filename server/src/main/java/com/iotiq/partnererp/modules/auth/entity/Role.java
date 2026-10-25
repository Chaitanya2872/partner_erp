package com.iotiq.partnererp.modules.auth.entity;

import com.iotiq.partnererp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * System role. {@link #code} is immutable and is what security checks use; {@link #displayName} is
 * editable by a Super Admin and is only ever shown to people.
 */
@Entity
@Table(name = "roles")
@EntityListeners(AuditingEntityListener.class)
public class Role extends BaseEntity {

    @Column(name = "code", nullable = false, length = 40, unique = true, updatable = false)
    private String code;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName;

    @Column(name = "description")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** The typed code, or empty for a retired role. */
    public Optional<RoleCode> roleCode() {
        return RoleCode.from(code);
    }

    public boolean isSuperAdmin() {
        return RoleCode.SUPER_ADMIN.name().equals(code);
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public long getVersion() {
        return version;
    }
}
