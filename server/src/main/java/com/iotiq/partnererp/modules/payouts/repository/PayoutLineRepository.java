package com.iotiq.partnererp.modules.payouts.repository;

import com.iotiq.partnererp.modules.payouts.entity.PayoutLine;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Payout allocations. A line is never edited; it is released once at most ({@code payout_records_guard}).
 */
public interface PayoutLineRepository extends JpaRepository<PayoutLine, UUID> {

    /** Every line of the payout, released ones included, oldest first. */
    @EntityGraph(attributePaths = {"payment", "invoice", "adjustment"})
    List<PayoutLine> findByPayoutIdOrderByCreatedAtAscIdAsc(UUID payoutId);

    @EntityGraph(attributePaths = {"payment", "invoice", "adjustment"})
    @Query("select l from PayoutLine l where l.payout.id = :payoutId and l.releasedAt is null order by l.createdAt, l.id")
    List<PayoutLine> active(@Param("payoutId") UUID payoutId);

    /** The live allocation of a receipt's commission, if any. */
    @Query("select l from PayoutLine l where l.payment.id = :paymentId and l.releasedAt is null")
    Optional<PayoutLine> activeForPayment(@Param("paymentId") UUID paymentId);

    /** The live allocations of these adjustments, with their payouts. */
    @EntityGraph(attributePaths = {"payout", "adjustment"})
    @Query("select l from PayoutLine l where l.adjustment.id in :ids and l.releasedAt is null")
    List<PayoutLine> activeForAdjustments(@Param("ids") Collection<UUID> ids);

    /** Active commission lines of the payout whose receipt has since been reversed. */
    @EntityGraph(attributePaths = {"payment"})
    @Query("select l from PayoutLine l where l.payout.id = :payoutId and l.releasedAt is null and l.payment is not null"
            + " and exists (select r.id from Payment r where r.reversedPayment = l.payment) order by l.createdAt, l.id")
    List<PayoutLine> reversedSources(@Param("payoutId") UUID payoutId);

    /** The payouts among {@code ids} holding an active line whose receipt was reversed. */
    @Query("select distinct l.payout.id from PayoutLine l where l.payout.id in :ids and l.releasedAt is null"
            + " and l.payment is not null and exists (select r.id from Payment r where r.reversedPayment = l.payment)")
    List<UUID> withReversedSources(@Param("ids") Collection<UUID> ids);
}
