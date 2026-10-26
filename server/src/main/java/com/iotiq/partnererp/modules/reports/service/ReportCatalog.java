package com.iotiq.partnererp.modules.reports.service;

import com.iotiq.partnererp.common.enums.LeadStage;
import com.iotiq.partnererp.common.enums.PartnerStatus;
import com.iotiq.partnererp.common.enums.PaymentStatus;
import com.iotiq.partnererp.modules.auth.entity.ModuleCode;
import com.iotiq.partnererp.modules.dashboard.repository.DashboardQueries;
import com.iotiq.partnererp.modules.payouts.entity.PayoutStatus;
import com.iotiq.partnererp.modules.reports.service.ReportDefinition.Column;
import com.iotiq.partnererp.modules.reports.service.ReportDefinition.Option;
import com.iotiq.partnererp.modules.reports.service.ReportDefinition.Type;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The ten Phase 12 reports (FR-10). Each uses the same rules as the dashboard (DECISIONS.md §M): live records only,
 * collections from standing ledger entries, commission at the invoice's fee % snapshot, product sales from invoice
 * line snapshots. No report selects B2C prices, landed costs, bank details or client contact details, and PAN is
 * masked.
 */
public final class ReportCatalog {

    private ReportCatalog() {
    }

    private static final String STANDING = DashboardQueries.STANDING;

    /** Σ of ledger entries per invoice (reversals are negative), as v_invoice_balance. */
    private static final String PAID = "(SELECT invoice_id, SUM(amount) AS paid FROM payments GROUP BY invoice_id)";

    private static List<Option> options(String... pairs) {
        Option[] options = new Option[pairs.length / 2];
        for (int i = 0; i < pairs.length; i += 2) {
            options[i / 2] = new Option(pairs[i], pairs[i + 1]);
        }
        return List.of(options);
    }

    private static final ReportDefinition PARTNERS = new ReportDefinition("partners", "Partners",
            "Every partner with tier and status, and their leads, invoicing, collections and commission in the period. "
                    + "PAN is shown only as on file or not; bank details are never included.",
            ModuleCode.PARTNERS, "Activity columns: lead created, invoice and payment dates",
            List.of(Column.text("code", "Code"), Column.text("name", "Partner"), Column.of("category", "Category", Type.CODE),
                    Column.of("status", "Status", Type.CODE), Column.text("tier", "Tier"), Column.text("city", "City"),
                    Column.of("agreement", "Agreement", Type.CODE), Column.of("pan_on_file", "PAN on file", Type.BOOLEAN),
                    Column.of("pan_verified", "PAN verified", Type.BOOLEAN), Column.of("created_on", "Created", Type.DATE),
                    Column.of("leads_created", "Leads created", Type.NUMBER).requiring(ModuleCode.LEADS).totalled(),
                    Column.money("invoiced_taxable", "Invoiced (taxable)").requiring(ModuleCode.INVOICES),
                    Column.money("collected_ex_gst", "Collected (ex-GST)").requiring(ModuleCode.PAYMENTS),
                    Column.money("commission_earned", "Commission earned").requiring(ModuleCode.PAYOUTS)),
            "status", Arrays.stream(PartnerStatus.values())
                    .map(s -> new Option(s.name(), s.getLabel())).toList(),
            true, List.of("code", "name", "city"), "name", false, """
            SELECT pa.id, pa.id AS partner_id, '/partners/' || pa.id AS link,
                   pa.partner_code AS code, pa.name, pa.category, pa.status, t.name AS tier, pa.city,
                   pa.agreement_status AS agreement, pa.pan IS NOT NULL AS pan_on_file,
                   pa.pan_verified_at IS NOT NULL AS pan_verified, (pa.created_at AT TIME ZONE :zone)::date AS created_on,
                   COALESCE(ld.created, 0) AS leads_created, COALESCE(inv.taxable, 0) AS invoiced_taxable,
                   COALESCE(col.net, 0) AS collected_ex_gst, COALESCE(col.commission, 0) AS commission_earned
              FROM partners pa
              LEFT JOIN partner_tiers t ON t.id = pa.tier_id
              LEFT JOIN (SELECT partner_id, COUNT(*) AS created FROM leads
                          WHERE deleted_at IS NULL AND created_at >= :tsFrom AND created_at < :tsTo
                          GROUP BY partner_id) ld ON ld.partner_id = pa.id
              LEFT JOIN (SELECT partner_id, SUM(taxable_amount) AS taxable FROM invoices
                          WHERE status = 'ISSUED' AND deleted_at IS NULL AND invoice_date BETWEEN :from AND :to
                          GROUP BY partner_id) inv ON inv.partner_id = pa.id
              LEFT JOIN (SELECT p.partner_id, SUM(p.net_amount) AS net,
                                SUM(CASE WHEN p.entry_type = 'RECEIPT' AND i.fee_percent > 0
                                         THEN round(p.net_amount * i.fee_percent / 100, 2) ELSE 0 END) AS commission
                           FROM payments p JOIN invoices i ON i.id = p.invoice_id
                          WHERE p.payment_date BETWEEN :from AND :to AND""" + " " + STANDING + """

                          GROUP BY p.partner_id) col ON col.partner_id = pa.id""");

