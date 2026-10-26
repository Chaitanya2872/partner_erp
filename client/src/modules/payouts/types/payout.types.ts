/** Wire types for /api/v1/payouts and /api/v1/settings/tds (server: modules/payouts/dto). Money is a number or decimal string. */
import type { Money } from "@/modules/partners/types/partner.types";

/** payouts.status (V18). Paid, rejected, cancelled and failed payouts never change. */
export type PayoutStatus = "DRAFT" | "PENDING_APPROVAL" | "APPROVED" | "PROCESSING" | "PAID" | "REJECTED" | "CANCELLED" | "FAILED";
/** CALCULATED with a confirmed configuration; otherwise approval is blocked. LEGACY: carried over from Phase 2. */
export type TdsStatus = "CALCULATED" | "CONFIG_INCOMPLETE" | "PAN_NOT_VERIFIED" | "LEGACY";
export type AdjustmentStatus = "PENDING_APPROVAL" | "APPROVED" | "REJECTED";
export type AdjustmentKind = "REVERSAL" | "MANUAL";

export interface Option {
  value: string;
  label: string;
}

export interface PayoutSummary {
  id: string;
  payoutNumber: string | null;
  partner: { id: string; name: string };
  status: PayoutStatus;
  statusLabel: string;
  legacy: boolean;
  asOfDate: string;
  fyLabel: string;
  commissionTotal: Money;
  tdsStatus: TdsStatus;
  tdsAmount: Money | null;
  netAmount: Money | null;
  paidDate: string | null;
  /** A receipt in the payout was reversed after allocation. */
  blocked: boolean;
  createdAt: string;
}

export interface PayoutTds {
  status: TdsStatus;
  /** Why TDS isn't calculated, when it isn't. */
  message: string | null;
  configurationId: string | null;
  sectionCode: string | null;
  ratePercent: Money | null;
  fyThreshold: Money | null;
  panVerified: boolean;
  priorFyCommission: Money | null;
  priorFyTdsBase: Money | null;
  tdsBase: Money | null;
  /** TDS from the configuration; `tdsAmount` differs when it was adjusted by hand. */
  computedTds: Money | null;
  tdsAmount: Money | null;
  adjusted: boolean;
}

export interface PayoutLine {
  id: string | null;
  lineType: "COMMISSION" | "ADJUSTMENT";
  paymentId: string | null;
  paymentNumber: string | null;
  invoiceId: string | null;
  invoiceNumber: string | null;
  paymentDate: string | null;
  baseAmount: Money | null;
  commissionPercent: Money | null;
  amount: Money;
  adjustmentId: string | null;
  adjustmentNumber: string | null;
  adjustmentKind: string | null;
  adjustmentReason: string | null;
  sourceReversed: boolean;
  releasedAt: string | null;
  releaseReason: string | null;
}

export interface PayoutCalculation {
  id: string;
  event: "GENERATED" | "RECALCULATED" | "ADJUSTED" | "APPROVED" | "LEGACY";
  status: TdsStatus;
  sectionCode: string | null;
  ratePercent: Money | null;
  fyThreshold: Money | null;
  fyLabel: string;
  panVerified: boolean;
  priorFyCommission: Money;
  priorFyTdsBase: Money;
  commissionTotal: Money;
  tdsBase: Money | null;
  computedTds: Money | null;
  tdsAmount: Money | null;
  adjustmentReason: string | null;
  createdAt: string;
  createdByName: string | null;
}

export interface PayoutActions {
  recalculate: boolean;
  adjustTds: boolean;
  submit: boolean;
  approve: boolean;
  reject: boolean;
  cancel: boolean;
  startProcessing: boolean;
  markPaid: boolean;
  fail: boolean;
}

export interface Payout {
  id: string;
  payoutNumber: string | null;
  status: PayoutStatus;
  statusLabel: string;
  legacy: boolean;
  partner: {
    id: string;
    name: string;
    partnerCode: string | null;
    panOnFile: boolean;
    /** Full value only when `sensitiveVisible`; otherwise masked. */
    pan: string | null;
    panVerified: boolean;
    bankName: string | null;
    bankAccount: string | null;
    ifsc: string | null;
    bankOnFile: boolean;
    sensitiveVisible: boolean;
  };
  asOfDate: string;
  fyStart: string;
  fyLabel: string;
  receiptsTotal: Money;
  adjustmentsTotal: Money;
  commissionTotal: Money;
  tds: PayoutTds;
  netAmount: Money | null;
  notes: string | null;
  lines: PayoutLine[];
  releasedLines: PayoutLine[];
  calculations: PayoutCalculation[];
  workflow: {
    createdAt: string;
    createdByName: string | null;
    submittedAt: string | null;
    submittedByName: string | null;
    approvedAt: string | null;
    approvedByName: string | null;
    approvalNote: string | null;
    processingAt: string | null;
    processingByName: string | null;
    bankAccountLast4: string | null;
    paidDate: string | null;
    paymentReference: string | null;
    paidAt: string | null;
    paidByName: string | null;
    closedAt: string | null;
    closedByName: string | null;
    statusReason: string | null;
  };
  reversedSources: string[];
  blockers: string[];
  actions: PayoutActions;
  version: number;
}

