package com.iotiq.partnererp.modules.auth.repository;

import com.iotiq.partnererp.modules.auth.entity.UserModulePermission;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface UserModulePermissionRepository extends JpaRepository<UserModulePermission, UUID> {

    @EntityGraph(attributePaths = "module")
    List<UserModulePermission> findByUserId(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from UserModulePermission p where p.user.id = :userId")
    int deleteByUserId(UUID userId);
}
