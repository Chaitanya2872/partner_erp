package com.iotiq.partnererp.modules.auth.entity;

import com.iotiq.partnererp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's access to one module. The row's existence means read access; no row means no access.
 */
@Entity
@Table(name = "user_module_permissions")
@EntityListeners(AuditingEntityListener.class)
public class UserModulePermission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false, updatable = false)
    private AppModule module;

    @Column(name = "can_read", nullable = false)
    private boolean canRead = true;

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
    private DataScope dataScope = DataScope.ALL;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    protected UserModulePermission() {
    }

    public UserModulePermission(User user, AppModule module) {
        this.user = user;
        this.module = module;
    }

    public User getUser() {
        return user;
    }

    public AppModule getModule() {
        return module;
    }

    public boolean isCanRead() {
        return canRead;
    }

    public boolean isCanWrite() {
        return canWrite;
    }

    public void setCanWrite(boolean canWrite) {
        this.canWrite = canWrite;
    }

    public boolean isCanDelete() {
        return canDelete;
    }

    public void setCanDelete(boolean canDelete) {
        this.canDelete = canDelete;
    }

    public boolean isCanExport() {
        return canExport;
    }

    public void setCanExport(boolean canExport) {
        this.canExport = canExport;
    }

    public boolean isCanApprove() {
        return canApprove;
    }

    public void setCanApprove(boolean canApprove) {
        this.canApprove = canApprove;
    }

    public DataScope getDataScope() {
        return dataScope;
    }

    public void setDataScope(DataScope dataScope) {
        this.dataScope = dataScope;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
