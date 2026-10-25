/** Wire types for /api/v1/partners (server: modules/partners/dto). Money arrives as JSON numbers or decimal strings. */

export type PartnerStatus = "PROSPECT" | "ONBOARDING" | "ACTIVE" | "INACTIVE";
export type AgreementStatus = "NOT_SENT" | "SENT" | "SIGNED";
export type PartnerCategory =
  | "INTERIOR_DESIGNER"
  | "ARCHITECT"
  | "BUILDER"
  | "DEVELOPER"
  | "CONTRACTOR"
  | "ELECTRICAL_DEALER"
  | "KITCHEN_FURNITURE_SHOWROOM"
  | "SOCIETY_REAL_ESTATE_APP"
  | "OTHER";
export type TierReviewAction = "UPGRADE" | "DOWNGRADE" | "GRACE" | "RECOVERED" | "AUTO_UPGRADE";
export type Money = number | string;

export interface TierRef {
  level: number;
  name: string;
  commissionPercent: Money;
}

export interface PartnerSummary {
  id: string;
  name: string;
  category: PartnerCategory;
  categoryLabel: string;
  city: string | null;
  contactName: string | null;
  phone: string | null;
  status: PartnerStatus;
  statusLabel: string;
  tier: TierRef;
  calculatedTier: TierRef;
  revenue12m: Money;
  agreementStatus: AgreementStatus;
  agreementStatusLabel: string;
  leadCount: number;
  strategicProgramme: boolean;
  downgradeGrace: boolean;
}

export interface Partner {
  id: string;
  name: string;
  category: PartnerCategory;
  categoryLabel: string;
  status: PartnerStatus;
  statusLabel: string;
  contactName: string | null;
  phone: string | null;
  email: string | null;
  city: string | null;
  tier: TierRef;
  calculatedTier: TierRef;
  revenue12m: Money;
  strategicProgramme: boolean;
  downgradeGrace: boolean;
  lastTierReviewOn: string | null;
  agreementStatus: AgreementStatus;
  agreementStatusLabel: string;
  agreementVersion: string | null;
  agreementDate: string | null;
  /** Full value only when `sensitiveVisible`; otherwise masked to the last 4 characters. */
  pan: string | null;
  panOnFile: boolean;
  /** Full value only when `sensitiveVisible`; otherwise its last 3 characters (it embeds the PAN). */
  gstin: string | null;
  gstinOnFile: boolean;
  licenceNo: string | null;
  bankName: string | null;
  /** Full value only when `sensitiveVisible`; otherwise masked to the last 4 digits. */
  bankAccount: string | null;
  bankAccountOnFile: boolean;
  ifsc: string | null;
  notes: string | null;
  sensitiveVisible: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface PartnerPerformance {
  tier: TierRef;
  calculatedTier: TierRef;
  revenue12m: Money;
  revenueSince: string;
  nextTier: TierRef | null;
  nextTierMinimum: Money | null;
  progressPercent: number | null;
  remainingToNextTier: Money | null;
  feesUnpaid: Money;
  feesPaid: Money;
  leadCount: number;
  agreementStatus: AgreementStatus;
  payoutReady: boolean;
}

export interface PartnerMeta {
  categories: { code: PartnerCategory; label: string }[];
  statuses: { code: PartnerStatus; label: string }[];
  agreementStatuses: { code: AgreementStatus; label: string }[];
  tiers: { level: number; name: string; commissionPercent: Money; minimumRevenue: Money; payoutBusinessDays: number }[];
  defaultAgreementVersion: string;
}

/**
 * Create/replace body. `pan`/`gstin`/`bankAccount`: null keeps the stored value, "" removes it. `tierLevel` and
 * `strategicProgramme` need Partners approve; send null to leave them unchanged.
 */
export interface PartnerInput {
  name: string;
  category: PartnerCategory | "";
  status: PartnerStatus;
  contactName: string;
  phone: string;
  email: string;
  city: string;
  tierLevel: number | null;
  strategicProgramme: boolean | null;
  agreementStatus: AgreementStatus;
  agreementVersion: string;
  agreementDate: string | null;
  pan: string | null;
  gstin: string | null;
  licenceNo: string;
  bankName: string;
  bankAccount: string | null;
  ifsc: string;
  notes: string;
  version?: number;
}

export interface PartnerFilters {
  q?: string;
  status?: PartnerStatus | "";
  category?: PartnerCategory | "";
  tier?: string;
  agreementStatus?: AgreementStatus | "";
  page: number;
  size: number;
  sort?: string;
}

export interface TierReviewItem {
  partnerId: string;
  partnerName: string;
  status: PartnerStatus;
  action: TierReviewAction | null;
  actionLabel: string;
  fromLevel: number;
  fromTierName: string;
  toLevel: number;
  toTierName: string;
  targetLevel: number;
  revenue12m: Money;
  programme: boolean;
  graceBefore: boolean;
  graceAfter: boolean;
}

export interface TierReviewPreview {
  reviewDate: string;
  reviewed: number;
  changes: TierReviewItem[];
}

export interface TierReviewHistoryEntry {
  id: string;
  action: TierReviewAction;
  actionLabel: string;
  fromLevel: number;
  toLevel: number;
  revenue12m: Money;
  programme: boolean;
  actorId: string | null;
  actorName: string | null;
  reviewedAt: string;
}
