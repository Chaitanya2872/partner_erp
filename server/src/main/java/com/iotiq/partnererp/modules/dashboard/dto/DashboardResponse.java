package com.iotiq.partnererp.modules.dashboard.dto;

import com.iotiq.partnererp.modules.reports.service.ReportPeriod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Dashboard and analytics payloads (Phase 12). Every figure is calculated on the server; the client only formats it.
 * A section is null when the caller can't see the module behind it (listed in {@code restricted}); a rate is null
 * when its denominator is zero. Amounts are rupees with 2 decimals, rates are percentages with 2 decimals (HALF_UP).
 * Metric definitions: DECISIONS.md §M.
 */
public final class DashboardResponse {

    private DashboardResponse() {
    }

    public record PeriodView(String preset, LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo,
                             String granularity, LocalDate today, String timezone) {

        public static PeriodView of(ReportPeriod p, LocalDate today, String zone) {
            return new PeriodView(p.preset().name(), p.from(), p.to(), p.previousFrom(), p.previousTo(),
                    p.granularity().name(), today, zone);
        }
    }

    /** A module whose figures were left out, and why (NO_ACCESS, SCOPE_SELF or SCOPE_TEAM). */
    public record Restriction(String module, String reason) {
    }

    /** One slice of a distribution (stage, tier, method, bucket, category). */
    public record Slice(String key, String label, long count, BigDecimal amount) {
    }

    public record Series(String key, String label, String unit, List<BigDecimal> values) {
    }

    /** Values per bucket; empty buckets are zero. */
    public record Trend(String granularity, List<LocalDate> buckets, List<Series> series) {
    }

    public record PartnerKpis(long total, long active, long inactive, long prospect, long onboarding,
                              long newInPeriod, long newPrevious) {
    }

    public record LeadKpis(long total, long newInPeriod, long newPrevious, long open, BigDecimal pipelineValue,
                           long openWithoutBudget, long wonInPeriod, long lostInPeriod, BigDecimal winRate,
                           long wonTotal, BigDecimal conversionRate) {
    }

    public record EstimateKpis(long total, long draft, long sent, long accepted, long rejected, long invoiced,
                               BigDecimal value, long converted, BigDecimal invoiceConversionRate) {
    }

    public record InvoiceKpis(long issued, BigDecimal gross, BigDecimal taxable, BigDecimal gst,
                              BigDecimal taxablePrevious, long cancelled, BigDecimal cancelledValue,
                              long outstandingCount, BigDecimal outstanding, long overdueCount, BigDecimal overdue,
                              long toleranceCount, BigDecimal toleranceResidual) {
    }

    public record PaymentKpis(long receipts, BigDecimal collected, BigDecimal collectedExGst, BigDecimal gstCollected,
                              BigDecimal collectedExGstPrevious, long reversals, BigDecimal reversedAmount,
                              long partlyPaidInvoices, BigDecimal partlyPaidOutstanding) {
    }

    public record StatusTotal(String status, String label, long count, BigDecimal commission, BigDecimal tds,
                              BigDecimal net) {
    }

    public record PayoutKpis(BigDecimal commissionEarned, BigDecimal commissionEarnedPrevious,
                             BigDecimal unbilledCommission, BigDecimal pendingLiability, BigDecimal approvedLiability,
                             long paidCount, BigDecimal paidCommission, BigDecimal paidTds, BigDecimal paidNet,
                             long negativeAdjustments, BigDecimal negativeAdjustmentBalance,
                             long adjustmentsAwaitingApproval, List<StatusTotal> byStatus) {
    }

    /** Ranking row. Figures the caller can't see (invoices, payouts) are null. */
    public record PartnerRank(UUID partnerId, String name, String code, String tier, String status,
                              BigDecimal collected, BigDecimal collectedExGst, BigDecimal share,
                              BigDecimal invoicedTaxable, BigDecimal commission) {
    }

    public record PartnerBalance(UUID partnerId, String name, String code, BigDecimal eligibleCommission,
                                 BigDecimal carriedAdjustments, BigDecimal available, BigDecimal inProgress,
                                 BigDecimal paidFy) {
    }

    public record Overview(PeriodView period, List<Restriction> restricted, PartnerKpis partners, LeadKpis leads,
                           EstimateKpis estimates, InvoiceKpis invoices, PaymentKpis payments, PayoutKpis payouts,
                           Trend revenueTrend, List<Slice> leadFunnel, List<Slice> tierMix,
                           List<Slice> paymentMethods, List<Slice> receivableAging, List<PartnerRank> topPartners) {
    }

    public record FollowUps(long pastExpectedClose, long unacknowledged, long awaitingEstimate,
                            long slaViolationsInPeriod, int ackHours, int estimateDays) {
    }

    public record PartnerLeads(UUID partnerId, String name, long created, long open, long won, long lost,
                               BigDecimal winRate, BigDecimal pipeline) {
    }

    public record Conversion(long leadsCreated, Long leadsWithEstimate, BigDecimal leadToEstimateRate,
                             Long estimatesIssued, Long estimatesInvoiced, BigDecimal estimateToInvoiceRate,
                             long won, long lost, BigDecimal winRate) {
    }

    public record Sales(PeriodView period, List<Restriction> restricted, LeadKpis kpis, List<Slice> funnel,
                        Trend trend, List<Slice> aging, FollowUps followUps, List<PartnerLeads> byPartner,
                        Conversion conversion, EstimateKpis estimates) {
    }

    public record Partners(PeriodView period, List<Restriction> restricted, PartnerKpis kpis, List<Slice> statuses,
                           List<Slice> tierMix, BigDecimal realizedRevenue, List<PartnerRank> rankings, Trend trend,
                           List<PartnerBalance> balances, List<PartnerBalance> negativeBalances) {
    }

    public record ProductKpis(long total, long active, long inactive, long missingPartnerPrice, long missingHsn,
                              Long missingB2cPrice) {
    }

    public record ProductSales(UUID productId, String sku, String name, String category, BigDecimal quantity,
                               BigDecimal taxable, BigDecimal share, long invoices) {
    }

    public record Products(PeriodView period, List<Restriction> restricted, ProductKpis kpis, List<Slice> categories,
                           BigDecimal totalTaxable, BigDecimal unlistedTaxable, List<ProductSales> topByValue,
                           List<ProductSales> topByQuantity, List<Slice> categoryRevenue) {
    }

    public record Finance(PeriodView period, List<Restriction> restricted, InvoiceKpis invoicing,
                          List<Slice> receivableAging, PaymentKpis collections, List<Slice> paymentMethods,
                          Trend trend, BigDecimal collectionEfficiency, PayoutKpis payouts,
                          List<PartnerBalance> balances) {
    }

    public record AlertItem(String id, String label, String detail, String href) {
    }

    /** One kind of attention item with its count, optional total amount and the first few records. */
    public record AlertGroup(String code, String severity, String module, String title, String detail, long count,
                             BigDecimal amount, String href, List<AlertItem> items) {
    }

    public record Alerts(List<Restriction> restricted, List<AlertGroup> groups, Instant generatedAt) {
    }
}
