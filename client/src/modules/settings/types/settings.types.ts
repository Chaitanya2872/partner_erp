/** Decimal values arrive as JSON numbers or strings. */
export type Money = number | string;

/** Company profile, document defaults, SLA targets and commercial settings (GET /settings/company). */
export interface CompanySettings {
  /** The GST registration (legal name, address, state, GSTIN) is changed through the GST registration card. */
  legalName: string | null;
  address: string | null;
  stateCode: string | null;
  stateName: string | null;
  gstin: string | null;
  phone: string | null;
  email: string | null;
  terms: string | null;
  estimateValidityDays: number;
  invoiceDueDays: number;
  defaultGstPercent: Money;
  acknowledgementHours: number;
  estimateSlaDays: number;
  leadProtectionDays: number;
  /** Stored but not applied: reversals after payment are always carried forward (DECISIONS L-3). */
  clawbackDays: number;
  updatedAt: string | null;
  updatedBy: string | null;
  version: number;
  /** Settings write. */
  canEdit: boolean;
  /** Settings write plus Manage commercial settings (lead protection, tiers). */
  canManageCommercial: boolean;
  limits: {
    maxTerms: number;
    maxValidityDays: number;
    maxDueDays: number;
    maxAcknowledgementHours: number;
    maxSlaDays: number;
    maxProtectionDays: number;
  };
}

export interface CompanySettingsInput {
  phone: string | null;
  email: string | null;
  terms: string | null;
  estimateValidityDays: number | null;
  invoiceDueDays: number | null;
  defaultGstPercent: number | string | null;
  acknowledgementHours: number | null;
  estimateSlaDays: number | null;
  version: number;
}

export interface PartnerTier {
  id: string;
  level: number;
  name: string;
  commissionPercent: Money;
  minimumRevenue: Money;
  payoutBusinessDays: number;
  /** Partners on the tier, excluding Inactive ones. */
  partners: number;
  updatedAt: string | null;
  updatedBy: string | null;
  version: number;
}

export interface PartnerTiers {
  tiers: PartnerTier[];
  /** Settings write plus Manage commercial settings. */
  canEdit: boolean;
}

export interface TierInput {
  level: number;
  name: string;
  commissionPercent: number | string;
  minimumRevenue: number | string;
  payoutBusinessDays: number;
  version: number;
}

export interface NumberSeries {
  prefix: string;
  document: string;
  format: string;
  example: string;
  allocatedWhen: string;
  year: number;
  lastValue: number;
  capacity: number;
}

export interface SecurityPolicy {
  password: { minLength: number; maxLength: number; rules: string[] };
  lockout: { maxFailedAttempts: number; lockoutMinutes: number; unlock: string };
  sessions: { accessTokenMinutes: number; refreshTokenDays: number; rules: string[] };
  lockedAccounts: number;
  pendingPasswordChanges: number;
}

export interface Control {
  area: string;
  control: string;
  permission: string;
  enforcedBy: string;
  reference: string;
}