    private static final ReportDefinition LEADS = new ReportDefinition("leads", "Leads",
            "Leads created in the period with their current stage, budget and protection. Client phone and email are "
                    + "not included.",
            ModuleCode.LEADS, "Lead created date",
            List.of(Column.text("lead_number", "Lead"), Column.of("created_on", "Created", Type.DATE),
                    Column.text("client", "Client"), Column.text("city", "City"), Column.text("partner", "Partner"),
                    Column.of("stage", "Stage", Type.CODE), Column.of("project_type", "Project", Type.CODE),
                    Column.money("budget", "Budget"), Column.of("acknowledged", "Acknowledged", Type.BOOLEAN),
                    Column.of("expected_close", "Expected close", Type.DATE),
                    Column.of("protection_until", "Protected until", Type.DATE),
                    Column.of("age_days", "Age (days)", Type.NUMBER)),
            "stage", Arrays.stream(LeadStage.values()).map(s -> new Option(s.name(), s.getLabel())).toList(),
            true, List.of("lead_number", "client", "partner", "city"), "created_on", true, """
            SELECT l.id, l.partner_id, '/leads/' || l.id AS link, l.lead_number,
                   (l.created_at AT TIME ZONE :zone)::date AS created_on, l.client_name AS client, l.city,
                   pa.name AS partner, l.stage, l.project_type, l.budget, l.acknowledged_at IS NOT NULL AS acknowledged,
                   l.expected_close, l.protection_until,
                   CAST(:today AS date) - (l.created_at AT TIME ZONE :zone)::date AS age_days
              FROM leads l JOIN partners pa ON pa.id = l.partner_id
             WHERE l.deleted_at IS NULL AND l.created_at >= :tsFrom AND l.created_at < :tsTo""");

    private static final ReportDefinition PRODUCTS = new ReportDefinition("products", "Products",
            "The price sheet with partner prices and data gaps, and quantities and taxable value invoiced in the period "
                    + "(from invoice line snapshots). B2C prices and costs are not included.",
            ModuleCode.PRODUCTS, "Sales columns: invoice date",
            List.of(Column.text("sku", "SKU"), Column.text("name", "Product"), Column.of("category", "Category", Type.CODE),
                    Column.of("status", "Status", Type.CODE), Column.text("unit", "Unit"), Column.text("hsn", "HSN"),
                    Column.of("partner_price", "Partner price", Type.MONEY), Column.of("gst_percent", "GST %", Type.PERCENT),
                    Column.of("missing_data", "Missing data", Type.BOOLEAN),
                    new Column("quantity_sold", "Quantity sold", Type.NUMBER, ModuleCode.INVOICES, true),
                    Column.money("sales_taxable", "Sales (taxable)").requiring(ModuleCode.INVOICES),
                    new Column("invoices", "Invoices", Type.NUMBER, ModuleCode.INVOICES, false)),
            "status", options("ACTIVE", "Active", "INACTIVE", "Inactive"),
            false, List.of("sku", "name", "hsn"), "sku", false, """
            SELECT pr.id, NULL::uuid AS partner_id, '/products/' || pr.id AS link, pr.sku,
                   pr.partner_name AS name, pr.category, CASE WHEN pr.active THEN 'ACTIVE' ELSE 'INACTIVE' END AS status,
                   pr.unit, pr.hsn, pr.partner_price, pr.gst_percent,
                   (pr.partner_price = 0 OR pr.hsn IS NULL OR btrim(pr.hsn) = '') AS missing_data,
                   COALESCE(s.qty, 0) AS quantity_sold, COALESCE(s.taxable, 0) AS sales_taxable,
                   COALESCE(s.invoices, 0) AS invoices
              FROM products pr
              LEFT JOIN (SELECT ii.product_id, SUM(ii.quantity) AS qty, SUM(ii.taxable_value) AS taxable,
                                COUNT(DISTINCT i.id) AS invoices
                           FROM invoice_items ii JOIN invoices i ON i.id = ii.invoice_id
                          WHERE i.status = 'ISSUED' AND i.deleted_at IS NULL AND i.invoice_date BETWEEN :from AND :to
                          GROUP BY ii.product_id) s ON s.product_id = pr.id""");

