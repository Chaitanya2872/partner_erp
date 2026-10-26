package com.iotiq.partnererp.modules.dashboard.service;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.enums.LeadStage;
import com.iotiq.partnererp.common.enums.PartnerStatus;
import com.iotiq.partnererp.modules.auth.entity.ModuleCode;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardRequest;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.AlertGroup;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.AlertItem;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.Alerts;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.Conversion;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.EstimateKpis;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.Finance;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.FollowUps;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.InvoiceKpis;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.LeadKpis;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.Overview;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.PartnerBalance;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.PartnerKpis;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.PartnerLeads;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.PartnerRank;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.Partners;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.PaymentKpis;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.PayoutKpis;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.PeriodView;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.ProductKpis;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.ProductSales;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.Products;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.Restriction;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.Sales;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.Series;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.Slice;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.StatusTotal;
import com.iotiq.partnererp.modules.dashboard.dto.DashboardResponse.Trend;
import com.iotiq.partnererp.modules.dashboard.repository.DashboardQueries;
import com.iotiq.partnererp.modules.dashboard.repository.DashboardQueries.Daily;
import com.iotiq.partnererp.modules.dashboard.repository.DashboardQueries.Row;
import com.iotiq.partnererp.modules.leads.repository.LeadMetricsRepository;
import com.iotiq.partnererp.modules.payments.entity.PaymentMethod;
import com.iotiq.partnererp.modules.payouts.entity.PayoutStatus;
import com.iotiq.partnererp.modules.payouts.repository.PayoutQueries;
import com.iotiq.partnererp.modules.payouts.service.PayoutRules;
import com.iotiq.partnererp.modules.reports.service.AnalyticsAccess;
import com.iotiq.partnererp.modules.reports.service.ReportPeriod;
import com.iotiq.partnererp.modules.settings.entity.CompanySettings;
import com.iotiq.partnererp.modules.settings.repository.CompanySettingsRepository;
import com.iotiq.partnererp.security.AuthenticatedUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dashboard, analytics and operational alerts (Phase 12). Access is decided before any query: a section is built only
 * when the caller can read its modules with the ALL scope ({@link AnalyticsAccess}); otherwise it is null and the
 * module is listed as restricted. Nothing is cached, so no figure can be served to a user with other access.
 * Definitions of every metric: DECISIONS.md §M.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final int TOP_PARTNERS = 5;
    static final int RANKING_SIZE = 10;
    static final int PARTNER_LEADS_SIZE = 20;
    static final int TOP_PRODUCTS = 10;
    static final int ALERT_ITEMS = 5;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final DashboardQueries queries;
    private final LeadMetricsRepository leadMetrics;
    private final PayoutQueries payoutQueries;
    private final CompanySettingsRepository settingsRepository;
    private final Clock clock;

    public DashboardService(DashboardQueries queries, LeadMetricsRepository leadMetrics, PayoutQueries payoutQueries,
                            CompanySettingsRepository settingsRepository, Clock clock) {
        this.queries = queries;
        this.leadMetrics = leadMetrics;
        this.payoutQueries = payoutQueries;
        this.settingsRepository = settingsRepository;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------------------ overview

    public Overview overview(DashboardRequest request, AuthenticatedUser user) {
        AnalyticsAccess access = AnalyticsAccess.of(user);
        LocalDate today = LocalDate.now(clock);
        ReportPeriod period = request.resolve(today);
        MapSqlParameterSource params = DashboardQueries.params(period, today);

        DashboardQueries.Receivables receivables = access.sees(ModuleCode.INVOICES) || access.sees(ModuleCode.PAYMENTS)
                ? queries.receivables(params) : null;
        return new Overview(view(period, today), restricted(access),
                access.sees(ModuleCode.PARTNERS) ? partnerKpis(params) : null,
                access.sees(ModuleCode.LEADS) ? leadKpis(params) : null,
                access.sees(ModuleCode.ESTIMATES) ? estimateKpis(params) : null,
                access.sees(ModuleCode.INVOICES) ? invoiceKpis(params, receivables) : null,
                access.sees(ModuleCode.PAYMENTS) ? paymentKpis(params, receivables) : null,
                access.sees(ModuleCode.PAYOUTS) ? payoutKpis(params) : null,
                revenueTrend(period, params, access),
                access.sees(ModuleCode.LEADS) ? funnel() : null,
                access.sees(ModuleCode.PARTNERS) ? tierMix() : null,
                access.sees(ModuleCode.PAYMENTS) ? methods(params) : null,
                access.sees(ModuleCode.INVOICES) ? aging(params) : null,
                access.sees(ModuleCode.PARTNERS, ModuleCode.PAYMENTS) ? rankings(params, access, TOP_PARTNERS) : null);
    }

    // ------------------------------------------------------------------------------------------------ sales

    public Sales sales(DashboardRequest request, AuthenticatedUser user) {
        AnalyticsAccess access = AnalyticsAccess.of(user);
        LocalDate today = LocalDate.now(clock);
        ReportPeriod period = request.resolve(today);
        PeriodView view = view(period, today);
        if (!access.sees(ModuleCode.LEADS)) {
            return new Sales(view, restricted(access), null, null, null, null, null, null, null,
                    access.sees(ModuleCode.ESTIMATES) ? estimateKpis(DashboardQueries.params(period, today)) : null);
        }
        MapSqlParameterSource params = DashboardQueries.params(period, today);
        Instant now = clock.instant();
        CompanySettings settings = settings();
        int ackHours = settings.getAcknowledgementHours();
        int estimateDays = settings.getEstimateSlaDays();

        LeadKpis kpis = leadKpis(params);
        DashboardQueries.FollowUpCounts f = queries.followUps(params, now, ackHours, estimateDays);
        long violations = queries.createdInPeriod(
                leadMetrics.slaBreachedIds(now, clock.getZone(), ackHours, estimateDays), params);
        FollowUps followUps = new FollowUps(f.pastExpectedClose(), f.unacknowledged(), f.awaitingEstimate(), violations,
                ackHours, estimateDays);

        Map<LocalDate, Daily> daily = byDay(queries.leadsDaily(params));
        Trend trend = trend(period, List.of(
                new SeriesSpec("created", "Leads created", "COUNT", daily, Daily::a),
                new SeriesSpec("won", "Won", "COUNT", daily, Daily::b),
                new SeriesSpec("lost", "Lost", "COUNT", daily, Daily::c)));

        List<Slice> aging = buckets(queries.openLeadAging(params), LEAD_AGE_BUCKETS);
        List<PartnerLeads> byPartner = queries.leadsByPartner(params, PARTNER_LEADS_SIZE).stream()
                .map(r -> new PartnerLeads(r.partnerId(), r.name(), r.created(), r.open(), r.won(), r.lost(),
                        rate(r.won(), r.won() + r.lost()), money(r.pipeline())))
                .toList();

        EstimateKpis estimates = access.sees(ModuleCode.ESTIMATES) ? estimateKpis(params) : null;
        Long withEstimate = null;
        long created = kpis.newInPeriod();
        if (access.sees(ModuleCode.ESTIMATES)) {
            long[] counts = queries.leadToEstimate(params);
            created = counts[0];
            withEstimate = counts[1];
        }
        Long issued = estimates == null ? null : estimates.total() - estimates.draft();
        Conversion conversion = new Conversion(created, withEstimate,
                withEstimate == null ? null : rate(withEstimate, created),
                issued, estimates == null ? null : estimates.converted(),
                estimates == null ? null : estimates.invoiceConversionRate(),
                kpis.wonInPeriod(), kpis.lostInPeriod(), kpis.winRate());
        return new Sales(view, restricted(access), kpis, funnel(), trend, aging, followUps, byPartner, conversion,
                estimates);
    }

    // ------------------------------------------------------------------------------------------------ partners

    public Partners partners(DashboardRequest request, AuthenticatedUser user) {
        AnalyticsAccess access = AnalyticsAccess.of(user);
        LocalDate today = LocalDate.now(clock);
        ReportPeriod period = request.resolve(today);
        PeriodView view = view(period, today);
        if (!access.sees(ModuleCode.PARTNERS)) {
            return new Partners(view, restricted(access), null, null, null, null, null, null, null, null);
        }
        MapSqlParameterSource params = DashboardQueries.params(period, today);
        PartnerKpis kpis = partnerKpis(params);
        List<Slice> statuses = List.of(
                slice(PartnerStatus.PROSPECT.name(), PartnerStatus.PROSPECT.getLabel(), kpis.prospect()),
                slice(PartnerStatus.ONBOARDING.name(), PartnerStatus.ONBOARDING.getLabel(), kpis.onboarding()),
                slice(PartnerStatus.ACTIVE.name(), PartnerStatus.ACTIVE.getLabel(), kpis.active()),
                slice(PartnerStatus.INACTIVE.name(), PartnerStatus.INACTIVE.getLabel(), kpis.inactive()));

        boolean payments = access.sees(ModuleCode.PAYMENTS);
        BigDecimal realized = payments ? money(queries.collections(params).net()) : null;
        List<SeriesSpec> series = new ArrayList<>();
        series.add(new SeriesSpec("newPartners", "New partners", "COUNT", byDay(queries.partnersCreatedDaily(params)),
                Daily::a));
        if (payments) {
            series.add(new SeriesSpec("collectedExGst", "Collected (ex-GST)", "INR",
                    byDay(queries.collectedDaily(params)), Daily::b));
        }
        List<PartnerBalance> balances = null;
        List<PartnerBalance> negative = null;
        if (access.sees(ModuleCode.PAYOUTS)) {
            List<PartnerBalance> all = balances(today);
            balances = all.stream().filter(b -> b.available().signum() > 0)
                    .sorted(Comparator.comparing(PartnerBalance::available).reversed()).limit(RANKING_SIZE).toList();
            negative = all.stream().filter(b -> b.carriedAdjustments().signum() < 0)
                    .sorted(Comparator.comparing(PartnerBalance::carriedAdjustments)).limit(RANKING_SIZE).toList();
        }
        return new Partners(view, restricted(access), kpis, statuses, tierMix(), realized,
                payments ? rankings(params, access, RANKING_SIZE) : null, trend(period, series), balances, negative);
    }

    // ------------------------------------------------------------------------------------------------ products

    public Products products(DashboardRequest request, AuthenticatedUser user) {
        AnalyticsAccess access = AnalyticsAccess.of(user);
        LocalDate today = LocalDate.now(clock);
        ReportPeriod period = request.resolve(today);
        PeriodView view = view(period, today);
        if (!access.sees(ModuleCode.PRODUCTS)) {
            return new Products(view, restricted(access), null, null, null, null, null, null, null);
        }
        DashboardQueries.ProductCounts c = queries.productCounts();
        // The number of products without a B2C price is internal B2C information (P7-09).
        ProductKpis kpis = new ProductKpis(c.total(), c.active(), c.inactive(), c.missingPartnerPrice(), c.missingHsn(),
                access.internalB2c() ? c.missingB2cPrice() : null);
        List<Slice> categories = queries.productCategories().stream()
                .map(r -> slice(r.key(), humanize(r.key()), r.count())).toList();
        if (!access.sees(ModuleCode.INVOICES)) {
            return new Products(view, restricted(access), kpis, categories, null, null, null, null, null);
        }
        List<DashboardQueries.ProductSalesRow> rows = queries.productSales(DashboardQueries.params(period, today));
        BigDecimal total = money(rows.stream().map(DashboardQueries.ProductSalesRow::taxable)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal unlisted = money(rows.stream().filter(r -> r.productId() == null)
                .map(DashboardQueries.ProductSalesRow::taxable).reduce(BigDecimal.ZERO, BigDecimal::add));
        List<ProductSales> listed = rows.stream().filter(r -> r.productId() != null)
                .map(r -> new ProductSales(r.productId(), r.sku(), r.name(), r.category(), r.quantity(),
                        money(r.taxable()), share(r.taxable(), total), r.invoices()))
                .toList();
        List<ProductSales> byValue = listed.stream().sorted(Comparator.comparing(ProductSales::taxable).reversed()
                .thenComparing(ProductSales::sku)).limit(TOP_PRODUCTS).toList();
        List<ProductSales> byQuantity = listed.stream().sorted(Comparator.comparing(ProductSales::quantity).reversed()
                .thenComparing(ProductSales::sku)).limit(TOP_PRODUCTS).toList();
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        Map<String, Long> countByCategory = new LinkedHashMap<>();
        for (DashboardQueries.ProductSalesRow r : rows) {
            String key = r.category() == null ? "UNLISTED" : r.category();
            byCategory.merge(key, r.taxable(), BigDecimal::add);
            countByCategory.merge(key, r.invoices(), Long::sum);
        }
        List<Slice> categoryRevenue = byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> new Slice(e.getKey(), "UNLISTED".equals(e.getKey()) ? "Not in price sheet" : humanize(e.getKey()),
                        countByCategory.get(e.getKey()), money(e.getValue())))
                .toList();
        return new Products(view, restricted(access), kpis, categories, total, unlisted, byValue, byQuantity,
                categoryRevenue);
    }

    // ------------------------------------------------------------------------------------------------ finance

    public Finance finance(DashboardRequest request, AuthenticatedUser user) {
        AnalyticsAccess access = AnalyticsAccess.of(user);
        LocalDate today = LocalDate.now(clock);
        ReportPeriod period = request.resolve(today);
        MapSqlParameterSource params = DashboardQueries.params(period, today);
        boolean invoices = access.sees(ModuleCode.INVOICES);
        boolean payments = access.sees(ModuleCode.PAYMENTS);
        DashboardQueries.Receivables receivables = invoices || payments ? queries.receivables(params) : null;
        InvoiceKpis invoicing = invoices ? invoiceKpis(params, receivables) : null;
        PaymentKpis collections = payments ? paymentKpis(params, receivables) : null;
        BigDecimal efficiency = invoicing != null && collections != null
                ? share(collections.collected(), invoicing.gross()) : null;
        return new Finance(view(period, today), restricted(access), invoicing, invoices ? aging(params) : null,
                collections, payments ? methods(params) : null, revenueTrend(period, params, access), efficiency,
                access.sees(ModuleCode.PAYOUTS) ? payoutKpis(params) : null,
                access.sees(ModuleCode.PAYOUTS) ? balances(today).stream()
                        .sorted(Comparator.comparing(PartnerBalance::available).reversed()).limit(RANKING_SIZE).toList()
                        : null);
    }

    // ------------------------------------------------------------------------------------------------ alerts

    private record AlertSpec(String code, ModuleCode module, String severity, String title, String detail,
                             String href, String itemHref, String sql) {
    }

    private static final String OPEN_LEAD = DashboardQueries.OPEN_LEAD;
    private static final String LEAD_LABEL = "COALESCE(l.lead_number, 'Lead') || ' · ' || l.client_name";

    private static final List<AlertSpec> ALERTS = List.of(
            new AlertSpec("INVOICES_OVERDUE", ModuleCode.INVOICES, "danger", "Overdue invoices",
                    "Issued invoices past their due date with more than ₹0.50 outstanding.", "/invoices", "/invoices/",
                    """
                    SELECT i.id::text AS id, i.invoice_number AS label,
                           'Due ' || to_char(i.due_date, 'DD Mon YYYY') AS detail,
                           i.grand_total - COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.invoice_id = i.id), 0) AS amount,
                           i.due_date AS ord
                      FROM invoices i
                     WHERE i.status = 'ISSUED' AND i.deleted_at IS NULL AND i.due_date < :today
                       AND i.grand_total - COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.invoice_id = i.id), 0) > 0.50"""),
            new AlertSpec("PAYOUTS_BLOCKED_REVERSAL", ModuleCode.PAYOUTS, "danger", "Payouts blocked by reversed receipts",
                    "Open payouts with a receipt that has since been reversed: recalculate or cancel them.", "/payouts",
                    "/payouts/", """
                    SELECT po.id::text AS id, COALESCE(po.payout_number, 'Payout') || ' · ' || pa.name AS label,
                           po.status AS detail, po.commission_total AS amount, po.created_at AS ord
                      FROM payouts po JOIN partners pa ON pa.id = po.partner_id
                     WHERE po.status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'PROCESSING')
                       AND EXISTS (SELECT 1 FROM payout_lines l JOIN payments r ON r.reversed_payment_id = l.payment_id
                                    WHERE l.payout_id = po.id AND l.released_at IS NULL)"""),
            new AlertSpec("LEADS_UNACKNOWLEDGED", ModuleCode.LEADS, "warning", "Leads not acknowledged in time",
                    "Open leads not acknowledged within the acknowledgement SLA.", "/leads", "/leads/",
                    "SELECT l.id::text AS id, " + LEAD_LABEL + " AS label, 'Created ' || to_char(l.created_at AT TIME ZONE "
                            + ":zone, 'DD Mon YYYY') AS detail, NULL::numeric AS amount, l.created_at AS ord "
                            + "FROM leads l WHERE " + OPEN_LEAD + " AND " + DashboardQueries.UNACKNOWLEDGED),
            new AlertSpec("LEADS_AWAITING_ESTIMATE", ModuleCode.LEADS, "warning", "Missed estimate follow-ups",
                    "Acknowledged open leads still without an estimate after the estimate SLA.", "/leads", "/leads/",
                    "SELECT l.id::text AS id, " + LEAD_LABEL + " AS label, 'Acknowledged ' || to_char(l.acknowledged_at "
                            + "AT TIME ZONE :zone, 'DD Mon YYYY') AS detail, NULL::numeric AS amount, "
                            + "l.acknowledged_at AS ord FROM leads l WHERE " + OPEN_LEAD + " AND "
                            + "l.acknowledged_at IS NOT NULL AND NOT EXISTS (SELECT 1 FROM estimates e WHERE e.lead_id = "
                            + "l.id AND e.deleted_at IS NULL) AND CAST(:today AS date) - (l.acknowledged_at AT TIME ZONE "
                            + ":zone)::date > :estDays"),
            new AlertSpec("LEADS_PAST_EXPECTED_CLOSE", ModuleCode.LEADS, "warning", "Overdue leads",
                    "Open leads past their expected close date.", "/leads", "/leads/",
                    "SELECT l.id::text AS id, " + LEAD_LABEL + " AS label, 'Expected close ' || to_char(l.expected_close, "
                            + "'DD Mon YYYY') AS detail, l.budget AS amount, l.expected_close AS ord FROM leads l "
                            + "WHERE " + OPEN_LEAD + " AND l.expected_close < :today"),
            new AlertSpec("ESTIMATES_EXPIRED", ModuleCode.ESTIMATES, "warning", "Sent estimates past validity",
                    "Sent estimates whose validity date has passed without a decision.", "/estimates", "/estimates/",
                    """
                    SELECT e.id::text AS id, COALESCE(e.estimate_number, 'Estimate') AS label,
                           'Valid until ' || to_char(e.valid_until, 'DD Mon YYYY') AS detail, e.grand_total AS amount,
                           e.valid_until AS ord
                      FROM estimates e
                     WHERE e.deleted_at IS NULL AND e.status = 'SENT' AND e.valid_until < :today"""),
            new AlertSpec("PAYOUTS_AWAITING_APPROVAL", ModuleCode.PAYOUTS, "warning", "Payouts awaiting approval",
                    "Submitted payouts waiting for a second person to approve them.", "/payouts", "/payouts/", """
                    SELECT po.id::text AS id, COALESCE(po.payout_number, 'Payout') || ' · ' || pa.name AS label,
                           'Submitted ' || to_char(po.submitted_at AT TIME ZONE :zone, 'DD Mon YYYY') AS detail,
                           po.commission_total AS amount, po.submitted_at AS ord
                      FROM payouts po JOIN partners pa ON pa.id = po.partner_id
                     WHERE po.status = 'PENDING_APPROVAL'"""),
            new AlertSpec("TDS_NOT_CONFIRMED", ModuleCode.PAYOUTS, "warning", "TDS not ready",
                    "Open payouts whose TDS can't be calculated (no confirmed TDS configuration or PAN not verified).",
                    "/payouts", "/payouts/", """
                    SELECT po.id::text AS id, COALESCE(po.payout_number, 'Payout') || ' · ' || pa.name AS label,
                           CASE po.tds_status WHEN 'PAN_NOT_VERIFIED' THEN 'PAN not verified'
                                ELSE 'TDS configuration not confirmed' END AS detail,
                           po.commission_total AS amount, po.created_at AS ord
                      FROM payouts po JOIN partners pa ON pa.id = po.partner_id
                     WHERE po.status IN ('DRAFT', 'PENDING_APPROVAL') AND po.tds_status IN ('CONFIG_INCOMPLETE', 'PAN_NOT_VERIFIED')"""),
            new AlertSpec("ADJUSTMENTS_OUTSTANDING", ModuleCode.PAYOUTS, "warning", "Negative adjustments outstanding",
                    "Approved negative adjustments not yet recovered through a payout, by partner.", "/payouts", null,
                    """
                    SELECT pa.id::text AS id, pa.name AS label,
                           COUNT(*) || CASE WHEN COUNT(*) = 1 THEN ' adjustment' ELSE ' adjustments' END AS detail,
                           SUM(a.amount) AS amount, SUM(a.amount) AS ord
                      FROM partner_adjustments a JOIN partners pa ON pa.id = a.partner_id
                     WHERE a.status = 'APPROVED' AND a.amount < 0
                       AND NOT EXISTS (SELECT 1 FROM payout_lines l WHERE l.adjustment_id = a.id AND l.released_at IS NULL)
                     GROUP BY pa.id, pa.name"""),
            new AlertSpec("PARTNERS_AGREEMENT_UNSIGNED", ModuleCode.PARTNERS, "warning", "Active partners without a signed agreement",
                    "Active partners whose agreement is not signed.", "/partners", "/partners/", """
                    SELECT pa.id::text AS id, pa.name AS label,
                           CASE pa.agreement_status WHEN 'SENT' THEN 'Agreement sent, not signed' ELSE 'Agreement not sent' END AS detail,
                           NULL::numeric AS amount, lower(pa.name) AS ord
                      FROM partners pa WHERE pa.status = 'ACTIVE' AND pa.agreement_status <> 'SIGNED'"""),
            new AlertSpec("LEADS_PROTECTION_ENDING", ModuleCode.LEADS, "info", "Lead protection ending soon",
                    "Open leads whose partner protection ends within 15 days.", "/leads", "/leads/",
                    "SELECT l.id::text AS id, " + LEAD_LABEL + " AS label, 'Protection ends ' || to_char(l.protection_until, "
                            + "'DD Mon YYYY') AS detail, l.budget AS amount, l.protection_until AS ord FROM leads l "
                            + "WHERE " + OPEN_LEAD + " AND l.protection_until BETWEEN :today AND CAST(:today AS date) + 15"),
            new AlertSpec("PRODUCTS_MISSING_DATA", ModuleCode.PRODUCTS, "info", "Products missing data",
                    "Active products without a partner price or an HSN code.", "/products", "/products/", """
                    SELECT pr.id::text AS id, pr.sku || ' · ' || pr.partner_name AS label,
                           CASE WHEN pr.partner_price = 0 AND (pr.hsn IS NULL OR btrim(pr.hsn) = '') THEN 'No partner price or HSN'
                                WHEN pr.partner_price = 0 THEN 'No partner price' ELSE 'No HSN code' END AS detail,
                           NULL::numeric AS amount, lower(pr.sku) AS ord
                      FROM products pr
                     WHERE pr.active AND (pr.partner_price = 0 OR pr.hsn IS NULL OR btrim(pr.hsn) = '')"""));

    /**
     * Attention items derived on the server from current data (FR-12), only for modules the caller can read with the
     * ALL scope. Groups without items are left out, except the TDS group while no TDS configuration is confirmed.
     */
    public Alerts alerts(AuthenticatedUser user) {
        AnalyticsAccess access = AnalyticsAccess.of(user);
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        ReportPeriod period = ReportPeriod.resolve(ReportPeriod.Preset.TODAY, null, null, today, ReportPeriod.Preset.TODAY);
        CompanySettings settings = settings();
        MapSqlParameterSource params = DashboardQueries.withSla(DashboardQueries.params(period, today), now,
                settings.getAcknowledgementHours(), settings.getEstimateSlaDays());
        List<AlertGroup> groups = new ArrayList<>();
        for (AlertSpec spec : ALERTS) {
            if (!access.sees(spec.module())) {
                continue;
            }
            List<DashboardQueries.AlertRow> rows = queries.alert(spec.sql(), params, ALERT_ITEMS);
            long count = rows.isEmpty() ? 0 : rows.get(0).total();
            String detail = spec.detail();
            if ("TDS_NOT_CONFIRMED".equals(spec.code()) && !queries.tdsConfirmed(params)) {
                detail = "No confirmed TDS configuration is in force, so no payout can be approved. " + detail;
            } else if (count == 0) {
                continue;
            }
            BigDecimal amount = rows.isEmpty() || rows.get(0).amount() == null ? null
                    : money(rows.get(0).amount());
            groups.add(new AlertGroup(spec.code(), spec.severity(), spec.module().name(), spec.title(), detail, count,
                    amount, spec.href(), rows.stream().map(r -> new AlertItem(r.id(), r.label(), r.detail(),
                    spec.itemHref() == null ? null : spec.itemHref() + r.id())).toList()));
        }
        return new Alerts(restricted(access), groups, now);
    }

    // ------------------------------------------------------------------------------------------------ sections

    private PartnerKpis partnerKpis(MapSqlParameterSource params) {
        DashboardQueries.PartnerCounts c = queries.partnerCounts(params);
        return new PartnerKpis(c.total(), c.active(), c.inactive(), c.prospect(), c.onboarding(), c.created(),
                c.createdPrevious());
    }

    private LeadKpis leadKpis(MapSqlParameterSource params) {
        DashboardQueries.LeadCounts c = queries.leadCounts(params);
        return new LeadKpis(c.total(), c.created(), c.createdPrevious(), c.open(), money(c.pipeline()),
                c.openWithoutBudget(), c.wonInPeriod(), c.lostInPeriod(),
                rate(c.wonInPeriod(), c.wonInPeriod() + c.lostInPeriod()), c.wonTotal(), rate(c.wonTotal(), c.total()));
    }

    private EstimateKpis estimateKpis(MapSqlParameterSource params) {
        DashboardQueries.EstimateCounts c = queries.estimateCounts(params);
        return new EstimateKpis(c.total(), c.draft(), c.sent(), c.accepted(), c.rejected(), c.invoiced(),
                money(c.value()), c.converted(), rate(c.converted(), c.total() - c.draft()));
    }

    private InvoiceKpis invoiceKpis(MapSqlParameterSource params, DashboardQueries.Receivables r) {
        DashboardQueries.InvoiceTotals t = queries.invoiceTotals(params);
        return new InvoiceKpis(t.issued(), money(t.gross()), money(t.taxable()), money(t.gst()),
                money(t.taxablePrevious()), t.cancelled(), money(t.cancelledValue()), r.count(),
                money(r.outstanding()), r.overdueCount(), money(r.overdue()), r.toleranceCount(),
                money(r.toleranceResidual()));
    }

    private PaymentKpis paymentKpis(MapSqlParameterSource params, DashboardQueries.Receivables r) {
        DashboardQueries.Collections c = queries.collections(params);
        return new PaymentKpis(c.receipts(), money(c.gross()), money(c.net()), money(c.gross().subtract(c.net())),
                money(c.netPrevious()), c.reversals(), money(c.reversed()), r.partlyPaid(),
                money(r.partlyPaidOutstanding()));
    }

    private PayoutKpis payoutKpis(MapSqlParameterSource params) {
        DashboardQueries.PayoutTotals t = queries.payoutTotals(params);
        Map<String, PayoutQueries.StatusTotal> byStatus = payoutQueries.statusTotals().stream()
                .collect(Collectors.toMap(PayoutQueries.StatusTotal::status, Function.identity()));
        List<StatusTotal> statuses = new ArrayList<>();
        for (PayoutStatus status : PayoutStatus.values()) {
            PayoutQueries.StatusTotal s = byStatus.get(status.name());
            statuses.add(s == null ? new StatusTotal(status.name(), status.getLabel(), 0, money(null), money(null), money(null))
                    : new StatusTotal(status.name(), status.getLabel(), s.count(), s.commission(), s.tds(), s.net()));
        }
        BigDecimal pending = sumCommission(byStatus, "DRAFT", "PENDING_APPROVAL");
        BigDecimal approved = sumCommission(byStatus, "APPROVED", "PROCESSING");
        return new PayoutKpis(money(t.earned()), money(t.earnedPrevious()), money(t.unbilled()), pending, approved,
                t.paidCount(), money(t.paidCommission()), money(t.paidTds()), money(t.paidNet()),
                t.negativeAdjustments(), money(t.negativeBalance()), t.awaitingApproval(), statuses);
    }

    private static BigDecimal sumCommission(Map<String, PayoutQueries.StatusTotal> byStatus, String... statuses) {
        BigDecimal sum = BigDecimal.ZERO;
        for (String status : statuses) {
            PayoutQueries.StatusTotal s = byStatus.get(status);
            if (s != null) {
                sum = sum.add(s.commission());
            }
        }
        return money(sum);
    }

    private List<Slice> funnel() {
        Map<String, Row> rows = queries.leadFunnel().stream().collect(Collectors.toMap(Row::key, Function.identity()));
        List<Slice> slices = new ArrayList<>();
        for (LeadStage stage : LeadStage.values()) {
            Row row = rows.get(stage.name());
            slices.add(new Slice(stage.name(), stage.getLabel(), row == null ? 0 : row.count(),
                    money(row == null ? null : row.amount())));
        }
        return slices;
    }

    private List<Slice> tierMix() {
        return queries.tierMix().stream().map(r -> new Slice(r.key(), r.label(), r.count(), null)).toList();
    }

    private List<Slice> methods(MapSqlParameterSource params) {
        Map<String, Row> rows = queries.paymentMethods(params).stream()
                .collect(Collectors.toMap(Row::key, Function.identity()));
        List<Slice> slices = new ArrayList<>();
        for (PaymentMethod method : PaymentMethod.values()) {
            Row row = rows.get(method.name());
            if (row != null) {
                slices.add(new Slice(method.name(), method.getLabel(), row.count(), money(row.amount())));
            }
        }
        return slices;
    }

    private static final Map<String, String> AGING_BUCKETS = linked("NOT_DUE", "Not due", "D1_30", "1–30 days",
            "D31_60", "31–60 days", "D61_90", "61–90 days", "D90_PLUS", "90+ days");
    private static final Map<String, String> LEAD_AGE_BUCKETS = linked("D0_7", "0–7 days", "D8_30", "8–30 days",
            "D31_60", "31–60 days", "D61_90", "61–90 days", "D90_PLUS", "90+ days");

    private List<Slice> aging(MapSqlParameterSource params) {
        return buckets(queries.receivableAging(params), AGING_BUCKETS);
    }

    private static List<Slice> buckets(List<Row> rows, Map<String, String> labels) {
        Map<String, Row> byKey = rows.stream().collect(Collectors.toMap(Row::key, Function.identity()));
        List<Slice> slices = new ArrayList<>();
        labels.forEach((key, label) -> {
            Row row = byKey.get(key);
            slices.add(new Slice(key, label, row == null ? 0 : row.count(), money(row == null ? null : row.amount())));
        });
        return slices;
    }

    private List<PartnerRank> rankings(MapSqlParameterSource params, AnalyticsAccess access, int limit) {
        BigDecimal total = queries.collections(params).net();
        boolean invoices = access.sees(ModuleCode.INVOICES);
        boolean payouts = access.sees(ModuleCode.PAYOUTS);
        return queries.partnerRevenue(params, limit).stream()
                .map(r -> new PartnerRank(r.partnerId(), r.name(), r.code(), r.tier(), r.status(), money(r.gross()),
                        money(r.net()), share(r.net(), total), invoices ? money(r.invoicedTaxable()) : null,
                        payouts ? money(r.commission()) : null))
                .toList();
    }

    private List<PartnerBalance> balances(LocalDate today) {
        return payoutQueries.balances(null, null, today, PayoutRules.fyStart(today)).stream()
                .map(b -> new PartnerBalance(b.partnerId(), b.partnerName(), b.partnerCode(), b.eligibleCommission(),
                        b.carriedAdjustments(), money(b.available()), b.inProgress(), b.paidFy()))
                .toList();
    }

    private Trend revenueTrend(ReportPeriod period, MapSqlParameterSource params, AnalyticsAccess access) {
        List<SeriesSpec> series = new ArrayList<>();
        if (access.sees(ModuleCode.INVOICES)) {
            series.add(new SeriesSpec("invoicedTaxable", "Invoiced (taxable)", "INR",
                    byDay(queries.invoicedDaily(params)), Daily::b));
        }
        if (access.sees(ModuleCode.PAYMENTS)) {
            series.add(new SeriesSpec("collectedExGst", "Collected (ex-GST)", "INR",
                    byDay(queries.collectedDaily(params)), Daily::b));
        }
        return series.isEmpty() ? null : trend(period, series);
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private record SeriesSpec(String key, String label, String unit, Map<LocalDate, Daily> daily,
                              Function<Daily, BigDecimal> value) {
    }

    /** Folds daily values into the period's buckets (days or months), keeping empty buckets at zero. */
    private static Trend trend(ReportPeriod period, List<SeriesSpec> specs) {
        List<LocalDate> buckets = period.buckets();
        List<Series> series = new ArrayList<>();
        for (SeriesSpec spec : specs) {
            Map<LocalDate, BigDecimal> sums = new LinkedHashMap<>();
            buckets.forEach(b -> sums.put(b, BigDecimal.ZERO));
            spec.daily().forEach((day, d) -> {
                BigDecimal v = spec.value().apply(d);
                if (v != null && !day.isBefore(period.from()) && !day.isAfter(period.to())) {
                    sums.merge(period.bucketOf(day), v, BigDecimal::add);
                }
            });
            boolean count = "COUNT".equals(spec.unit());
            series.add(new Series(spec.key(), spec.label(), spec.unit(), sums.values().stream()
                    .map(v -> count ? v.setScale(0, RoundingMode.HALF_UP) : money(v)).toList()));
        }
        return new Trend(period.granularity().name(), buckets, series);
    }

    private static Map<LocalDate, Daily> byDay(List<Daily> rows) {
        Map<LocalDate, Daily> map = new LinkedHashMap<>();
        rows.forEach(r -> map.put(r.day(), r));
        return map;
    }

    private static PeriodView view(ReportPeriod period, LocalDate today) {
        return PeriodView.of(period, today, AppConstants.BUSINESS_ZONE.getId());
    }

    private static List<Restriction> restricted(AnalyticsAccess access) {
        return access.restricted().entrySet().stream()
                .map(e -> new Restriction(e.getKey().name(), e.getValue())).toList();
    }

    private CompanySettings settings() {
        return settingsRepository.findSingleton()
                .orElseThrow(() -> new IllegalStateException("Company settings are missing"));
    }

    private static Slice slice(String key, String label, long count) {
        return new Slice(key, label, count, null);
    }

    /** Amounts with 2 decimals (HALF_UP); null is zero. */
    static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    /** {@code part / whole × 100} with 2 decimals (HALF_UP); null when the whole is zero. */
    static BigDecimal rate(long part, long whole) {
        return whole == 0 ? null : BigDecimal.valueOf(part).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal share(BigDecimal part, BigDecimal whole) {
        if (part == null || whole == null || whole.signum() == 0) {
            return null;
        }
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private static String humanize(String code) {
        if (code == null) {
            return null;
        }
        String words = code.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static Map<String, String> linked(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
