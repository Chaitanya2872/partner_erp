package com.iotiq.partnererp.modules.payments.repository;

import com.iotiq.partnererp.modules.payments.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByInvoiceIdOrderByPaymentDateAsc(UUID invoiceId);
}
