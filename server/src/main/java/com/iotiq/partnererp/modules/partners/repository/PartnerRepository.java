package com.iotiq.partnererp.modules.partners.repository;

import com.iotiq.partnererp.common.enums.PartnerStatus;
import com.iotiq.partnererp.modules.partners.entity.Partner;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerRepository extends JpaRepository<Partner, UUID>, JpaSpecificationExecutor<Partner> {

    Optional<Partner> findByPartnerCode(String partnerCode);

    @EntityGraph(attributePaths = "tier")
    List<Partner> findByStatusNotOrderByNameAsc(PartnerStatus status);

    @EntityGraph(attributePaths = "tier")
    List<Partner> findByIdInOrderByNameAsc(Collection<UUID> ids);

    /** Locks the partners while a tier review is applied, so concurrent edits wait. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Partner p where p.id in :ids order by p.id")
    List<Partner> lockAllById(@Param("ids") Collection<UUID> ids);
}
