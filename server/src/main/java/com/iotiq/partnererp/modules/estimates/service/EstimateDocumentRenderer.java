package com.iotiq.partnererp.modules.estimates.service;

import com.iotiq.partnererp.common.enums.BillTo;
import com.iotiq.partnererp.common.persistence.LineKind;
import com.iotiq.partnererp.common.util.AmountInWords;
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
 * Printable HTML for estimates, laid out like the owner's estimate template (dark header band, three detail cards,
 * items table, installation section, tax and totals box with the amount in words, terms and notes, signatures and
 * footer). A4 and print friendly; self-contained.
 *
 * <p>{@link #partner} receives only the partner-safe {@link EstimateResponse}, so it can't print B2C data. The
 * internal copy additionally takes the twin and carries the source's "INTERNAL ONLY" stamp. All text is escaped.
 * Parts of the template we hold no data for (payment milestones, bank details, tier slabs) are not printed, and there
 * is no round-off line because totals are not rounded to whole rupees.
 */
public final class EstimateDocumentRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    public static final String INTERNAL_STAMP = "INTERNAL ONLY. Shows B2C product names and prices. Never send to the "
            + "partner or client.";
    public static final String CSS = """
            :root{--dark:#11271c;--gold:#c39c67;--ivory:#f4efe2;--line:#cfc8b6;--text:#1f1f1f;--muted:#5a5a52;--warn:#7a2d22}
            *{box-sizing:border-box}
            @page{size:A4;margin:8mm 0}
            html,body{margin:0}
            body{font-family:Calibri,"Segoe UI",Roboto,Arial,sans-serif;background:#e6e2d6;color:var(--text);font-size:12px;line-height:1.35;-webkit-print-color-adjust:exact;print-color-adjust:exact}
            .doc{background:#fff;color:var(--text);max-width:210mm;min-height:297mm;margin:12px auto 40px;box-shadow:0 2px 14px rgba(0,0,0,.18);padding:0 0 18px}
            .doc>*:not(.hd):not(.stamp):not(.warn){margin-left:20px;margin-right:20px}
            .hd{display:flex;justify-content:space-between;align-items:center;gap:16px;background:var(--dark);color:var(--ivory);padding:16px 20px;border-bottom:4px solid var(--gold);margin-bottom:14px}
            .hd h2{margin:0;color:var(--ivory);font-size:18px}
            .hd .co{font-size:10px;color:#d8d2bf;margin-top:4px}
            .meta{text-align:right}
            .meta h2{color:var(--gold);font-size:22px!important;letter-spacing:.18em}
            .meta .s{font-size:11px;color:#d8d2bf}
            .parties,.grid3{display:grid;gap:10px;margin-bottom:12px}
            .grid3{grid-template-columns:1fr 1fr 1fr}
            .parties{grid-template-columns:1fr 1fr}
            .card,.parties>div{border:1px solid var(--line);border-radius:4px;overflow:hidden;padding:0 8px 5px}
            .card h4,.parties .lab{display:block;margin:0 -8px 5px;background:var(--ivory);color:var(--dark);font-size:10px;letter-spacing:.1em;text-transform:uppercase;padding:5px 8px;border-bottom:1px solid var(--line)}
            .kv{display:flex;gap:6px;padding:2px 0;align-items:baseline}
            .kv .k{flex:0 0 74px;color:var(--muted);font-size:10.5px}
            .kv .v{flex:1;min-width:0;font-weight:600;word-break:break-word}
            .lab{font-size:10.5px;letter-spacing:.1em;text-transform:uppercase;color:var(--muted)}
            table{border-collapse:collapse;width:100%;margin-bottom:10px}
            th{background:var(--dark);color:var(--ivory);text-align:left;padding:5px;font-size:9.5px;letter-spacing:.06em;text-transform:uppercase;font-weight:600}
            td{padding:4px 5px;border-bottom:1px solid var(--line);vertical-align:top;font-size:11.5px}
            tr{break-inside:avoid}
            .n{text-align:right;white-space:nowrap;font-variant-numeric:tabular-nums}
            td.s,th.s{font-size:11.5px}
            tr.subtotal td{background:var(--ivory);font-weight:700}
            .tot{display:grid;grid-template-columns:1.25fr 1fr;gap:14px;margin-top:6px;break-inside:avoid}
            .tt{border:1px solid var(--line);border-radius:4px;overflow:hidden;width:100%}
            .tt div{display:flex;justify-content:space-between;padding:4px 9px;border-bottom:1px solid var(--line);font-variant-numeric:tabular-nums}
            .tt .g{background:var(--dark);color:var(--ivory);font-weight:700;font-size:14px;padding:8px 9px;border-bottom:0}
            .tt .m{color:var(--muted)}
            .words{margin-top:6px;font-size:11px}
            .words b,.h3{color:var(--dark)}
            .h3{margin:12px 0 5px;font-size:11px;letter-spacing:.1em;text-transform:uppercase;font-weight:700;border-bottom:2px solid var(--gold);padding-bottom:3px;break-after:avoid}
            .hint{color:var(--muted);font-size:10px;margin-top:3px}
            .note{margin-top:12px;font-size:12px;white-space:pre-wrap}
            ol.terms{margin:0;padding-left:18px}
            ol.terms li{margin:2px 0}
            .two{display:grid;grid-template-columns:1fr 1fr;gap:16px;break-inside:avoid}
            .int{outline:2px dashed var(--warn);outline-offset:-6px}
            .stamp{background:#fff7f5;border-top:1px solid var(--warn);border-bottom:1px solid var(--warn);color:var(--warn);padding:6px 20px;margin-bottom:0;font-weight:700;text-align:center;letter-spacing:.04em}
            .sub{display:block;font-size:10px;color:var(--muted);margin-top:1px}
            .inst{margin:14px 20px 5px;font-size:11px;color:var(--dark);text-transform:uppercase;letter-spacing:.1em;border-bottom:2px solid var(--gold);padding-bottom:3px}
            .sign{display:flex;justify-content:space-between;gap:30px;margin-top:26px;break-inside:avoid}
            .sign div{flex:1;border-top:1px solid var(--text);padding-top:4px;font-size:10.5px;color:var(--muted)}
            .foot{margin-top:14px;padding-top:8px;border-top:2px solid var(--gold);font-size:10px;color:var(--muted);display:flex;justify-content:space-between;gap:16px}
            @media print{body{background:#fff}.doc{margin:0;box-shadow:none;max-width:none;min-height:0;padding-bottom:0}}
            @media screen and (max-width:820px){.doc{max-width:100%}.grid3,.tot,.two,.parties{grid-template-columns:1fr}}
            """;

    private EstimateDocumentRenderer() {
    }

    /** Partner copy: partner names and rates only. */
    public static String partner(EstimateResponse e, CompanySettings company) {
        StringBuilder product = new StringBuilder();
        StringBuilder install = new StringBuilder();
        BigDecimal installTotal = BigDecimal.ZERO;
        for (EstimateResponse.Line l : e.lines()) {
            boolean installation = l.kind() == LineKind.INSTALLATION;
            if (installation) {
                installTotal = installTotal.add(l.amount());
            }
            StringBuilder rows = installation ? install : product;
            rows.append("<tr><td>").append(l.lineNo()).append("</td><td>").append(esc(l.sku()))
                    .append("</td><td>").append(esc(l.description()))
                    .append("</td><td>").append(esc(l.hsn())).append("</td><td class=\"n\">")
                    .append(qty(l.quantity())).append(' ').append(esc(l.unit())).append("</td><td class=\"n\">")
                    .append(inr(l.unitPrice())).append("</td><td class=\"n\">").append(pct(l.gstPercent()))
                    .append("%</td><td class=\"n\">").append(inr(l.amount())).append("</td></tr>");
        }
        String head = "<thead><tr><th>#</th><th>Code</th><th>Description</th><th>HSN</th><th class=\"n\">Qty</th>"
                + "<th class=\"n\">Rate (₹)</th><th class=\"n\">GST</th><th class=\"n\">Amount (₹)</th></tr></thead>";
        String body = "<div class=\"doc\">" + header(company, "ESTIMATE", "Partner copy", e.estimateNumber(),
                e.estimateDate(), e.validUntil(), null)
                + cards(e) + table(head, product, install, 7, installTotal)
                + totalsBlock(e.totals(), e.discountPercent(), e.igst())
                + notesAndTerms(e.notes(), company) + closing(company, "Accepted by — authorised signatory")
                + "</div>";
        return page(e.estimateNumber(), body);
    }

    /** Internal B2C copy (source vm(…,'int')): B2C names and rates, the partner copy name per line, the uplift. */
    public static String internal(EstimateResponse e, InternalEstimateDocument twin, CompanySettings company) {
        StringBuilder product = new StringBuilder();
        StringBuilder install = new StringBuilder();
        BigDecimal installTotal = BigDecimal.ZERO;
        for (InternalEstimateDocument.Line l : twin.lines()) {
            boolean installation = l.kind() == LineKind.INSTALLATION;
            if (installation) {
                installTotal = installTotal.add(l.amount());
            }
            StringBuilder rows = installation ? install : product;
            rows.append("<tr><td>").append(l.lineNo()).append("</td><td>").append(esc(l.b2cName()));
            if (!installation && l.partnerName() != null) {
                rows.append("<span class=\"sub\">Partner copy name: ").append(esc(l.partnerName())).append("</span>");
            }
            if (l.b2cPriceMissing()) {
                rows.append("<span class=\"sub\">No B2C price set</span>");
            }
            rows.append("</td><td>").append(esc(l.hsn())).append("</td><td class=\"n\">").append(qty(l.quantity()))
                    .append(' ').append(esc(l.unit())).append("</td><td class=\"n\">").append(inr(l.rate()))
                    .append("</td><td class=\"n\">").append(pct(l.gstPercent())).append("%</td><td class=\"n\">")
                    .append(inr(l.amount())).append("</td></tr>");
        }
        String head = "<thead><tr><th>#</th><th>Description</th><th>HSN</th><th class=\"n\">Qty</th>"
                + "<th class=\"n\">Rate (₹)</th><th class=\"n\">GST</th><th class=\"n\">Amount (₹)</th></tr></thead>";
        String stamp = "<div class=\"stamp\">" + INTERNAL_STAMP + " Partner discount vs B2C list: " + inr(twin.uplift())
                + (twin.b2cIncomplete() ? ". " + twin.missingB2cPrices()
                + " line(s) have no B2C price, so this comparison is incomplete." : "") + "</div>";
        String body = "<div class=\"doc int\">" + stamp + header(company, "ESTIMATE", "Internal B2C copy",
                twin.internalNumber(), twin.documentDate(), e.validUntil(), e.estimateNumber())
                + cards(e) + table(head, product, install, 6, installTotal)
                + totalsBlock(twin.b2cTotals(), twin.discountPercent(), twin.igst())
                + notesAndTerms(null, company) + closing(company, "Reviewed by") + "</div>";
        return page(twin.internalNumber(), body);
    }

    private static String page(String title, String body) {
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>" + esc(title)
                + "</title><style>" + CSS + "</style></head><body>" + body + "</body></html>";
    }

    private static String header(CompanySettings c, String title, String subtitle, String number, LocalDate date,
                                 LocalDate validUntil, String partnerDocument) {
        StringBuilder contact = new StringBuilder(esc(c.getPhone()));
        if (c.getEmail() != null && !c.getEmail().isBlank()) {
            contact.append(" · ").append(esc(c.getEmail()));
        }
        if (c.getGstin() != null && !c.getGstin().isBlank()) {
            contact.append(" · GSTIN ").append(esc(c.getGstin()));
        }
        return "<div class=\"hd\"><div><h2>" + esc(c.getCompanyName()) + "</h2><div class=\"co\">" + esc(c.getAddress())
                + "</div><div class=\"co\">" + contact + "</div></div><div class=\"meta\"><h2>" + esc(title)
                + "</h2><div class=\"s\">" + esc(subtitle) + "</div><div><b>" + esc(number) + "</b></div>"
                + "<div class=\"s\">Date " + date(date) + " · Valid till " + date(validUntil) + "</div>"
                + (partnerDocument == null ? "" : "<div class=\"s\">Partner document: " + esc(partnerDocument) + "</div>")
                + "</div></div>";
    }

    private static String kv(String key, String valueHtml) {
        return "<div class=\"kv\"><span class=\"k\">" + key + "</span><span class=\"v\">" + valueHtml + "</span></div>";
    }

    /** Three cards as in the template: the referring partner, the client or project, and the estimate details. */
    private static String cards(EstimateResponse e) {
        EstimateLeadRef lead = e.lead();
        boolean partnerBuys = e.billTo() == BillTo.PARTNER;
        String name = partnerBuys ? e.partner().name() : lead == null ? "—" : lead.clientName();
        StringBuilder client = new StringBuilder(kv("Name", esc(name)));
        if (!partnerBuys && lead != null && lead.clientPhone() != null) {
            client.append(kv("Phone", esc(lead.clientPhone())));
        }
        if (lead != null && lead.city() != null) {
            client.append(kv("Location", esc(lead.city())));
        }
        if (lead != null && lead.projectTypeLabel() != null) {
            client.append(kv("Project", esc(lead.projectTypeLabel())));
        }
        if (lead != null && lead.leadNumber() != null) {
            client.append(kv("Lead", esc(lead.leadNumber())));
        }
        StringBuilder details = new StringBuilder(kv("Estimate no.", esc(e.estimateNumber())))
                .append(kv("Date", date(e.estimateDate()))).append(kv("Valid until", date(e.validUntil())))
                .append(kv("Supply", e.igst() ? "Inter-state (IGST)" : "Intra-state (CGST + SGST)"));
        if (e.createdByName() != null) {
            details.append(kv("Prepared by", esc(e.createdByName())));
        }
        return "<div class=\"grid3\"><div class=\"card\"><h4>Referred by</h4>" + kv("Partner", esc(e.partner().name()))
                + "</div><div class=\"card\"><h4>" + (partnerBuys ? "Billed to partner" : "Client / project") + "</h4>"
                + client + "</div><div class=\"card\"><h4>Estimate details</h4>" + details + "</div></div>";
    }

    /** Product lines first; then, if there are any, the "Installation" section with its own subtotal. */
    private static String table(String head, StringBuilder product, StringBuilder install, int labelSpan,
                                BigDecimal installSubtotal) {
        StringBuilder out = new StringBuilder();
        if (product.length() > 0 || install.length() == 0) {
            out.append("<table>").append(head).append("<tbody>").append(product).append("</tbody></table>");
        }
        if (install.length() > 0) {
            out.append("<h3 class=\"inst\">Installation</h3><table>").append(head).append("<tbody>").append(install)
                    .append("<tr class=\"subtotal\"><td colspan=\"").append(labelSpan)
                    .append("\" class=\"n\">Installation subtotal</td><td class=\"n\">")
                    .append(inr(installSubtotal)).append("</td></tr></tbody></table>");
        }
        return out.toString();
    }

    /** The totals box (CGST + SGST or IGST) and the grand total in words. */
    private static String totalsBlock(TotalsResponse t, BigDecimal discountPercent, boolean igst) {
        StringBuilder out = new StringBuilder("<div class=\"tot\"><div><div class=\"words\"><b>Amount in words:</b> ")
                .append(esc(AmountInWords.rupees(t.grandTotal()))).append("</div><div class=\"hint\">")
                .append(igst ? "Inter-state supply (IGST)" : "Intra-state supply (CGST + SGST)")
                .append("</div></div><div class=\"tt\"><div><span>Subtotal</span><span>")
                .append(inr(t.subtotal())).append("</span></div>");
        if (t.discountAmount().signum() != 0) {
            out.append("<div class=\"m\"><span>Less: discount (").append(pct(discountPercent)).append("%)</span><span>−")
                    .append(inr(t.discountAmount())).append("</span></div>");
        }
        out.append("<div><b>Taxable value</b><b>").append(inr(t.taxableAmount())).append("</b></div>");
        if (igst) {
            out.append("<div><span>IGST</span><span>").append(inr(t.gstAmount())).append("</span></div>");
        } else {
            out.append("<div><span>CGST</span><span>").append(inr(t.cgstAmount())).append("</span></div>")
                    .append("<div><span>SGST</span><span>").append(inr(t.sgstAmount())).append("</span></div>");
        }
        return out.append("<div class=\"g\"><span>GRAND TOTAL</span><span>").append(inr(t.grandTotal()))
                .append("</span></div></div></div>").toString();
    }

    /** Notes and the company's standing terms (one per line), side by side when both exist. */
    public static String notesAndTerms(String notes, CompanySettings company) {
        StringBuilder terms = new StringBuilder();
        if (company.getTerms() != null) {
            for (String line : company.getTerms().split("\\R")) {
                if (!line.isBlank()) {
                    terms.append("<li>").append(esc(line.strip())).append("</li>");
                }
            }
        }
        StringBuilder out = new StringBuilder();
        if (notes != null || terms.length() > 0) {
            out.append("<div class=\"two\">");
            if (terms.length() > 0) {
                out.append("<div><div class=\"h3\">Terms &amp; conditions</div><ol class=\"terms\">").append(terms)
                        .append("</ol></div>");
            }
            if (notes != null) {
                out.append("<div><div class=\"h3\">Notes</div><div class=\"note\" style=\"margin-top:0\">")
                        .append(esc(notes)).append("</div></div>");
            }
            out.append("</div>");
        }
        return out.toString();
    }

    /** Signature lines and the company footer. */
    public static String closing(CompanySettings c, String right) {
        return "<div class=\"sign\"><div>Prepared by — " + esc(c.getCompanyName()) + "</div><div>" + esc(right)
                + "</div></div><div class=\"foot\"><span><b>" + esc(c.getCompanyName()) + "</b> · "
                + esc(c.getAddress()) + "</span><span>" + esc(c.getPhone())
                + (c.getEmail() == null || c.getEmail().isBlank() ? "" : " · " + esc(c.getEmail()))
                + "</span></div>";
    }

    public static String esc(String value) {
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
    public static String inr(BigDecimal amount) {
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
