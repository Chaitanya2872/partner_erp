package com.iotiq.partnererp.modules.settings.repository;

import com.iotiq.partnererp.modules.settings.entity.PartnerTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerTierRepository extends JpaRepository<PartnerTier, UUID> {

    Optional<PartnerTier> findByTierLevel(short tierLevel);

    List<PartnerTier> findAllByOrderByTierLevelAsc();
}
