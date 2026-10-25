package com.iotiq.partnererp.modules.auth.repository;

import com.iotiq.partnererp.modules.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(String code);

    Optional<Role> findByIdAndActiveTrue(UUID id);

    List<Role> findAllByActiveTrue();

    @Query("select count(r) > 0 from Role r where r.active = true and r.id <> :excludeId "
            + "and lower(trim(r.displayName)) = lower(trim(:displayName))")
    boolean existsActiveDisplayName(String displayName, UUID excludeId);
}
