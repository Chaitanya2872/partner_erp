package com.iotiq.partnererp.modules.invoices.service;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.enums.InvoiceStatus;
import com.iotiq.partnererp.modules.estimates.dto.TotalsResponse;
import com.iotiq.partnererp.modules.estimates.service.EstimateDocumentRenderer;
import com.iotiq.partnererp.modules.invoices.dto.InternalInvoiceDocument;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceParty;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceResponse;
import com.iotiq.partnererp.modules.invoices.dto.InvoiceTotals;
import com.iotiq.partnererp.modules.invoices.tax.TaxType;
import com.iotiq.partnererp.modules.settings.entity.CompanySettings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static com.iotiq.partnererp.modules.estimates.service.EstimateDocumentRenderer.esc;
import static com.iotiq.partnererp.modules.estimates.service.EstimateDocumentRenderer.inr;

/**
 * Printable HTML for invoices (source docHtml / DOC_CSS), printed from the browser (D-15, temporary). Always
 * paper-white.
 *
 * <p>{@link #partner} receives only the partner-safe {@link InvoiceResponse}, so it can't print B2C data. Issued and
 * cancelled invoices print their frozen snapshot; drafts are marked "Draft · not a tax invoice" and carry no number.
 * All text is escaped.
 */
public final class InvoiceDocumentRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final String CSS = EstimateDocumentRenderer.CSS + """
            .warn{border:2px solid #9b1c1c;color:#9b1c1c;padding:6px 10px;margin-bottom:12px;font-weight:700}
            .pos{margin-bottom:12px}
            td.s,th.s{font-size:11.5px}
            """;

    private InvoiceDocumentRenderer() {
    }

    /** Partner copy: partner names and rates, the tax breakdown per line and in total. */
    public static String partner(InvoiceResponse i, CompanySettings company) {
        boolean inter = i.tax().taxType() == TaxType.INTER;
        String stateTax = i.tax().stateTaxLabel() == null ? "SGST" : i.tax().stateTaxLabel();
        StringBuilder rows = new StringBuilder();
        for (InvoiceResponse.Line l : i.lines()) {
            rows.append("<tr><td>").append(l.lineNo()).append("</td><td>").append(esc(l.description()))
                    .append("</td><td>").append(esc(l.hsn())).append("</td><td class=\"n\">").append(num(l.quantity()))
                    .append(' ').append(esc(l.unit())).append("</td><td class=\"n\">").append(inr(l.unitPrice()))
                    .append("</td><td class=\"n\">").append(inr(l.taxableValue())).append("</td><td class=\"n\">")
                    .append(num(l.gstPercent())).append("%</td>");
            if (inter) {
                rows.append("<td class=\"n\">").append(inr(l.igstAmount())).append("</td>");
            } else {
                rows.append("<td class=\"n\">").append(inr(l.cgstAmount())).append("</td><td class=\"n\">")
                        .append(inr(l.sgstAmount())).append("</td>");
            }
            rows.append("<td class=\"n\">").append(inr(l.lineTotal())).append("</td></tr>");
        }
        String head = "<tr><th>#</th><th>Description</th><th>HSN/SAC</th><th class=\"n\">Qty</th>"
                + "<th class=\"n\">Rate</th><th class=\"n\">Taxable</th><th class=\"n\">GST</th>"
                + (inter ? "<th class=\"n\">IGST</th>" : "<th class=\"n\">CGST</th><th class=\"n\">" + stateTax + "</th>")
                + "<th class=\"n\">Total</th></tr>";
        String title = i.status() == InvoiceStatus.DRAFT ? "DRAFT INVOICE" : "TAX INVOICE";
        StringBuilder warn = new StringBuilder();
        if (i.status() == InvoiceStatus.DRAFT) {
            warn.append("<div class=\"warn\">Draft · not a tax invoice. It gets a number when it is issued.</div>");
        } else if (i.status() == InvoiceStatus.CANCELLED) {
            warn.append("<div class=\"warn\">CANCELLED on ").append(esc(DATE.format(i.cancelledAt()
                    .atZone(AppConstants.BUSINESS_ZONE))))
                    .append(i.cancelReason() == null ? "" : ". Reason: " + esc(i.cancelReason())).append("</div>");
        }
        String pos = i.placeOfSupplyCode() == null ? "Not chosen yet"
                : esc(i.placeOfSupplyCode() + " · " + i.placeOfSupplyName());
        String body = "<div class=\"doc\">" + warn
                + header(i.supplier(), company, title, i.invoiceNumber() == null ? "Draft" : i.invoiceNumber(),
                i.invoiceDate(), i.dueDate(), null)
                + parties(i) + "<div class=\"pos\"><span class=\"lab\">Place of supply</span> " + pos
                + (i.tax().taxTypeLabel() == null ? "" : " · " + esc(i.tax().taxTypeLabel())) + "</div>"
                + "<table><thead>" + head + "</thead><tbody>" + rows + "</tbody></table>"
                + totals(i.totals(), i.discountPercent(), inter, stateTax)
                + (i.notes() == null ? "" : "<div class=\"note\">" + esc(i.notes()) + "</div>") + "</div>";
        return page(i.invoiceNumber() == null ? "Draft invoice" : i.invoiceNumber(), body);
    }

    /** Internal B2C copy of an issued invoice: B2C names and rates, the partner copy name per line, the uplift. */
    public static String internal(InvoiceResponse i, InternalInvoiceDocument twin, CompanySettings company) {
        StringBuilder rows = new StringBuilder();
        for (InternalInvoiceDocument.Line l : twin.lines()) {
            rows.append("<tr><td>").append(l.lineNo()).append("</td><td>").append(esc(l.b2cName()));
            if (l.partnerName() != null) {
                rows.append("<div class=\"sub\">Partner copy name: ").append(esc(l.partnerName())).append("</div>");
            }
            if (l.b2cPriceMissing()) {
                rows.append("<div class=\"sub\">No B2C price set</div>");
            }
            rows.append("</td><td>").append(esc(l.hsn())).append("</td><td class=\"n\">").append(num(l.quantity()))
                    .append(' ').append(esc(l.unit())).append("</td><td class=\"n\">").append(inr(l.rate()))
                    .append("</td><td class=\"n\">").append(num(l.gstPercent())).append("%</td><td class=\"n\">")
                    .append(inr(l.amount())).append("</td></tr>");
        }
        String stamp = "<div class=\"stamp\">" + EstimateDocumentRenderer.INTERNAL_STAMP
                + " Partner discount vs B2C list: " + inr(twin.uplift())
                + (twin.b2cIncomplete() ? ". " + twin.missingB2cPrices()
                + " line(s) have no B2C price, so this comparison is incomplete." : "") + "</div>";
        TotalsResponse t = twin.b2cTotals();
        String totals = "<div class=\"tt\"><div><span>Subtotal</span><span>" + inr(t.subtotal()) + "</span></div>"
                + (t.discountAmount().signum() == 0 ? "" : "<div><span>Discount</span><span>−"
                + inr(t.discountAmount()) + "</span></div>")
                + "<div><span>Taxable value</span><span>" + inr(t.taxableAmount()) + "</span></div>"
                + "<div><span>GST</span><span>" + inr(t.gstAmount()) + "</span></div>"
                + "<div class=\"g\"><span>Total</span><span>" + inr(t.grandTotal()) + "</span></div></div>";
        String body = "<div class=\"doc int\">" + stamp
                + header(i.supplier(), company, "INVOICE (INTERNAL B2C COPY)", twin.internalNumber(),
                twin.documentDate(), i.dueDate(), i.invoiceNumber())
                + parties(i) + "<table><thead><tr><th>#</th><th>Description</th><th>HSN/SAC</th>"
                + "<th class=\"n\">Qty</th><th class=\"n\">Rate</th><th class=\"n\">GST</th><th class=\"n\">Amount</th>"
                + "</tr></thead><tbody>" + rows + "</tbody></table>" + totals + "</div>";
        return page(twin.internalNumber(), body);
    }

    private static String page(String title, String body) {
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>" + esc(title)
                + "</title><style>" + CSS + "</style></head><body>" + body + "</body></html>";
    }

    private static String header(InvoiceParty supplier, CompanySettings c, String title, String number,
                                 LocalDate date, LocalDate due, String partnerDocument) {
        StringBuilder contact = new StringBuilder(esc(c.getPhone()));
        if (c.getEmail() != null && !c.getEmail().isBlank()) {
            contact.append(" · ").append(esc(c.getEmail()));
        }
        StringBuilder reg = new StringBuilder();
        if (supplier.gstin() != null) {
            reg.append("GSTIN ").append(esc(supplier.gstin()));
        }
        if (supplier.stateCode() != null) {
            reg.append(reg.isEmpty() ? "" : " · ").append("State ").append(esc(supplier.stateCode() + " · "
                    + supplier.stateName()));
        }
        return "<div class=\"hd\"><div><h2>" + esc(supplier.name()) + "</h2><div class=\"note\" style=\"margin:0\">"
                + esc(supplier.address()) + "</div><div>" + contact + "</div><div>" + reg + "</div></div>"
                + "<div class=\"meta\"><h2 style=\"font-size:18px\">" + esc(title) + "</h2><div><b>" + esc(number)
                + "</b></div><div>Date " + date(date) + "</div><div>Due " + date(due) + "</div>"
                + (partnerDocument == null ? "" : "<div>Partner document: " + esc(partnerDocument) + "</div>")
                + "</div></div>";
    }

    /** Billed to (the customer's billing snapshot) and the referring partner. */
    private static String parties(InvoiceResponse i) {
        InvoiceParty b = i.billing();
        StringBuilder billed = new StringBuilder("<b>").append(esc(b.name() == null ? "—" : b.name())).append("</b>");
        if (b.address() != null) {
            billed.append("<div class=\"note\" style=\"margin:0\">").append(esc(b.address())).append("</div>");
        }
        if (b.stateCode() != null) {
            billed.append("<div>State ").append(esc(b.stateCode() + " · " + b.stateName())).append("</div>");
        }
        billed.append("<div>").append(b.gstin() == null ? "Unregistered (no GSTIN)" : "GSTIN " + esc(b.gstin()))
                .append("</div>");
        return "<div class=\"parties\"><div><div class=\"lab\">Billed to</div>" + billed
                + "</div><div><div class=\"lab\">Referred by</div><b>" + esc(i.partner().name()) + "</b>"
                + (i.estimate() == null ? "" : "<div>Estimate " + esc(i.estimate().estimateNumber()) + "</div>")
                + "</div></div>";
    }

    private static String totals(InvoiceTotals t, BigDecimal discountPercent, boolean inter, String stateTax) {
        StringBuilder out = new StringBuilder("<div class=\"tt\"><div><span>Subtotal</span><span>")
                .append(inr(t.subtotal())).append("</span></div>");
        if (t.discountAmount().signum() != 0) {
            out.append("<div><span>Discount (").append(num(discountPercent)).append("%)</span><span>−")
                    .append(inr(t.discountAmount())).append("</span></div>");
        }
        out.append("<div><span>Taxable value</span><span>").append(inr(t.taxableAmount())).append("</span></div>");
        if (inter) {
            out.append("<div><span>IGST</span><span>").append(inr(t.igstAmount())).append("</span></div>");
        } else {
            out.append("<div><span>CGST</span><span>").append(inr(t.cgstAmount())).append("</span></div>")
                    .append("<div><span>").append(stateTax).append("</span><span>").append(inr(t.sgstAmount()))
                    .append("</span></div>");
        }
        return out.append("<div class=\"g\"><span>Total</span><span>").append(inr(t.grandTotal()))
                .append("</span></div></div>").toString();
    }

    private static String num(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String date(LocalDate value) {
        return value == null ? "—" : value.format(DATE);
    }
}