    private static final ReportDefinition ESTIMATES = new ReportDefinition("estimates", "Estimates",
            "Live estimates dated in the period with their current status and whether a live issued invoice was "
                    + "raised from them.",
            ModuleCode.ESTIMATES, "Estimate date",
            List.of(Column.text("estimate_number", "Estimate"), Column.of("estimate_date", "Date", Type.DATE),
                    Column.text("lead", "Lead"), Column.text("client", "Client"), Column.text("partner", "Partner"),
                    Column.of("status", "Status", Type.CODE), Column.of("valid_until", "Valid until", Type.DATE),
                    Column.money("taxable", "Taxable"), Column.money("gst", "GST"), Column.money("total", "Total"),
                    Column.of("invoiced", "Invoiced", Type.BOOLEAN)),
            "status", options("DRAFT", "Draft", "SENT", "Sent", "ACCEPTED", "Accepted", "REJECTED", "Rejected",
                    "INVOICED", "Invoiced"),
            true, List.of("estimate_number", "lead", "client", "partner"), "estimate_date", true, """
            SELECT e.id, e.partner_id, '/estimates/' || e.id AS link, e.estimate_number, e.estimate_date,
                   l.lead_number AS lead, l.client_name AS client, pa.name AS partner, e.status, e.valid_until,
                   e.taxable_amount AS taxable, e.gst_amount AS gst, e.grand_total AS total,
                   EXISTS (SELECT 1 FROM invoices i WHERE i.estimate_id = e.id AND i.status = 'ISSUED'
                              AND i.deleted_at IS NULL) AS invoiced
              FROM estimates e JOIN partners pa ON pa.id = e.partner_id LEFT JOIN leads l ON l.id = e.lead_id
             WHERE e.deleted_at IS NULL AND e.estimate_date BETWEEN :from AND :to""");

    /** BR-V6 payment status in SQL (InvoiceRules#paymentStatus); drafts are never selected. */
    private static final String PAYMENT_STATUS = """
            CASE WHEN i.status = 'CANCELLED' THEN 'CANCELLED'
                 WHEN COALESCE(pd.paid, 0) >= i.grand_total - 0.50 THEN 'PAID'
                 WHEN COALESCE(pd.paid, 0) > 0 THEN CASE WHEN i.due_date < :today THEN 'PARTLY_PAID_OVERDUE' ELSE 'PARTLY_PAID' END
                 WHEN i.due_date < :today THEN 'OVERDUE' ELSE 'UNPAID' END""";

