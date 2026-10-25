package com.iotiq.partnererp.modules.auth.repository;

import com.iotiq.partnererp.modules.auth.entity.RolePermissionTemplate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RolePermissionTemplateRepository
        extends JpaRepository<RolePermissionTemplate, RolePermissionTemplate.Key> {

    @EntityGraph(attributePaths = "module")
    List<RolePermissionTemplate> findByIdRoleId(UUID roleId);
}
