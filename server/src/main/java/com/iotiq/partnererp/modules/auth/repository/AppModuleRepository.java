package com.iotiq.partnererp.modules.auth.repository;

import com.iotiq.partnererp.modules.auth.entity.AppModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppModuleRepository extends JpaRepository<AppModule, UUID> {

    List<AppModule> findAllByOrderBySortOrderAsc();
}