    private static final ReportDefinition INVOICES = new ReportDefinition("invoices", "Invoices",
            "Issued and cancelled invoices dated in the period (drafts excluded) with today's payment status. "
                    + "Received is the sum of standing receipts; a balance up to ₹0.50 is shown as Paid (BR-V6) but "
                    + "kept in the balance column.",
            ModuleCode.INVOICES, "Invoice date",
            List.of(Column.text("invoice_number", "Invoice"), Column.of("invoice_date", "Date", Type.DATE),
                    Column.of("due_date", "Due", Type.DATE), Column.text("partner", "Partner"),
                    Column.text("billed_to", "Billed to"), Column.of("status", "Status", Type.CODE),
                    Column.money("taxable", "Taxable"), Column.money("gst", "GST"), Column.money("total", "Total"),
                    Column.money("received", "Received"), Column.money("balance", "Balance")),
            "status", Arrays.stream(PaymentStatus.values()).map(s -> new Option(s.name(), s.getLabel())).toList(),
            true, List.of("invoice_number", "partner", "billed_to"), "invoice_date", true, """
            SELECT i.id, i.partner_id, '/invoices/' || i.id AS link, i.invoice_number, i.invoice_date, i.due_date,
                   pa.name AS partner, i.billing_name AS billed_to,""" + " " + PAYMENT_STATUS + " " + """
             AS status,
                   CASE WHEN i.status = 'ISSUED' THEN i.taxable_amount ELSE 0 END AS taxable,
                   CASE WHEN i.status = 'ISSUED' THEN i.gst_amount ELSE 0 END AS gst,
                   CASE WHEN i.status = 'ISSUED' THEN i.grand_total ELSE 0 END AS total,
                   COALESCE(pd.paid, 0) AS received,
                   CASE WHEN i.status = 'ISSUED' THEN GREATEST(i.grand_total - COALESCE(pd.paid, 0), 0) ELSE 0 END AS balance
              FROM invoices i JOIN partners pa ON pa.id = i.partner_id
              LEFT JOIN""" + " " + PAID + " " + """
             pd ON pd.invoice_id = i.id
             WHERE i.deleted_at IS NULL AND i.status IN ('ISSUED', 'CANCELLED') AND i.invoice_date BETWEEN :from AND :to""");

    private static final ReportDefinition PAYMENTS = new ReportDefinition("payments", "Payments",
            "Ledger entries dated in the period. Standing receipts are realised collections; a reversed receipt and its "
                    + "reversal are both listed and cancel out in the totals.",
            ModuleCode.PAYMENTS, "Payment date",
            List.of(Column.text("payment_number", "Payment"), Column.of("payment_date", "Date", Type.DATE),
                    Column.of("entry_type", "Entry", Type.CODE), Column.of("state", "State", Type.CODE),
                    Column.text("invoice", "Invoice"), Column.text("partner", "Partner"),
                    Column.of("method", "Method", Type.CODE), Column.text("reference", "Reference"),
                    Column.money("amount", "Amount (incl. GST)"), Column.money("amount_ex_gst", "Amount (ex-GST)"),
                    Column.money("gst", "GST")),
            "state", options("STANDING", "Standing", "REVERSED", "Reversed", "REVERSAL", "Reversal", "REFUND", "Refund (legacy)"),
            true, List.of("payment_number", "invoice", "partner", "reference"), "payment_date", true, """
            SELECT p.id, p.partner_id, '/payments/' || p.id AS link, p.payment_number, p.payment_date, p.entry_type,
                   CASE WHEN p.entry_type = 'REVERSAL' THEN 'REVERSAL' WHEN p.entry_type = 'REFUND' THEN 'REFUND'
                        WHEN EXISTS (SELECT 1 FROM payments r WHERE r.reversed_payment_id = p.id) THEN 'REVERSED'
                        ELSE 'STANDING' END AS state,
                   i.invoice_number AS invoice, pa.name AS partner, p.payment_method AS method,
                   p.reference_number AS reference, p.amount, p.net_amount AS amount_ex_gst,
                   p.amount - p.net_amount AS gst
              FROM payments p JOIN invoices i ON i.id = p.invoice_id JOIN partners pa ON pa.id = p.partner_id
             WHERE p.payment_date BETWEEN :from AND :to""");

