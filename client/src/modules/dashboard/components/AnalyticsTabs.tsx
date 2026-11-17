import { useEffect } from "react";
import { AlertTriangle, ArrowRightLeft, Banknote, Clock, FileText, Handshake, Hash, Package, Percent, ShoppingCart, Tag, Target, Trophy, UserCheck, UserPlus, Wallet } from "lucide-react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { BarsChart, DonutChart, FunnelChart, TrendChart } from "@/components/charts/Charts";
import { DashboardSection, SummaryPanel } from "@/components/dashboard/Dashboard";
import { ErrorState } from "@/components/feedback/States";
import { Badge } from "@/components/ui/Badge";
import { useAuth } from "@/hooks/useAuth";
import { queryKeys } from "@/lib/queryKeys";
import { LeadFunnelChart } from "@/modules/reports/components/LeadFunnelChart";
import { RevenueChart } from "@/modules/reports/components/RevenueChart";
import { formatCurrency, formatNumber, formatPercent, NOT_AVAILABLE } from "@/utils/format";
import { dashboardService } from "../services/dashboardService";
import type { PartnerBalance, PartnerRank, PeriodFilter, PeriodView, ProductSales, Trend as TrendData } from "../types/dashboard.types";
import { Kpi, KpiGrid, MiniTable, RecordLink, RestrictionNotice, Trend, Unavailable, type MiniColumn } from "./DashboardStats";

/*
 * One component per dashboard tab. Each fetches only its own endpoint while it is open; the server decides which
 * modules' figures are included (read access with the All records scope) and returns null for the rest.
 */

interface TabProps {
  period: PeriodFilter;
  enabled: boolean;
  onResolved?: (period: PeriodView) => void;
}

const money = formatCurrency;
const pct = (v: string | number | null | undefined) => (v === null || v === undefined ? NOT_AVAILABLE : formatPercent(v));
const count = (v: number | null | undefined) => (v === null || v === undefined ? NOT_AVAILABLE : formatNumber(v));

function useSection<T extends { period: PeriodView }>(section: string, fetch: (p: PeriodFilter, s?: AbortSignal) => Promise<T>, { period, enabled, onResolved }: TabProps) {
  const query = useQuery({
    queryKey: queryKeys.dashboard.section(section, { ...period }),
    queryFn: ({ signal }) => fetch(period, signal),
    enabled,
    placeholderData: keepPreviousData,
  });
  const resolved = query.data?.period;
  useEffect(() => {
    if (resolved) onResolved?.(resolved);
  }, [resolved, onResolved]);
  return query;
}

function onlyUnit(trend: TrendData | null | undefined, unit: string): TrendData | null | undefined {
  return trend ? { ...trend, series: trend.series.filter((s) => s.unit === unit) } : trend;
}

function SectionError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return <ErrorState error={error} onRetry={onRetry} />;
}

function rankColumns(canPartners: boolean, extra: { invoiced: boolean; commission: boolean }): MiniColumn<PartnerRank>[] {
  return [
    {
      header: "Partner",
      cell: (r) => (
        <div className="min-w-36">
          <RecordLink to={`/partners/${r.partnerId}`} canOpen={canPartners}>
            {r.name}
          </RecordLink>
          <div className="text-xs text-muted-foreground">
            {r.code ?? NOT_AVAILABLE}
            {r.tier ? ` · ${r.tier}` : ""}
          </div>
        </div>
      ),
    },
    { header: "Collected (ex-GST)", numeric: true, cell: (r) => money(r.collectedExGst) },
    { header: "Share", numeric: true, cell: (r) => pct(r.share) },
    ...(extra.invoiced ? [{ header: "Invoiced (taxable)", numeric: true, cell: (r: PartnerRank) => money(r.invoicedTaxable) }] : []),
    ...(extra.commission ? [{ header: "Commission", numeric: true, cell: (r: PartnerRank) => money(r.commission) }] : []),
  ];
}

