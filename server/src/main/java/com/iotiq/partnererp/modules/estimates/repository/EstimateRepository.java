package com.iotiq.partnererp.modules.estimates.repository;

import com.iotiq.partnererp.modules.estimates.entity.Estimate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Estimates. Soft-deleted estimates are excluded by the live finders here and by the service search.
 */
public interface EstimateRepository extends JpaRepository<Estimate, UUID>, JpaSpecificationExecutor<Estimate> {

    Optional<Estimate> findByEstimateNumber(String estimateNumber);

    @EntityGraph(attributePaths = {"partner", "lead", "lead.partner", "lead.partner.tier"})
    Optional<Estimate> findByIdAndDeletedAtIsNull(UUID id);

    /** Locks the estimate for status changes and delete, so concurrent requests wait and re-read. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Estimate e where e.id = :id and e.deletedAt is null")
    Optional<Estimate> lockLive(@Param("id") UUID id);
}
