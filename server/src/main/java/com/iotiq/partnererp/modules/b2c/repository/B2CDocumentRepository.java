package com.iotiq.partnererp.modules.b2c.repository;

import com.iotiq.partnererp.modules.b2c.entity.B2CDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface B2CDocumentRepository extends JpaRepository<B2CDocument, UUID> {

    Optional<B2CDocument> findByEstimateId(UUID estimateId);

    Optional<B2CDocument> findByInvoiceId(UUID invoiceId);
}
