package com.iotiq.partnererp.modules.payments.repository;

import com.iotiq.partnererp.modules.payments.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The payment ledger. Entries are never updated or deleted (V17 trigger); a receipt is "reversed" when a reversal
 * entry points at it.
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    List<Payment> findByInvoiceIdOrderByPaymentDateAsc(UUID invoiceId);

    @Override
    @EntityGraph(attributePaths = {"invoice", "partner", "reversedPayment"})
    Page<Payment> findAll(Specification<Payment> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"invoice", "partner", "reversedPayment"})
    Optional<Payment> findWithInvoiceById(UUID id);

    /** Locks the entry, so concurrent reversals of one receipt queue and the second one sees the first. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> lock(@Param("id") UUID id);

    Optional<Payment> findByIdempotencyKey(UUID idempotencyKey);

    @EntityGraph(attributePaths = {"invoice", "partner"})
    Optional<Payment> findByReversedPaymentId(UUID reversedPaymentId);

    @Query("select p.reversedPayment.id from Payment p where p.reversedPayment.id in :ids")
    List<UUID> reversedAmong(@Param("ids") Collection<UUID> ids);

    /** Receipts of the invoice that have not been reversed. */
    @Query("select p from Payment p where p.invoice.id = :invoiceId"
            + " and p.entryType = com.iotiq.partnererp.modules.payments.entity.PaymentEntryType.RECEIPT"
            + " and not exists (select r.id from Payment r where r.reversedPayment = p) order by p.paymentDate, p.createdAt")
    List<Payment> liveReceipts(@Param("invoiceId") UUID invoiceId);

    /** Unreversed receipts carrying this reference (case-insensitive), on any invoice. */
    @EntityGraph(attributePaths = {"invoice"})
    @Query("select p from Payment p where lower(p.referenceNumber) = :reference"
            + " and p.entryType = com.iotiq.partnererp.modules.payments.entity.PaymentEntryType.RECEIPT"
            + " and not exists (select r.id from Payment r where r.reversedPayment = p)")
    List<Payment> liveReceiptsWithReference(@Param("reference") String reference);

    @Query(value = "select coalesce(sum(amount), 0) from payments where invoice_id = :invoiceId", nativeQuery = true)
    BigDecimal paidAmount(@Param("invoiceId") UUID invoiceId);

    @Query(value = "select coalesce(sum(net_amount), 0) from payments where invoice_id = :invoiceId", nativeQuery = true)
    BigDecimal netAmount(@Param("invoiceId") UUID invoiceId);

    @Query(value = "select count(*) from partner_payouts where payment_id = :paymentId", nativeQuery = true)
    long payoutCount(@Param("paymentId") UUID paymentId);

    /**
     * Receivables over live issued invoices: an invoice is outstanding while its balance is above the ₹0.50 paid
     * tolerance (BR-V6); overdue when its due date is before {@code today}.
     */
    @Query(value = """
            select count(*) as outstandingInvoices,
                   coalesce(sum(b.balance), 0) as outstandingAmount,
                   count(*) filter (where b.due_date < :today) as overdueInvoices,
                   coalesce(sum(b.balance) filter (where b.due_date < :today), 0) as overdueAmount
              from (select i.due_date, i.grand_total - coalesce(sum(p.amount), 0) as balance
                      from invoices i left join payments p on p.invoice_id = i.id
                     where i.status = 'ISSUED' and i.deleted_at is null
                     group by i.id, i.due_date, i.grand_total) b
             where b.balance > 0.50
            """, nativeQuery = true)
    Receivables receivables(@Param("today") LocalDate today);

    interface Receivables {
        long getOutstandingInvoices();

        BigDecimal getOutstandingAmount();

        long getOverdueInvoices();

        BigDecimal getOverdueAmount();
    }
}
