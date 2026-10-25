package com.iotiq.partnererp.modules.auth.repository;

import com.iotiq.partnererp.modules.auth.entity.Permission;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByCode(String code);

    /** The current action catalogue (retired Phase 2 codes excluded). */
    @EntityGraph(attributePaths = "module")
    List<Permission> findAllByActiveTrueOrderBySortOrderAsc();
}
