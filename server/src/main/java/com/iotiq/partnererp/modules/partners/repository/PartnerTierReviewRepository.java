package com.iotiq.partnererp.modules.partners.repository;

import com.iotiq.partnererp.modules.partners.entity.PartnerTierReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PartnerTierReviewRepository extends JpaRepository<PartnerTierReview, UUID> {

    List<PartnerTierReview> findByPartnerIdOrderByReviewedAtDesc(UUID partnerId);
}
