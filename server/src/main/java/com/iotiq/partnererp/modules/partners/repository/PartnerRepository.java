package com.iotiq.partnererp.modules.partners.repository;

import com.iotiq.partnererp.modules.partners.entity.Partner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PartnerRepository extends JpaRepository<Partner, UUID> {

    Optional<Partner> findByPartnerCode(String partnerCode);
}