function balanceColumns(canPartners: boolean): MiniColumn<PartnerBalance>[] {
  return [
    {
      header: "Partner",
      cell: (b) => (
        <RecordLink to={`/partners/${b.partnerId}`} canOpen={canPartners}>
          {b.name}
        </RecordLink>
      ),
    },
    { header: "Eligible commission", numeric: true, cell: (b) => money(b.eligibleCommission) },
    { header: "Adjustments", numeric: true, cell: (b) => money(b.carriedAdjustments) },
    { header: "In progress", numeric: true, cell: (b) => money(b.inProgress) },
    { header: "Available", numeric: true, cell: (b) => <span className={Number(b.available) < 0 ? "text-danger" : undefined}>{money(b.available)}</span> },
  ];
}

// ------------------------------------------------------------------------------------------------ overview

export function OverviewTab(props: TabProps) {
  const { canRead } = useAuth();
  const q = useSection("overview", dashboardService.overview, props);
  const o = q.data;
  const loading = q.isPending;
  if (q.isError && !o) return <SectionError error={q.error} onRetry={() => q.refetch()} />;
  const partnersOpen = canRead("PARTNERS");
  return (
    <div className="space-y-6">
      <RestrictionNotice restricted={o?.restricted} />
      <KpiGrid>
        {loading || o?.payments ? (
          <Kpi
            label="Collected (ex-GST)" icon={Wallet}
            loading={loading}
            value={o?.payments ? money(o.payments.collectedExGst) : undefined}
            trend={o?.payments ? <Trend current={o.payments.collectedExGst} previous={o.payments.collectedExGstPrevious} /> : undefined}
            hint={o?.payments ? `${money(o.payments.collected)} received incl. GST` : undefined}
            href={canRead("PAYMENTS") ? "/payments" : undefined}
          />
        ) : null}
        {loading || o?.invoices ? (
          <Kpi
            label="Invoiced (taxable)" icon={FileText}
            loading={loading}
            value={o?.invoices ? money(o.invoices.taxable) : undefined}
            trend={o?.invoices ? <Trend current={o.invoices.taxable} previous={o.invoices.taxablePrevious} /> : undefined}
            hint={o?.invoices ? `${formatNumber(o.invoices.issued)} invoices issued` : undefined}
            href={canRead("INVOICES") ? "/invoices" : undefined}
          />
        ) : null}
        {loading || o?.invoices ? (
          <Kpi
            label="Outstanding receivables" icon={Clock}
            loading={loading}
            value={o?.invoices ? money(o.invoices.outstanding) : undefined}
            hint={o?.invoices ? `${formatNumber(o.invoices.outstandingCount)} invoices · ${money(o.invoices.overdue)} overdue` : undefined}
          />
        ) : null}
        {loading || o?.leads ? (
          <Kpi
            label="New leads" icon={UserPlus}
            loading={loading}
            value={o?.leads ? formatNumber(o.leads.newInPeriod) : undefined}
            trend={o?.leads ? <Trend current={o.leads.newInPeriod} previous={o.leads.newPrevious} /> : undefined}
            hint={o?.leads ? `${formatNumber(o.leads.open)} open · ${money(o.leads.pipelineValue)} pipeline` : undefined}
            href={canRead("LEADS") ? "/leads" : undefined}
          />
        ) : null}
        {loading || o?.leads ? (
          <Kpi
            label="Win rate" icon={Trophy}
            loading={loading}
            value={o?.leads ? pct(o.leads.winRate) : undefined}
            hint={o?.leads ? `${formatNumber(o.leads.wonInPeriod)} won · ${formatNumber(o.leads.lostInPeriod)} lost in the period` : undefined}
          />
        ) : null}
        {loading || o?.estimates ? (
          <Kpi
            label="Estimate → invoice" icon={ArrowRightLeft}
            loading={loading}
            value={o?.estimates ? pct(o.estimates.invoiceConversionRate) : undefined}
            hint={o?.estimates ? `${formatNumber(o.estimates.converted)} of ${formatNumber(o.estimates.total - o.estimates.draft)} issued estimates invoiced` : undefined}
          />
        ) : null}
        {loading || o?.partners ? (
          <Kpi
            label="Active partners" icon={Handshake}
            loading={loading}
            value={o?.partners ? formatNumber(o.partners.active) : undefined}
            trend={o?.partners ? <Trend current={o.partners.newInPeriod} previous={o.partners.newPrevious} /> : undefined}
            hint={o?.partners ? `${formatNumber(o.partners.newInPeriod)} new · ${formatNumber(o.partners.total)} total` : undefined}
            href={partnersOpen ? "/partners" : undefined}
          />
        ) : null}
        {loading || o?.payouts ? (
          <Kpi
            label="Commission earned" icon={Banknote}
            loading={loading}
            value={o?.payouts ? money(o.payouts.commissionEarned) : undefined}
            trend={o?.payouts ? <Trend current={o.payouts.commissionEarned} previous={o.payouts.commissionEarnedPrevious} /> : undefined}
            hint={o?.payouts ? `${money(Number(o.payouts.pendingLiability) + Number(o.payouts.approvedLiability))} in open payouts` : undefined}
            href={canRead("PAYOUTS") ? "/payouts" : undefined}
          />
        ) : null}
      </KpiGrid>

      <div className="grid gap-4 xl:grid-cols-2">
        <RevenueChart trend={o?.revenueTrend} loading={loading} />
        <LeadFunnelChart slices={o?.leadFunnel} loading={loading} />
        <BarsChart
          title="Receivables by age"
          description="Outstanding balance of issued invoices by days past due (balances of ₹0.50 or less excluded)."
          slices={o?.receivableAging}
          measure="amount"
          category="Age"
          loading={loading}
          unavailable={!loading && !o?.receivableAging ? "Needs Invoices read access with the All records scope." : undefined}
        />
        <DonutChart
          title="Collections by method"
          description="Standing receipts in the period, net of reversals."
          slices={o?.paymentMethods}
          measure="amount"
          category="Method"
          loading={loading}
          unavailable={!loading && !o?.paymentMethods ? "Needs Payments read access with the All records scope." : undefined}
        />
      </div>
      {o?.topPartners ? (
        <MiniTable
          title="Top partners by collections"
          description="Ex-GST collections in the period; share of all collections."
          columns={rankColumns(partnersOpen, { invoiced: o.topPartners.some((r) => r.invoicedTaxable !== null), commission: o.topPartners.some((r) => r.commission !== null) })}
          rows={o.topPartners}
          rowKey={(r) => r.partnerId}
        />
      ) : null}
    </div>
  );
}

