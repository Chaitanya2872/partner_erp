package com.iotiq.partnererp.modules.invoices.repository;

import com.iotiq.partnererp.modules.invoices.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
}
