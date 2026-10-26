package com.iotiq.partnererp.modules.invoices.repository;

import com.iotiq.partnererp.modules.invoices.entity.Invoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Invoices. Discarded drafts ({@code deletedAt}) are excluded by the live finders here and by the service search.
 */
public interface InvoiceRepository extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    @EntityGraph(attributePaths = {"partner", "lead", "lead.partner", "lead.partner.tier", "estimate"})
    Optional<Invoice> findByIdAndDeletedAtIsNull(UUID id);

    /** Locks the invoice for edits, issue, cancel and discard, so concurrent requests wait and re-read. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id and i.deletedAt is null")
    Optional<Invoice> lockLive(@Param("id") UUID id);

    /** The live (draft or issued) invoice made from an estimate, if any. */
    @Query("select i from Invoice i where i.estimate.id = :estimateId and i.deletedAt is null"
            + " and i.status <> com.iotiq.partnererp.common.enums.InvoiceStatus.CANCELLED")
    Optional<Invoice> findLiveForEstimate(@Param("estimateId") UUID estimateId);

    /** Net amount received (refunds are negative), BR-V6. */
    @Query(value = "select coalesce(sum(amount), 0) from payments where invoice_id = :id", nativeQuery = true)
    BigDecimal paidAmount(@Param("id") UUID id);

    @Query(value = "select invoice_id as invoiceId, coalesce(sum(amount), 0) as paid from payments"
            + " where invoice_id in (:ids) group by invoice_id", nativeQuery = true)
    List<PaidAmount> paidAmounts(@Param("ids") Collection<UUID> ids);

    @Query(value = "select count(*) from payments where invoice_id = :id", nativeQuery = true)
    long paymentCount(@Param("id") UUID id);

    @Query(value = "select count(*) from partner_payouts where invoice_id = :id", nativeQuery = true)
    long payoutCount(@Param("id") UUID id);

    interface PaidAmount {
        UUID getInvoiceId();

        BigDecimal getPaid();
    }
}