export interface PayoutPreview {
  partner: { id: string; name: string };
  asOfDate: string;
  fyLabel: string;
  lines: PayoutLine[];
  receiptsTotal: Money;
  adjustmentsTotal: Money;
  commissionTotal: Money;
  tds: PayoutTds;
  netAmount: Money | null;
  canCreate: boolean;
  blockers: string[];
  openPayout: PayoutSummary | null;
}

export interface PayoutHistoryEntry {
  id: string;
  at: string;
  actorName: string | null;
  action: string;
  changes: { field: string; from: unknown; to: unknown }[];
}

export interface PayoutMeta {
  statuses: Option[];
  adjustmentStatuses: Option[];
  maxReason: number;
  maxNotes: number;
  maxReference: number;
  tdsConfigured: boolean;
  tdsMessage: string | null;
  canWrite: boolean;
  canApprove: boolean;
  canMarkPaid: boolean;
  canExport: boolean;
  canConfigureTds: boolean;
  canViewSensitive: boolean;
}

export interface PayoutDashboard {
  fyLabel: string;
  byStatus: { status: PayoutStatus; label: string; count: number; commission: Money; tds: Money; net: Money }[];
  eligibleCommission: Money;
  eligibleReceipts: number;
  carriedAdjustments: Money;
  pendingAdjustments: number;
  paidFy: Money;
  tdsFy: Money;
  partnersWithBalance: number;
  tdsConfigured: boolean;
}

/** One partner's payout position (gross of TDS). */
export interface PartnerPayoutBalance {
  partnerId: string;
  partnerName: string;
  partnerCode: string | null;
  panOnFile: boolean;
  panVerified: boolean;
  bankOnFile: boolean;
  eligibleReceipts: number;
  eligibleCommission: Money;
  carriedAdjustments: Money;
  pendingAdjustments: number;
  pendingAdjustmentAmount: Money;
  inProgressPayouts: number;
  inProgress: Money;
  paidFy: Money;
  tdsFy: Money;
  paidTotal: Money;
  lastPaidDate: string | null;
}

export interface PartnerAdjustment {
  id: string;
  adjustmentNumber: string;
  partner: { id: string; name: string };
  kind: AdjustmentKind;
  kindLabel: string;
  amount: Money;
  reason: string;
  status: AdjustmentStatus;
  statusLabel: string;
  sourcePaymentId: string | null;
  sourcePaymentNumber: string | null;
  createdAt: string;
  createdByName: string | null;
  decidedAt: string | null;
  decidedByName: string | null;
  decisionNote: string | null;
  allocatedTo: { id: string; payoutNumber: string | null; status: PayoutStatus } | null;
  canDecide: boolean;
}

export interface PayoutFilters {
  q?: string;
  status?: PayoutStatus | "";
  partnerId?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface AdjustmentFilters {
  q?: string;
  partnerId?: string;
  status?: AdjustmentStatus | "";
  kind?: AdjustmentKind | "";
  page?: number;
  size?: number;
}

export interface TdsConfigurationVersion {
  id: string;
  status: "DRAFT" | "CONFIRMED";
  sectionCode: string | null;
  description: string | null;
  ratePercent: Money | null;
  rateWithoutPanPercent: Money | null;
  fyThreshold: Money | null;
  effectiveFrom: string | null;
  editedByName: string | null;
  updatedAt: string | null;
  confirmedAt: string | null;
  confirmedByName: string | null;
  confirmationNote: string | null;
  complete: boolean;
  version: number;
}

export interface TdsConfiguration {
  configured: boolean;
  inForce: TdsConfigurationVersion | null;
  draft: TdsConfigurationVersion | null;
  history: TdsConfigurationVersion[];
  canEdit: boolean;
  canConfirm: boolean;
}

export interface TdsConfigurationInput {
  sectionCode: string;
  description: string;
  ratePercent: string;
  rateWithoutPanPercent: string;
  fyThreshold: string;
  effectiveFrom: string;
  version?: number;
}