// ------------------------------------------------------------------------------------------------ sales

export function SalesTab(props: TabProps) {
  const { canRead } = useAuth();
  const q = useSection("sales", dashboardService.sales, props);
  const s = q.data;
  const loading = q.isPending;
  if (q.isError && !s) return <SectionError error={q.error} onRetry={() => q.refetch()} />;
  const k = s?.kpis;
  if (!loading && !k) {
    return (
      <div className="space-y-4">
        <RestrictionNotice restricted={s?.restricted} modules={["LEADS", "ESTIMATES"]} />
        <Unavailable title="Lead analytics are not available">Pipeline, conversion and follow-ups need Leads read access with the All records scope.</Unavailable>
      </div>
    );
  }
  const c = s?.conversion;
  const f = s?.followUps;
  return (
    <div className="space-y-6">
      <RestrictionNotice restricted={s?.restricted} modules={["LEADS", "ESTIMATES", "PARTNERS"]} />
      <KpiGrid>
        <Kpi label="New leads" icon={UserPlus} loading={loading} value={k ? formatNumber(k.newInPeriod) : undefined} trend={k ? <Trend current={k.newInPeriod} previous={k.newPrevious} /> : undefined} hint={k ? `${formatNumber(k.total)} leads in total` : undefined} />
        <Kpi label="Open pipeline" icon={Target} loading={loading} value={k ? money(k.pipelineValue) : undefined} hint={k ? `${formatNumber(k.open)} open · ${formatNumber(k.openWithoutBudget)} without a budget` : undefined} />
        <Kpi label="Won · lost" icon={Trophy} loading={loading} value={k ? `${formatNumber(k.wonInPeriod)} · ${formatNumber(k.lostInPeriod)}` : undefined} hint={k ? `Win rate ${pct(k.winRate)}` : undefined} />
        <Kpi label="Lead → estimate" icon={ArrowRightLeft} loading={loading} value={c ? pct(c.leadToEstimateRate) : undefined} hint={c ? `${count(c.leadsWithEstimate)} of ${formatNumber(c.leadsCreated)} new leads` : undefined} />
      </KpiGrid>

      <div className="grid gap-4 xl:grid-cols-2">
        <TrendChart title="Leads created, won and lost" description="Won and lost count the latest move into the closed stage; a reopened lead counts once." trend={s?.trend} loading={loading} />
        <LeadFunnelChart slices={s?.funnel} loading={loading} />
        <FunnelChart
          title="Lead to estimate"
          description="Leads created in the period and how many have an estimate (any live estimate, drafts included)."
          steps={c ? [{ label: "Leads created", count: c.leadsCreated }, { label: "With an estimate", count: c.leadsWithEstimate }] : null}
          loading={loading}
          unavailable={!loading && c?.leadsWithEstimate === null ? "Needs Estimates read access with the All records scope." : undefined}
        />
        <FunnelChart
          title="Estimate to invoice"
          description="Estimates issued in the period and how many became an invoice (cancelled invoices excluded)."
          steps={c ? [{ label: "Estimates issued", count: c.estimatesIssued }, { label: "Invoiced", count: c.estimatesInvoiced }] : null}
          loading={loading}
          unavailable={!loading && c?.estimatesIssued === null ? "Needs Estimates read access with the All records scope." : undefined}
        />
        <BarsChart title="Open leads by age" description="Days since the lead was created." slices={s?.aging} measure="count" category="Age" loading={loading} />
        {f ? (
          <SummaryPanel
            title="Follow-ups"
            rows={[
              { label: `Not acknowledged within ${f.ackHours} h`, value: formatNumber(f.unacknowledged) },
              { label: `No estimate after ${f.estimateDays} days`, value: formatNumber(f.awaitingEstimate) },
              { label: "Past expected close date", value: formatNumber(f.pastExpectedClose) },
              { label: "SLA breaches for leads created in the period", value: formatNumber(f.slaViolationsInPeriod), emphasis: true },
            ]}
          />
        ) : null}
      </div>
      {s?.byPartner ? (
        <MiniTable
          title="Leads by partner"
          description="Leads created in the period, current open pipeline and results; top 20 by leads created."
          rows={s.byPartner}
          rowKey={(r) => r.partnerId}
          columns={[
            { header: "Partner", cell: (r) => <RecordLink to={`/partners/${r.partnerId}`} canOpen={canRead("PARTNERS")}>{r.name}</RecordLink> },
            { header: "Created", numeric: true, cell: (r) => formatNumber(r.created) },
            { header: "Open", numeric: true, cell: (r) => formatNumber(r.open) },
            { header: "Won", numeric: true, cell: (r) => formatNumber(r.won) },
            { header: "Lost", numeric: true, cell: (r) => formatNumber(r.lost) },
            { header: "Win rate", numeric: true, cell: (r) => pct(r.winRate) },
            { header: "Pipeline", numeric: true, cell: (r) => money(r.pipeline) },
          ]}
        />
      ) : null}
    </div>
  );
}

