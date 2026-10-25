package com.iotiq.partnererp.modules.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A module-specific sensitive action granted to one user (e.g. MARK_PAYOUT_PAID).
 */
@Entity
@Table(name = "user_action_permissions")
public class UserActionPermission {

    @EmbeddedId
    private Key id;

    @MapsId("permissionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id")
    private Permission permission;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "granted_by", updatable = false)
    private UUID grantedBy;

    protected UserActionPermission() {
    }

    public UserActionPermission(UUID userId, Permission permission, UUID grantedBy) {
        this.id = new Key(userId, permission.getId());
        this.permission = permission;
        this.grantedBy = grantedBy;
    }

    @PrePersist
    void onCreate() {
        if (grantedAt == null) {
            grantedAt = Instant.now();
        }
    }

    public Key getId() {
        return id;
    }

    public Permission getPermission() {
        return permission;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        @Column(name = "permission_id", nullable = false)
        private UUID permissionId;

        protected Key() {
        }

        public Key(UUID userId, UUID permissionId) {
            this.userId = userId;
            this.permissionId = permissionId;
        }

        public UUID getUserId() {
            return userId;
        }

        public UUID getPermissionId() {
            return permissionId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(permissionId, k.permissionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, permissionId);
        }
    }
}
