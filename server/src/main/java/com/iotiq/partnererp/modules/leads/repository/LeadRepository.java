package com.iotiq.partnererp.modules.leads.repository;

import com.iotiq.partnererp.modules.leads.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

    List<Lead> findByClientPhoneDigits(String clientPhoneDigits);
}
