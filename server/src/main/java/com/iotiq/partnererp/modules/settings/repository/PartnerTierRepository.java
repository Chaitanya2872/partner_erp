package com.iotiq.partnererp.modules.settings.repository;

import com.iotiq.partnererp.modules.settings.entity.PartnerTier;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerTierRepository extends JpaRepository<PartnerTier, UUID> {

    Optional<PartnerTier> findByTierLevel(short tierLevel);

    List<PartnerTier> findAllByOrderByTierLevelAsc();

    /** All four tiers, locked in level order so concurrent tier edits queue instead of interleaving. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PartnerTier t order by t.tierLevel")
    List<PartnerTier> lockAll();

    /** Partners per tier level, excluding Inactive partners: [tierLevel, count]. */
    @Query("select p.tier.tierLevel, count(p) from Partner p "
            + "where p.status <> com.iotiq.partnererp.common.enums.PartnerStatus.INACTIVE group by p.tier.tierLevel")
    List<Object[]> countPartnersByLevel();
}
