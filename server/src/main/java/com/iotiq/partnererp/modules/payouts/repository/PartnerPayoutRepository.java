package com.iotiq.partnererp.modules.payouts.repository;

import com.iotiq.partnererp.modules.payouts.entity.PartnerPayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PartnerPayoutRepository extends JpaRepository<PartnerPayout, UUID> {

    Optional<PartnerPayout> findByPaymentId(UUID paymentId);
}
