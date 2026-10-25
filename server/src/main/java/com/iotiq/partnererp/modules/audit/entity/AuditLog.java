package com.iotiq.partnererp.modules.audit.entity;

import com.iotiq.partnererp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit entry. old/new values are JSON with sensitive keys already redacted.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "entity_type", nullable = false, length = 60, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    @Column(name = "action", nullable = false, length = 40, updatable = false)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "jsonb", updatable = false)
    private String oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "jsonb", updatable = false)
    private String newValues;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @Column(name = "request_id", length = 64, updatable = false)
    private String requestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOldValues() {
        return oldValues;
    }

    public void setOldValues(String oldValues) {
        this.oldValues = oldValues;
    }

    public String getNewValues() {
        return newValues;
    }

    public void setNewValues(String newValues) {
        this.newValues = newValues;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
