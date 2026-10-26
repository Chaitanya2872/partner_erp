package com.iotiq.partnererp.modules.payouts.repository;

import com.iotiq.partnererp.modules.payouts.entity.PayoutEnums.TdsConfigStatus;
import com.iotiq.partnererp.modules.payouts.entity.TdsConfiguration;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * TDS configuration versions. Confirmed versions never change ({@code tds_configurations_guard}).
 */
public interface TdsConfigurationRepository extends JpaRepository<TdsConfiguration, UUID> {

    /** The confirmed versions effective on or before {@code date}, latest first; the first one is in force. */
    @Query("select c from TdsConfiguration c where c.status = com.iotiq.partnererp.modules.payouts.entity"
            + ".PayoutEnums.TdsConfigStatus.CONFIRMED and c.effectiveFrom <= :date order by c.effectiveFrom desc")
    List<TdsConfiguration> effectiveOn(@Param("date") LocalDate date);

    default Optional<TdsConfiguration> inForce(LocalDate date) {
        return effectiveOn(date).stream().findFirst();
    }

    Optional<TdsConfiguration> findFirstByStatus(TdsConfigStatus status);

    List<TdsConfiguration> findByStatusOrderByEffectiveFromDesc(TdsConfigStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TdsConfiguration c where c.id = :id")
    Optional<TdsConfiguration> lock(@Param("id") UUID id);

    boolean existsByStatusAndEffectiveFrom(TdsConfigStatus status, LocalDate effectiveFrom);
}