// ------------------------------------------------------------------------------------------------ partners

export function PartnersTab(props: TabProps) {
  const { canRead } = useAuth();
  const q = useSection("partners", dashboardService.partners, props);
  const p = q.data;
  const loading = q.isPending;
  if (q.isError && !p) return <SectionError error={q.error} onRetry={() => q.refetch()} />;
  const k = p?.kpis;
  if (!loading && !k) {
    return (
      <div className="space-y-4">
        <RestrictionNotice restricted={p?.restricted} modules={["PARTNERS"]} />
        <Unavailable title="Partner analytics are not available">They need Partners read access with the All records scope.</Unavailable>
      </div>
    );
  }
  const partnersOpen = canRead("PARTNERS");
  return (
    <div className="space-y-6">
      <RestrictionNotice restricted={p?.restricted} modules={["PARTNERS", "PAYMENTS", "INVOICES", "PAYOUTS"]} />
      <KpiGrid>
        <Kpi label="Active partners" icon={Handshake} loading={loading} value={k ? formatNumber(k.active) : undefined} hint={k ? `${formatNumber(k.inactive)} inactive · ${formatNumber(k.total)} total` : undefined} />
        <Kpi label="In onboarding" icon={UserCheck} loading={loading} value={k ? formatNumber(k.onboarding + k.prospect) : undefined} hint={k ? `${formatNumber(k.prospect)} prospects · ${formatNumber(k.onboarding)} onboarding` : undefined} />
        <Kpi label="New partners" icon={UserPlus} loading={loading} value={k ? formatNumber(k.newInPeriod) : undefined} trend={k ? <Trend current={k.newInPeriod} previous={k.newPrevious} /> : undefined} />
        {loading || (p?.realizedRevenue !== null && p?.realizedRevenue !== undefined) ? (
          <Kpi label="Partner revenue (ex-GST)" icon={Wallet} loading={loading} value={p?.realizedRevenue != null ? money(p.realizedRevenue) : undefined} hint="Collected through partners in the period" />
        ) : null}
      </KpiGrid>
      <div className="grid gap-4 xl:grid-cols-2">
        <DonutChart title="Partners by status" slices={p?.statuses} measure="count" category="Status" loading={loading} />
        <DonutChart title="Active partners by tier" description="Current tier; commission already earned keeps the rate snapshotted on its invoice." slices={p?.tierMix} measure="count" category="Tier" loading={loading} />
        <TrendChart title="New partners" trend={onlyUnit(p?.trend, "COUNT")} loading={loading} />
        <TrendChart title="Collected through partners" description="Ex-GST share of standing receipts." trend={onlyUnit(p?.trend, "INR")} loading={loading} unavailable={!loading && !p?.trend?.series.some((s) => s.unit === "INR") ? "Needs Payments read access with the All records scope." : undefined} />
      </div>
      {p?.rankings ? (
        <MiniTable
          title="Partner ranking"
          description="Top 10 by ex-GST collections in the period."
          columns={rankColumns(partnersOpen, { invoiced: p.rankings.some((r) => r.invoicedTaxable !== null), commission: p.rankings.some((r) => r.commission !== null) })}
          rows={p.rankings}
          rowKey={(r) => r.partnerId}
        />
      ) : null}
      {p?.negativeBalances && p.negativeBalances.length > 0 ? (
        <MiniTable title="Negative balances" description="Partners whose recoveries exceed eligible commission; recovered from future payouts." columns={balanceColumns(partnersOpen)} rows={p.negativeBalances} rowKey={(b) => b.partnerId} />
      ) : null}
      {p?.balances ? (
        <MiniTable title="Outstanding payout balances" description="Commission eligible today and not yet in a payout, per partner." columns={balanceColumns(partnersOpen)} rows={p.balances} rowKey={(b) => b.partnerId} empty="No partner has a payout balance." />
      ) : null}
    </div>
  );
}

