package com.iotiq.partnererp.modules.auth.repository;

import com.iotiq.partnererp.modules.auth.entity.UserActionPermission;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserActionPermissionRepository extends JpaRepository<UserActionPermission, UserActionPermission.Key> {

    @EntityGraph(attributePaths = {"permission", "permission.module"})
    List<UserActionPermission> findByIdUserId(UUID userId);
}
