package com.iotiq.partnererp.modules.estimates.service;

import com.iotiq.partnererp.common.enums.BillTo;
import com.iotiq.partnererp.modules.estimates.dto.EstimateLeadRef;
import com.iotiq.partnererp.modules.estimates.dto.EstimateResponse;
import com.iotiq.partnererp.modules.estimates.dto.InternalEstimateDocument;
import com.iotiq.partnererp.modules.estimates.dto.TotalsResponse;
import com.iotiq.partnererp.modules.settings.entity.CompanySettings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Printable HTML for estimates (source docHtml / DOC_CSS, "Download HTML"). Always paper-white.
 *
 * <p>{@link #partner} receives only the partner-safe {@link EstimateResponse}, so it can't print B2C data. The
 * internal copy additionally takes the twin and carries the source's "INTERNAL ONLY" stamp. All text is escaped.
 */
public final class EstimateDocumentRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    static final String INTERNAL_STAMP = "INTERNAL ONLY. Shows B2C product names and prices. Never send to the "
            + "partner or client.";
    private static final String CSS = """
            body{font-family:Arial,sans-serif;margin:24px;background:#fff;color:#1a1a1a}
            .doc{background:#fff;color:#1a1a1a;font:13px/1.45 Arial,sans-serif;max-width:900px;margin:0 auto}
            .doc h2{margin:0;color:#11271c}
            .hd{display:flex;justify-content:space-between;gap:16px;border-bottom:3px solid #c39c67;padding-bottom:10px;margin-bottom:12px}
            .meta{text-align:right}
            .parties{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-bottom:12px}
            .lab{font-size:10.5px;letter-spacing:.1em;text-transform:uppercase;color:#6b6b6b}
            table{border-collapse:collapse;width:100%}
            th{background:#11271c;color:#f4efe2;text-align:left;padding:6px 8px;font-size:11.5px}
            td{padding:6px 8px;border-bottom:1px solid #e4dfd0;vertical-align:top}
            .n{text-align:right;white-space:nowrap}
            .tt{margin-left:auto;width:300px;max-width:100%}
            .tt div{display:flex;justify-content:space-between;padding:2px 0}
            .tt .g{border-top:2px solid #11271c;font-weight:700;font-size:15px}
            .note{margin-top:12px;font-size:12px;white-space:pre-wrap}
            .int{border:2px dashed #a8680f;padding:10px}
            .stamp{border:1px solid #a8680f;background:#fff7e8;color:#6b4206;padding:8px 10px;margin-bottom:12px;font-weight:700}
            .sub{font-size:11px;color:#777}
            @media print{body{margin:0}}
            """;

    private EstimateDocumentRenderer() {
    }

    /** Partner copy: partner names and rates only. */
    public static String partner(EstimateResponse e, CompanySettings company) {
        StringBuilder rows = new StringBuilder();
        for (EstimateResponse.Line l : e.lines()) {
            rows.append("<tr><td>").append(l.lineNo()).append("</td><td>").append(esc(l.description()))
                    .append("</td><td>").append(esc(l.hsn())).append("</td><td class=\"n\">")
                    .append(qty(l.quantity())).append(' ').append(esc(l.unit())).append("</td><td class=\"n\">")
                    .append(inr(l.unitPrice())).append("</td><td class=\"n\">").append(pct(l.gstPercent()))
                    .append("%</td><td class=\"n\">").append(inr(l.amount())).append("</td></tr>");
        }
        String body = "<div class=\"doc\">" + header(company, "ESTIMATE", e.estimateNumber(), e.estimateDate(),
                "Valid till " + date(e.validUntil()), null)
                + parties(e) + table(rows) + totals(e.totals(), e.discountPercent(), e.igst())
                + (e.notes() == null ? "" : "<div class=\"note\">" + esc(e.notes()) + "</div>") + "</div>";
        return page(e.estimateNumber(), body);
    }

    /** Internal B2C copy (source vm(…,'int')): B2C names and rates, the partner copy name per line, the uplift. */
    public static String internal(EstimateResponse e, InternalEstimateDocument twin, CompanySettings company) {
        StringBuilder rows = new StringBuilder();
        for (InternalEstimateDocument.Line l : twin.lines()) {
            rows.append("<tr><td>").append(l.lineNo()).append("</td><td>").append(esc(l.b2cName()));
            if (l.partnerName() != null) {
                rows.append("<div class=\"sub\">Partner copy name: ").append(esc(l.partnerName())).append("</div>");
            }
            if (l.b2cPriceMissing()) {
                rows.append("<div class=\"sub\">No B2C price set</div>");
            }
            rows.append("</td><td>").append(esc(l.hsn())).append("</td><td class=\"n\">").append(qty(l.quantity()))
                    .append(' ').append(esc(l.unit())).append("</td><td class=\"n\">").append(inr(l.rate()))
                    .append("</td><td class=\"n\">").append(pct(l.gstPercent())).append("%</td><td class=\"n\">")
                    .append(inr(l.amount())).append("</td></tr>");
        }
        String stamp = "<div class=\"stamp\">" + INTERNAL_STAMP + " Partner discount vs B2C list: " + inr(twin.uplift())
                + (twin.b2cIncomplete() ? ". " + twin.missingB2cPrices()
                + " line(s) have no B2C price, so this comparison is incomplete." : "") + "</div>";
        String body = "<div class=\"doc int\">" + stamp + header(company, "ESTIMATE (INTERNAL B2C COPY)",
                twin.internalNumber(), twin.documentDate(), "Valid till " + date(e.validUntil()), e.estimateNumber())
                + parties(e) + table(rows) + totals(twin.b2cTotals(), twin.discountPercent(), twin.igst()) + "</div>";
        return page(twin.internalNumber(), body);
    }

    private static String page(String title, String body) {
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>" + esc(title)
                + "</title><style>" + CSS + "</style></head><body>" + body + "</body></html>";
    }

    private static String header(CompanySettings c, String title, String number, LocalDate date, String extra,
                                 String partnerDocument) {
        StringBuilder contact = new StringBuilder(esc(c.getPhone()));
        if (c.getEmail() != null && !c.getEmail().isBlank()) {
            contact.append(" · ").append(esc(c.getEmail()));
        }
        if (c.getGstin() != null && !c.getGstin().isBlank()) {
            contact.append(" · GSTIN ").append(esc(c.getGstin()));
        }
        return "<div class=\"hd\"><div><h2>" + esc(c.getCompanyName()) + "</h2><div>" + esc(c.getAddress())
                + "</div><div>" + contact + "</div></div><div class=\"meta\"><h2 style=\"font-size:18px\">" + esc(title)
                + "</h2><div><b>" + esc(number) + "</b></div><div>Date " + date(date) + "</div><div>" + esc(extra)
                + "</div>" + (partnerDocument == null ? "" : "<div>Partner document: " + esc(partnerDocument) + "</div>")
                + "</div></div>";
    }

    /** Source parties block: the client (or the partner for bulk purchases) and the referring partner. */
    private static String parties(EstimateResponse e) {
        EstimateLeadRef lead = e.lead();
        boolean partnerBuys = e.billTo() == BillTo.PARTNER;
        String name = partnerBuys ? e.partner().name() : lead == null ? "—" : lead.clientName();
        StringBuilder details = new StringBuilder();
        if (!partnerBuys && lead != null && lead.clientPhone() != null) {
            details.append(esc(lead.clientPhone())).append(' ');
        }
        if (lead != null && lead.city() != null) {
            details.append(esc(lead.city()));
        }
        return "<div class=\"parties\"><div><div class=\"lab\">" + (partnerBuys ? "Billed to partner" : "Client")
                + "</div><b>" + esc(name) + "</b><div>" + details + "</div>"
                + (lead == null || lead.projectTypeLabel() == null ? "" : "<div>" + esc(lead.projectTypeLabel()) + "</div>")
                + "</div><div><div class=\"lab\">Referred by</div><b>" + esc(e.partner().name()) + "</b></div></div>";
    }

    private static String table(StringBuilder rows) {
        return "<table><thead><tr><th>#</th><th>Description</th><th>HSN</th><th class=\"n\">Qty</th>"
                + "<th class=\"n\">Rate</th><th class=\"n\">GST</th><th class=\"n\">Amount</th></tr></thead><tbody>"
                + rows + "</tbody></table>";
    }

    private static String totals(TotalsResponse t, BigDecimal discountPercent, boolean igst) {
        StringBuilder out = new StringBuilder("<div class=\"tt\"><div><span>Subtotal</span><span>")
                .append(inr(t.subtotal())).append("</span></div>");
        if (t.discountAmount().signum() != 0) {
            out.append("<div><span>Discount (").append(pct(discountPercent)).append("%)</span><span>−")
                    .append(inr(t.discountAmount())).append("</span></div>");
        }
        out.append("<div><span>Taxable value</span><span>").append(inr(t.taxableAmount())).append("</span></div>");
        if (igst) {
            out.append("<div><span>IGST</span><span>").append(inr(t.gstAmount())).append("</span></div>");
        } else {
            out.append("<div><span>CGST</span><span>").append(inr(t.cgstAmount())).append("</span></div>")
                    .append("<div><span>SGST</span><span>").append(inr(t.sgstAmount())).append("</span></div>");
        }
        return out.append("<div class=\"g\"><span>Total</span><span>").append(inr(t.grandTotal()))
                .append("</span></div></div>").toString();
    }

    static String esc(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** Indian digit grouping with two decimals, e.g. ₹12,34,567.50. */
    static String inr(BigDecimal amount) {
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        String sign = value.signum() < 0 ? "-" : "";
        String plain = value.abs().toPlainString();
        String whole = plain.substring(0, plain.indexOf('.'));
        String fraction = plain.substring(plain.indexOf('.'));
        StringBuilder grouped = new StringBuilder();
        if (whole.length() > 3) {
            String head = whole.substring(0, whole.length() - 3);
            for (int i = 0; i < head.length(); i++) {
                if (i > 0 && (head.length() - i) % 2 == 0) {
                    grouped.append(',');
                }
                grouped.append(head.charAt(i));
            }
            grouped.append(',').append(whole.substring(whole.length() - 3));
        } else {
            grouped.append(whole);
        }
        return sign + "₹" + grouped + fraction;
    }

    private static String qty(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String pct(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String date(LocalDate value) {
        return value == null ? "—" : value.format(DATE);
    }
}
