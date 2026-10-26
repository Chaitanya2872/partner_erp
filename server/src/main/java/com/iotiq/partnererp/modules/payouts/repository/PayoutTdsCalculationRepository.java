package com.iotiq.partnererp.modules.payouts.repository;

import com.iotiq.partnererp.modules.payouts.entity.PayoutTdsCalculation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Append-only TDS calculation history of payouts. */
public interface PayoutTdsCalculationRepository extends JpaRepository<PayoutTdsCalculation, UUID> {

    @EntityGraph(attributePaths = {"tdsConfiguration"})
    List<PayoutTdsCalculation> findByPayoutIdOrderByCreatedAtDescIdDesc(UUID payoutId);
}