// ------------------------------------------------------------------------------------------------ products

function productColumns(canProducts: boolean, by: "value" | "quantity"): MiniColumn<ProductSales>[] {
  return [
    {
      header: "Product",
      cell: (r) => (
        <div className="min-w-40">
          {r.productId ? (
            <RecordLink to={`/products/${r.productId}`} canOpen={canProducts}>
              {r.name}
            </RecordLink>
          ) : (
            r.name
          )}
          <div className="text-xs text-muted-foreground">{[r.sku, r.category].filter(Boolean).join(" · ") || NOT_AVAILABLE}</div>
        </div>
      ),
    },
    ...(by === "quantity"
      ? [{ header: "Quantity", numeric: true, cell: (r: ProductSales) => formatNumber(r.quantity) }, { header: "Taxable", numeric: true, cell: (r: ProductSales) => money(r.taxable) }]
      : [{ header: "Taxable", numeric: true, cell: (r: ProductSales) => money(r.taxable) }, { header: "Share", numeric: true, cell: (r: ProductSales) => pct(r.share) }]),
    { header: "Invoices", numeric: true, cell: (r) => formatNumber(r.invoices) },
  ];
}

export function ProductsTab(props: TabProps) {
  const { canRead } = useAuth();
  const q = useSection("products", dashboardService.products, props);
  const p = q.data;
  const loading = q.isPending;
  if (q.isError && !p) return <SectionError error={q.error} onRetry={() => q.refetch()} />;
  const k = p?.kpis;
  if (!loading && !k) return <Unavailable title="Product analytics are not available">They need Products read access.</Unavailable>;
  const canProducts = canRead("PRODUCTS");
  return (
    <div className="space-y-6">
      <RestrictionNotice restricted={p?.restricted} modules={["INVOICES"]} />
      <KpiGrid>
        <Kpi label="Active products" icon={Package} loading={loading} value={k ? formatNumber(k.active) : undefined} hint={k ? `${formatNumber(k.inactive)} inactive · ${formatNumber(k.total)} total` : undefined} />
        <Kpi label="Missing partner price" icon={Tag} loading={loading} value={k ? formatNumber(k.missingPartnerPrice) : undefined} hint="Active products" />
        <Kpi label="Missing HSN code" icon={Hash} loading={loading} value={k ? formatNumber(k.missingHsn) : undefined} hint="Active products" />
        {loading || (p?.totalTaxable !== null && p?.totalTaxable !== undefined) ? (
          <Kpi label="Product sales (taxable)" icon={ShoppingCart} loading={loading} value={p?.totalTaxable != null ? money(p.totalTaxable) : undefined} hint={p?.unlistedTaxable != null ? `${money(p.unlistedTaxable)} from lines not in the price sheet` : undefined} />
        ) : null}
      </KpiGrid>
      {k?.missingB2cPrice !== null && k?.missingB2cPrice !== undefined ? (
        <p className="text-sm text-muted-foreground">
          <Badge tone="outline">Internal</Badge> {formatNumber(k.missingB2cPrice)} active products have no internal B2C price.
        </p>
      ) : null}
      <div className="grid gap-4 xl:grid-cols-2">
        <DonutChart title="Products by category" slices={p?.categories} measure="count" category="Category" loading={loading} />
        <BarsChart
          title="Sales by category"
          description="Taxable value of invoice lines in the period, from the line snapshots."
          slices={p?.categoryRevenue}
          measure="amount"
          layout="horizontal"
          category="Category"
          loading={loading}
          unavailable={!loading && !p?.categoryRevenue ? "Needs Invoices read access with the All records scope." : undefined}
        />
      </div>
      {p?.topByValue ? (
        <div className="grid gap-4 xl:grid-cols-2">
          <MiniTable title="Top products by value" columns={productColumns(canProducts, "value")} rows={p.topByValue} rowKey={(r) => r.productId ?? r.name} />
          <MiniTable title="Top products by quantity" columns={productColumns(canProducts, "quantity")} rows={p.topByQuantity} rowKey={(r) => r.productId ?? r.name} />
        </div>
      ) : null}
    </div>
  );
}

