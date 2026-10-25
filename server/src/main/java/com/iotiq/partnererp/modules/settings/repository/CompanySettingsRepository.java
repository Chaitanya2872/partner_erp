package com.iotiq.partnererp.modules.settings.repository;

import com.iotiq.partnererp.modules.settings.entity.CompanySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface CompanySettingsRepository extends JpaRepository<CompanySettings, UUID> {

    /** The table holds a single row (enforced by the singleton constraint). */
    @Query("select s from CompanySettings s")
    Optional<CompanySettings> findSingleton();
}
