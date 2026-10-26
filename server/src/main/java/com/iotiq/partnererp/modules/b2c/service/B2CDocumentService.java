package com.iotiq.partnererp.modules.b2c.service;

import com.iotiq.partnererp.common.persistence.DocumentLine;
import com.iotiq.partnererp.common.persistence.DocumentTotals;
import com.iotiq.partnererp.common.util.DocumentCalculator;
import com.iotiq.partnererp.modules.b2c.entity.B2CDocument;
import com.iotiq.partnererp.modules.b2c.entity.B2CDocumentItem;
import com.iotiq.partnererp.modules.b2c.entity.B2CDocumentType;
import com.iotiq.partnererp.modules.b2c.repository.B2CDocumentRepository;
import com.iotiq.partnererp.modules.estimates.entity.Estimate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes the internal B2C twin of a saved document (source saveDoc, BUSINESS_RULES §6.6, FR-09).
 *
 * <p>The twin is written in the caller's transaction, so a failure here rolls the parent back too: there is never a
 * document without its twin, or a twin without its document. Lines copy the parent's saved snapshot (B2C name and
 * rate, partner name, HSN, unit, quantity, GST), never the current product.
 */
@Service
public class B2CDocumentService {

    public static final String INTERNAL_PREFIX = "INT-";

    private final B2CDocumentRepository repository;

    public B2CDocumentService(B2CDocumentRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public B2CDocument syncEstimate(Estimate estimate) {
        B2CDocument twin = repository.findByEstimateId(estimate.getId()).orElseGet(() -> {
            B2CDocument created = new B2CDocument();
            created.setDocumentType(B2CDocumentType.ESTIMATE);
            created.setEstimate(estimate);
            return created;
        });
        if (!twin.getItems().isEmpty()) {
            // Remove the old lines first: (document, line_no) is unique and inserts would run before deletes.
            twin.getItems().clear();
            repository.flush();
        }
        twin.setInternalNumber(INTERNAL_PREFIX + estimate.getEstimateNumber());
        twin.setParentNumber(estimate.getEstimateNumber());
        twin.setPartner(estimate.getPartner());
        twin.setLead(estimate.getLead());
        twin.setDocumentDate(estimate.getEstimateDate());
        twin.setDiscountPercent(estimate.getDiscountPercent());
        twin.setIgst(estimate.isIgst());
        fill(twin, estimate.getItems(), estimate.getTotals());
        return repository.saveAndFlush(twin);
    }

    @Transactional(readOnly = true)
    public Optional<B2CDocument> findForEstimate(UUID estimateId) {
        return repository.findByEstimateId(estimateId);
    }

    private static void fill(B2CDocument twin, List<? extends DocumentLine> lines, DocumentTotals partnerTotals) {
        boolean incomplete = false;
        for (DocumentLine line : lines) {
            B2CDocumentItem item = new B2CDocumentItem();
            item.setLineNo(line.getLineNo());
            item.setProduct(line.getProduct());
            item.setDescription(line.getB2cName());
            item.setPartnerName(line.getDescription());
            item.setHsn(line.getHsn());
            item.setUnit(line.getUnit());
            item.setQuantity(line.getQuantity());
            item.setUnitPrice(line.getB2cUnitPrice());
            item.setGstPercent(line.getGstPercent());
            item.setLineTotal(DocumentCalculator.lineAmount(line.getQuantity(), line.getB2cUnitPrice()));
            twin.addItem(item);
            incomplete |= line.getB2cUnitPrice().signum() == 0;
        }
        DocumentCalculator.Totals b2c = DocumentCalculator.totals(lines.stream()
                .map(l -> new DocumentCalculator.Line(l.getQuantity(), l.getB2cUnitPrice(), l.getGstPercent()))
                .toList(), twin.getDiscountPercent());
        twin.setB2cTotals(new DocumentTotals(b2c.subtotal(), b2c.discountAmount(), b2c.taxableAmount(),
                b2c.gstAmount(), b2c.grandTotal()));
        twin.setPartnerTotals(new DocumentTotals(partnerTotals.getSubtotal(), partnerTotals.getDiscountAmount(),
                partnerTotals.getTaxableAmount(), partnerTotals.getGstAmount(), partnerTotals.getGrandTotal()));
        twin.setUplift(b2c.taxableAmount().subtract(partnerTotals.getTaxableAmount()));
        twin.setB2cIncomplete(incomplete);
    }
}
