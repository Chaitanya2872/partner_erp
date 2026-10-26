/** Phase 12 dashboard payloads (`/api/v1/dashboard/*`). Amounts arrive as decimal strings, rounded to 2 dp by the server. */
export type Money = number | string;

export type PeriodPreset = "TODAY" | "LAST_7_DAYS" | "LAST_30_DAYS" | "THIS_MONTH" | "LAST_MONTH" | "THIS_QUARTER" | "THIS_FY" | "CUSTOM";

/** Preset plus the custom range; dates are Asia/Kolkata business dates, both ends inclusive. */
export interface PeriodFilter {
  preset: PeriodPreset;
  from: string;
  to: string;
}

export interface PeriodView {
  preset: PeriodPreset;
  from: string;
  to: string;
  previousFrom: string;
  previousTo: string;
  granularity: "DAY" | "MONTH";
  today: string;
  timezone: string;
}

/** A module the caller can't aggregate: NO_ACCESS, or a SELF/TEAM scope (organisation-wide figures are withheld). */
export interface Restriction {
  module: string;
  reason: "NO_ACCESS" | "SCOPE_SELF" | "SCOPE_TEAM" | string;
}

export interface Slice {
  key: string;
  label: string;
  count: number;
  amount: Money | null;
}

export interface Series {
  key: string;
  label: string;
  unit: "INR" | "COUNT" | string;
  values: Money[];
}

export interface Trend {
  granularity: "DAY" | "MONTH";
  buckets: string[];
  series: Series[];
}

export interface PartnerKpis {
  total: number;
  active: number;
  inactive: number;
  prospect: number;
  onboarding: number;
  newInPeriod: number;
  newPrevious: number;
}

export interface LeadKpis {
  total: number;
  newInPeriod: number;
  newPrevious: number;
  open: number;
  pipelineValue: Money;
  openWithoutBudget: number;
  wonInPeriod: number;
  lostInPeriod: number;
  winRate: Money | null;
  wonTotal: number;
  conversionRate: Money | null;
}

export interface EstimateKpis {
  total: number;
  draft: number;
  sent: number;
  accepted: number;
  rejected: number;
  invoiced: number;
  value: Money;
  converted: number;
  invoiceConversionRate: Money | null;
}

export interface InvoiceKpis {
  issued: number;
  gross: Money;
  taxable: Money;
  gst: Money;
  taxablePrevious: Money;
  cancelled: number;
  cancelledValue: Money;
  outstandingCount: number;
  outstanding: Money;
  overdueCount: number;
  overdue: Money;
  toleranceCount: number;
  toleranceResidual: Money;
}

export interface PaymentKpis {
  receipts: number;
  collected: Money;
  collectedExGst: Money;
  gstCollected: Money;
  collectedExGstPrevious: Money;
  reversals: number;
  reversedAmount: Money;
  partlyPaidInvoices: number;
  partlyPaidOutstanding: Money;
}

export interface StatusTotal {
  status: string;
  label: string;
  count: number;
  commission: Money;
  tds: Money;
  net: Money;
}

export interface PayoutKpis {
  commissionEarned: Money;
  commissionEarnedPrevious: Money;
  unbilledCommission: Money;
  pendingLiability: Money;
  approvedLiability: Money;
  paidCount: number;
  paidCommission: Money;
  paidTds: Money;
  paidNet: Money;
  negativeAdjustments: number;
  negativeAdjustmentBalance: Money;
  adjustmentsAwaitingApproval: number;
  byStatus: StatusTotal[];
}

export interface PartnerRank {
  partnerId: string;
  name: string;
  code: string | null;
  tier: string | null;
  status: string;
  collected: Money;
  collectedExGst: Money;
  share: Money | null;
  invoicedTaxable: Money | null;
  commission: Money | null;
}

export interface PartnerBalance {
  partnerId: string;
  name: string;
  code: string | null;
  eligibleCommission: Money;
  carriedAdjustments: Money;
  available: Money;
  inProgress: Money;
  paidFy: Money;
}

interface Scoped {
  period: PeriodView;
  restricted: Restriction[];
}

export interface Overview extends Scoped {
  partners: PartnerKpis | null;
  leads: LeadKpis | null;
  estimates: EstimateKpis | null;
  invoices: InvoiceKpis | null;
  payments: PaymentKpis | null;
  payouts: PayoutKpis | null;
  revenueTrend: Trend | null;
  leadFunnel: Slice[] | null;
  tierMix: Slice[] | null;
  paymentMethods: Slice[] | null;
  receivableAging: Slice[] | null;
  topPartners: PartnerRank[] | null;
}

export interface FollowUps {
  pastExpectedClose: number;
  unacknowledged: number;
  awaitingEstimate: number;
  slaViolationsInPeriod: number;
  ackHours: number;
  estimateDays: number;
}

export interface PartnerLeads {
  partnerId: string;
  name: string;
  created: number;
  open: number;
  won: number;
  lost: number;
  winRate: Money | null;
  pipeline: Money;
}

export interface Conversion {
  leadsCreated: number;
  leadsWithEstimate: number | null;
  leadToEstimateRate: Money | null;
  estimatesIssued: number | null;
  estimatesInvoiced: number | null;
  estimateToInvoiceRate: Money | null;
  won: number;
  lost: number;
  winRate: Money | null;
}

export interface Sales extends Scoped {
  kpis: LeadKpis | null;
  funnel: Slice[] | null;
  trend: Trend | null;
  aging: Slice[] | null;
  followUps: FollowUps | null;
  byPartner: PartnerLeads[] | null;
  conversion: Conversion | null;
  estimates: EstimateKpis | null;
}

export interface Partners extends Scoped {
  kpis: PartnerKpis | null;
  statuses: Slice[] | null;
  tierMix: Slice[] | null;
  realizedRevenue: Money | null;
  rankings: PartnerRank[] | null;
  trend: Trend | null;
  balances: PartnerBalance[] | null;
  negativeBalances: PartnerBalance[] | null;
}

export interface ProductKpis {
  total: number;
  active: number;
  inactive: number;
  missingPartnerPrice: number;
  missingHsn: number;
  /** Only for callers with the internal B2C permission. */
  missingB2cPrice: number | null;
}

export interface ProductSales {
  productId: string | null;
  sku: string | null;
  name: string;
  category: string | null;
  quantity: Money;
  taxable: Money;
  share: Money | null;
  invoices: number;
}

export interface Products extends Scoped {
  kpis: ProductKpis | null;
  categories: Slice[] | null;
  totalTaxable: Money | null;
  unlistedTaxable: Money | null;
  topByValue: ProductSales[] | null;
  topByQuantity: ProductSales[] | null;
  categoryRevenue: Slice[] | null;
}

export interface Finance extends Scoped {
  invoicing: InvoiceKpis | null;
  receivableAging: Slice[] | null;
  collections: PaymentKpis | null;
  paymentMethods: Slice[] | null;
  trend: Trend | null;
  collectionEfficiency: Money | null;
  payouts: PayoutKpis | null;
  balances: PartnerBalance[] | null;
}

export interface AlertItem {
  id: string;
  label: string;
  detail: string | null;
  href: string | null;
}

export interface AlertGroup {
  code: string;
  severity: "danger" | "warning" | "info";
  module: string;
  title: string;
  detail: string;
  count: number;
  amount: Money | null;
  href: string | null;
  items: AlertItem[];
}

export interface Alerts {
  restricted: Restriction[];
  groups: AlertGroup[];
  generatedAt: string;
}