    private static final ReportDefinition RECEIVABLES = new ReportDefinition("receivables", "Receivables ageing",
            "Issued invoices with more than ₹0.50 outstanding today, by days past due (source ageing buckets).",
            ModuleCode.INVOICES, "Invoice date",
            List.of(Column.text("invoice_number", "Invoice"), Column.of("invoice_date", "Date", Type.DATE),
                    Column.of("due_date", "Due", Type.DATE), Column.text("partner", "Partner"),
                    Column.text("billed_to", "Billed to"), Column.of("bucket", "Ageing", Type.CODE),
                    Column.of("days_past_due", "Days past due", Type.NUMBER), Column.money("total", "Total"),
                    Column.money("received", "Received"), Column.money("balance", "Balance")),
            "bucket", options("NOT_DUE", "Not due", "D1_30", "1–30 days", "D31_60", "31–60 days", "D61_90", "61–90 days",
                    "D90_PLUS", "90+ days"),
            true, List.of("invoice_number", "partner", "billed_to"), "days_past_due", true, """
            SELECT * FROM (
                SELECT i.id, i.partner_id, '/invoices/' || i.id AS link, i.invoice_number, i.invoice_date, i.due_date,
                       pa.name AS partner, i.billing_name AS billed_to, i.grand_total AS total,
                       COALESCE(pd.paid, 0) AS received, i.grand_total - COALESCE(pd.paid, 0) AS balance,
                       GREATEST(CAST(:today AS date) - i.due_date, 0) AS days_past_due,
                       CASE WHEN i.due_date IS NULL OR i.due_date >= :today THEN 'NOT_DUE'
                            WHEN CAST(:today AS date) - i.due_date <= 30 THEN 'D1_30'
                            WHEN CAST(:today AS date) - i.due_date <= 60 THEN 'D31_60'
                            WHEN CAST(:today AS date) - i.due_date <= 90 THEN 'D61_90' ELSE 'D90_PLUS' END AS bucket
                  FROM invoices i JOIN partners pa ON pa.id = i.partner_id
                  LEFT JOIN""" + " " + PAID + " " + """
             pd ON pd.invoice_id = i.id
                 WHERE i.status = 'ISSUED' AND i.deleted_at IS NULL AND i.invoice_date BETWEEN :from AND :to) b
             WHERE b.balance > 0.50""");

    private static final ReportDefinition COMMISSIONS = new ReportDefinition("commissions", "Partner commissions",
            "Commission earned on standing receipts dated in the period, at the fee % frozen on the invoice (or on the "
                    + "payout line once allocated), and where it stands: not yet in a payout, in a payout, or paid.",
            ModuleCode.PAYOUTS, "Payment date",
            List.of(Column.text("payment_number", "Receipt"), Column.of("payment_date", "Date", Type.DATE),
                    Column.text("invoice", "Invoice"), Column.text("partner", "Partner"),
                    Column.money("base_ex_gst", "Receipt (ex-GST)"), Column.of("commission_percent", "Fee %", Type.PERCENT),
                    Column.money("commission", "Commission"), Column.text("payout", "Payout"),
                    Column.of("payout_status", "Payout status", Type.CODE), Column.of("state", "State", Type.CODE)),
            "state", options("UNBILLED", "Not in a payout", "IN_PAYOUT", "In a payout", "PAID", "Paid"),
            true, List.of("payment_number", "invoice", "partner", "payout"), "payment_date", true, """
            SELECT p.id, p.partner_id, CASE WHEN po.id IS NULL THEN NULL ELSE '/payouts/' || po.id END AS link,
                   p.payment_number, p.payment_date, i.invoice_number AS invoice, pa.name AS partner,
                   COALESCE(l.base_amount, p.net_amount) AS base_ex_gst,
                   COALESCE(l.commission_percent, i.fee_percent) AS commission_percent,
                   COALESCE(l.amount, round(p.net_amount * i.fee_percent / 100, 2)) AS commission,
                   po.payout_number AS payout, po.status AS payout_status,
                   CASE WHEN po.id IS NULL THEN 'UNBILLED' WHEN po.status = 'PAID' THEN 'PAID' ELSE 'IN_PAYOUT' END AS state
              FROM payments p JOIN invoices i ON i.id = p.invoice_id JOIN partners pa ON pa.id = p.partner_id
              LEFT JOIN payout_lines l ON l.payment_id = p.id AND l.released_at IS NULL
              LEFT JOIN payouts po ON po.id = l.payout_id
             WHERE p.entry_type = 'RECEIPT' AND i.fee_percent > 0 AND p.payment_date BETWEEN :from AND :to
               AND""" + " " + STANDING);

