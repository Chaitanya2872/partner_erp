package com.iotiq.partnererp.modules.leads.repository;

import com.iotiq.partnererp.modules.leads.entity.LeadStageHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeadStageHistoryRepository extends JpaRepository<LeadStageHistory, UUID> {

    List<LeadStageHistory> findByLeadIdOrderByOccurredAtAsc(UUID leadId);
}
