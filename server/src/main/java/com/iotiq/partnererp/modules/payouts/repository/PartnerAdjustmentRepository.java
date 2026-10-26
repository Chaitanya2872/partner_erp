package com.iotiq.partnererp.modules.payouts.repository;

import com.iotiq.partnererp.modules.payouts.entity.PartnerAdjustment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Partner adjustments. Never deleted; a manual one is decided once ({@code payout_records_guard}).
 */
public interface PartnerAdjustmentRepository extends JpaRepository<PartnerAdjustment, UUID>,
        JpaSpecificationExecutor<PartnerAdjustment> {

    @Override
    @EntityGraph(attributePaths = {"partner", "sourcePayment"})
    Page<PartnerAdjustment> findAll(Specification<PartnerAdjustment> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"partner", "sourcePayment"})
    Optional<PartnerAdjustment> findWithPartnerById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PartnerAdjustment a where a.id = :id")
    Optional<PartnerAdjustment> lock(@Param("id") UUID id);

    Optional<PartnerAdjustment> findByIdempotencyKey(UUID idempotencyKey);

    boolean existsBySourceLineId(UUID sourceLineId);

    /** Approved adjustments of the partner not allocated to a live payout: carried into the next payout. */
    @Query("select a from PartnerAdjustment a where a.partner.id = :partnerId"
            + " and a.status = com.iotiq.partnererp.modules.payouts.entity.PayoutEnums.AdjustmentStatus.APPROVED"
            + " and not exists (select l.id from PayoutLine l where l.adjustment = a and l.releasedAt is null)"
            + " order by a.createdAt, a.id")
    List<PartnerAdjustment> carried(@Param("partnerId") UUID partnerId);
}
