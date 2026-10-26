package com.iotiq.partnererp.modules.payouts.repository;

import com.iotiq.partnererp.modules.payouts.entity.Payout;
import com.iotiq.partnererp.modules.payouts.entity.PayoutStatus;
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

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Partner payouts. Rows are never deleted ({@code payouts_guard}); finished payouts never change.
 */
public interface PayoutRepository extends JpaRepository<Payout, UUID>, JpaSpecificationExecutor<Payout> {

    @Override
    @EntityGraph(attributePaths = {"partner"})
    Page<Payout> findAll(Specification<Payout> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"partner", "tdsConfiguration"})
    Optional<Payout> findWithPartnerById(UUID id);

    /** Locks the payout: transitions and the payment reversal hook take this lock, so they run one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payout p where p.id = :id")
    Optional<Payout> lock(@Param("id") UUID id);

    Optional<Payout> findByIdempotencyKey(UUID idempotencyKey);

    Optional<Payout> findFirstByPartnerIdAndStatusIn(UUID partnerId, Collection<PayoutStatus> statuses);
}
