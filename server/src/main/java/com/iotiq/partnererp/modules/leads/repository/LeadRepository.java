package com.iotiq.partnererp.modules.leads.repository;

import com.iotiq.partnererp.modules.leads.entity.Lead;
import jakarta.persistence.LockModeType;
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
 * Leads. Soft-deleted leads (D-26) are excluded by the live finders here and by the service's search specification.
 */
public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

    /** Includes deleted leads; used by persistence tests of the generated digits column. */
    List<Lead> findByClientPhoneDigits(String clientPhoneDigits);

    @EntityGraph(attributePaths = {"partner", "partner.tier"})
    Optional<Lead> findByIdAndDeletedAtIsNull(UUID id);

    /** An archived (soft-deleted) lead, for the archive views. */
    @EntityGraph(attributePaths = {"partner", "partner.tier"})
    Optional<Lead> findByIdAndDeletedAtIsNotNull(UUID id);

    /** Locks an archived lead for restore. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lead l where l.id = :id and l.deletedAt is not null")
    Optional<Lead> lockArchived(@Param("id") UUID id);

    /** Locks the lead for acknowledge, stage change and delete, so concurrent requests wait and re-read. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lead l where l.id = :id and l.deletedAt is null")
    Optional<Lead> lockLive(@Param("id") UUID id);
}