    private static final ReportDefinition PAYOUTS = new ReportDefinition("payouts", "Payouts",
            "Payouts with an as-of date in the period: commission, TDS and net, status and dates. Bank details and "
                    + "transfer references are not included.",
            ModuleCode.PAYOUTS, "Payout as-of date",
            List.of(Column.text("payout_number", "Payout"), Column.of("as_of_date", "As of", Type.DATE),
                    Column.text("partner", "Partner"), Column.of("status", "Status", Type.CODE),
                    Column.money("commission", "Commission"), Column.money("tds", "TDS"), Column.money("net", "Net"),
                    Column.of("tds_status", "TDS status", Type.CODE), Column.of("approved_on", "Approved", Type.DATE),
                    Column.of("paid_date", "Paid", Type.DATE), Column.of("legacy", "Legacy", Type.BOOLEAN)),
            "status", Arrays.stream(PayoutStatus.values()).map(s -> new Option(s.name(), s.getLabel())).toList(),
            true, List.of("payout_number", "partner"), "as_of_date", true, """
            SELECT po.id, po.partner_id, '/payouts/' || po.id AS link, po.payout_number, po.as_of_date,
                   pa.name AS partner, po.status, po.commission_total AS commission, po.tds_amount AS tds,
                   po.net_amount AS net, po.tds_status, (po.approved_at AT TIME ZONE :zone)::date AS approved_on,
                   po.paid_date, po.legacy
              FROM payouts po JOIN partners pa ON pa.id = po.partner_id
             WHERE po.as_of_date BETWEEN :from AND :to""");

    private static final ReportDefinition TDS_ADJUSTMENTS = new ReportDefinition("tds-adjustments", "TDS and adjustments",
            "TDS on payouts with an as-of date in the period (rejected, cancelled and failed payouts excluded) and "
                    + "partner adjustments created in the period. PAN is masked.",
            ModuleCode.PAYOUTS, "Payout as-of date / adjustment created date",
            List.of(Column.of("type", "Type", Type.CODE), Column.text("reference", "Reference"),
                    Column.of("entry_date", "Date", Type.DATE), Column.text("partner", "Partner"),
                    Column.of("pan", "PAN", Type.MASKED), Column.of("section", "Section / kind", Type.CODE),
                    Column.of("rate", "Rate %", Type.PERCENT), Column.money("base", "TDS base"),
                    Column.money("tds_amount", "TDS"), Column.money("adjustment_amount", "Adjustment"),
                    Column.of("state", "Status", Type.CODE), Column.of("manually_adjusted", "TDS changed by hand", Type.BOOLEAN),
                    Column.text("note", "Reason")),
            "type", options("TDS", "TDS", "ADJUSTMENT", "Adjustment"),
            true, List.of("reference", "partner"), "entry_date", true, """
            SELECT po.id, po.partner_id, '/payouts/' || po.id AS link, 'TDS' AS type, po.payout_number AS reference,
                   po.as_of_date AS entry_date, pa.name AS partner, pa.pan, po.tds_section AS section,
                   po.tds_rate AS rate, po.tds_base AS base, po.tds_amount, NULL::numeric AS adjustment_amount,
                   po.status AS state, po.tds_adjusted AS manually_adjusted, NULL::varchar AS note
              FROM payouts po JOIN partners pa ON pa.id = po.partner_id
             WHERE NOT po.legacy AND po.tds_amount IS NOT NULL AND po.status NOT IN ('REJECTED', 'CANCELLED', 'FAILED')
               AND po.as_of_date BETWEEN :from AND :to
            UNION ALL
            SELECT a.id, a.partner_id, NULL, 'ADJUSTMENT', a.adjustment_number, (a.created_at AT TIME ZONE :zone)::date,
                   pa.name, pa.pan, a.kind, NULL, NULL, NULL, a.amount, a.status, NULL, a.reason
              FROM partner_adjustments a JOIN partners pa ON pa.id = a.partner_id
             WHERE a.created_at >= :tsFrom AND a.created_at < :tsTo""");

    public static final List<ReportDefinition> ALL = List.of(PARTNERS, LEADS, PRODUCTS, ESTIMATES, INVOICES, PAYMENTS,
            RECEIVABLES, COMMISSIONS, PAYOUTS, TDS_ADJUSTMENTS);

    public static Optional<ReportDefinition> find(String code) {
        return ALL.stream().filter(r -> r.code().equals(code)).findFirst();
    }
}
