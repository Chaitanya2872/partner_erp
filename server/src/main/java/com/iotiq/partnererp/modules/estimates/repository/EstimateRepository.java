package com.iotiq.partnererp.modules.estimates.repository;

import com.iotiq.partnererp.modules.estimates.entity.Estimate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EstimateRepository extends JpaRepository<Estimate, UUID> {

    Optional<Estimate> findByEstimateNumber(String estimateNumber);
}
