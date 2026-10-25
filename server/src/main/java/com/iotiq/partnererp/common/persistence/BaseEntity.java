package com.iotiq.partnererp.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.proxy.HibernateProxy;

import java.util.UUID;

/**
 * UUID primary key and identity-based equality shared by all entities.
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    public UUID getId() {
        return id;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || effectiveClass(this) != effectiveClass(o)) {
            return false;
        }
        UUID otherId = ((BaseEntity) o).getId();
        return id != null && id.equals(otherId);
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }

    private static Class<?> effectiveClass(Object o) {
        return o instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
    }
}