// ------------------------------------------------------------------------------------------------ finance

export function FinanceTab(props: TabProps) {
  const { canRead } = useAuth();
  const q = useSection("finance", dashboardService.finance, props);
  const f = q.data;
  const loading = q.isPending;
  if (q.isError && !f) return <SectionError error={q.error} onRetry={() => q.refetch()} />;
  if (!loading && !f?.invoicing && !f?.collections && !f?.payouts) {
    return (
      <div className="space-y-4">
        <RestrictionNotice restricted={f?.restricted} modules={["INVOICES", "PAYMENTS", "PAYOUTS"]} />
        <Unavailable title="Financial analytics are not available">They need Invoices, Payments or Payouts read access with the All records scope.</Unavailable>
      </div>
    );
  }
  const inv = f?.invoicing;
  const col = f?.collections;
  const pay = f?.payouts;
  return (
    <div className="space-y-6">
      <RestrictionNotice restricted={f?.restricted} modules={["INVOICES", "PAYMENTS", "PAYOUTS", "PARTNERS"]} />
      {loading || inv || col ? (
        <DashboardSection title="Invoicing and collections">
          <KpiGrid>
            {loading || inv ? (
              <Kpi label="Invoiced (gross)" icon={FileText} loading={loading} value={inv ? money(inv.gross) : undefined} hint={inv ? `${money(inv.taxable)} taxable · ${money(inv.gst)} GST` : undefined} />
            ) : null}
            {loading || col ? (
              <Kpi label="Collected" icon={Wallet} loading={loading} value={col ? money(col.collected) : undefined} hint={col ? `${formatNumber(col.receipts)} receipts · ${money(col.gstCollected)} GST` : undefined} />
            ) : null}
            {loading || (f?.collectionEfficiency !== null && f?.collectionEfficiency !== undefined) ? (
              <Kpi label="Collected to invoiced (indicative)" icon={Percent} loading={loading} value={f?.collectionEfficiency != null ? pct(f.collectionEfficiency) : undefined} hint="Collected ÷ invoiced (gross) in the same period; receipts may relate to earlier invoices. Not an approved KPI." />
            ) : null}
            {loading || inv ? (
              <Kpi label="Overdue" icon={AlertTriangle} loading={loading} value={inv ? money(inv.overdue) : undefined} hint={inv ? `${formatNumber(inv.overdueCount)} of ${formatNumber(inv.outstandingCount)} outstanding invoices` : undefined} />
            ) : null}
          </KpiGrid>
          <div className="grid gap-4 lg:grid-cols-3">
            {inv ? (
              <SummaryPanel
                title="Receivables"
                rows={[
                  { label: `Outstanding (${formatNumber(inv.outstandingCount)})`, value: money(inv.outstanding) },
                  { label: `Overdue (${formatNumber(inv.overdueCount)})`, value: money(inv.overdue) },
                  { label: `Closed within ₹0.50 tolerance (${formatNumber(inv.toleranceCount)})`, value: money(inv.toleranceResidual) },
                  { label: `Cancelled in period (${formatNumber(inv.cancelled)})`, value: money(inv.cancelledValue) },
                ]}
              />
            ) : null}
            {col ? (
              <SummaryPanel
                title="Collections"
                rows={[
                  { label: "Received (standing receipts)", value: money(col.collected) },
                  { label: "Of which GST", value: money(col.gstCollected) },
                  { label: `Reversed in period (${formatNumber(col.reversals)})`, value: money(col.reversedAmount) },
                  { label: `Partly paid invoices (${formatNumber(col.partlyPaidInvoices)})`, value: money(col.partlyPaidOutstanding) },
                  { label: "Collected (ex-GST)", value: money(col.collectedExGst), emphasis: true },
                ]}
              />
            ) : null}
            {pay ? (
              <SummaryPanel
                title="Payouts"
                rows={[
                  { label: "Commission earned in period", value: money(pay.commissionEarned) },
                  { label: "Earned, not yet in a payout", value: money(pay.unbilledCommission) },
                  { label: "Draft and submitted payouts", value: money(pay.pendingLiability) },
                  { label: "Approved or processing", value: money(pay.approvedLiability) },
                  { label: `Paid in period (${formatNumber(pay.paidCount)}) · TDS ${money(pay.paidTds)}`, value: money(pay.paidNet), emphasis: true },
                ]}
              />
            ) : null}
          </div>
        </DashboardSection>
      ) : null}

      <div className="grid gap-4 xl:grid-cols-2">
        <RevenueChart trend={f?.trend} loading={loading} />
        <BarsChart title="Receivables by age" description="Days past due; balances of ₹0.50 or less are excluded." slices={f?.receivableAging} measure="amount" category="Age" loading={loading} unavailable={!loading && !f?.receivableAging ? "Needs Invoices read access with the All records scope." : undefined} />
        <DonutChart title="Collections by method" slices={f?.paymentMethods} measure="amount" category="Method" loading={loading} unavailable={!loading && !f?.paymentMethods ? "Needs Payments read access with the All records scope." : undefined} />
        {pay ? (
          <MiniTable
            title="Payouts by status"
            caption="Payouts by status (all dates)"
            rows={pay.byStatus}
            rowKey={(r) => r.status}
            empty="No payouts yet."
            columns={[
              { header: "Status", cell: (r) => r.label },
              { header: "Payouts", numeric: true, cell: (r) => formatNumber(r.count) },
              { header: "Commission", numeric: true, cell: (r) => money(r.commission) },
              { header: "TDS", numeric: true, cell: (r) => money(r.tds) },
              { header: "Net", numeric: true, cell: (r) => money(r.net) },
            ]}
          />
        ) : null}
      </div>
      {pay && pay.negativeAdjustments > 0 ? (
        <p className="text-sm text-muted-foreground">
          {formatNumber(pay.negativeAdjustments)} approved negative adjustments still to recover: {money(pay.negativeAdjustmentBalance)}.
          {pay.adjustmentsAwaitingApproval > 0 ? ` ${formatNumber(pay.adjustmentsAwaitingApproval)} adjustments await approval.` : ""}
        </p>
      ) : null}
      {f?.balances ? (
        <MiniTable title="Partner payout balances" description="Eligible commission not yet in a payout, with carried adjustments." columns={balanceColumns(canRead("PARTNERS"))} rows={f.balances} rowKey={(b) => b.partnerId} empty="No partner has a payout balance." />
      ) : null}
    </div>
  );
}
