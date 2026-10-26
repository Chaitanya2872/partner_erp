/**
 * Wire types for /api/v1/estimates and /api/v1/internal/estimates (server: modules/estimates/dto). Money arrives as
 * JSON numbers or decimal strings; every total is computed by the server.
 *
 * The partner copy (Estimate, EstimateSummary) never carries B2C names, B2C prices, the twin number or the uplift.
 * Those live only in InternalEstimateDocument (INTERNAL_B2C read) and in the preview's `internal` block
 * (PRODUCTS:VIEW_INTERNAL_B2C), which the server omits for everyone else.
 */

import type { Money } from "@/modules/partners/types/partner.types";
import type { LeadStage, PartnerRef } from "@/modules/leads/types/lead.types";

export type EstimateStatus = "DRAFT" | "SENT" | "ACCEPTED" | "REJECTED" | "INVOICED";
export type BillTo = "CLIENT" | "PARTNER";

export interface Totals {
  subtotal: Money;
  discountAmount: Money;
  taxableAmount: Money;
  gstAmount: Money;
  cgstAmount: Money;
  sgstAmount: Money;
  grandTotal: Money;
}

export interface EstimateLeadRef {
  id: string;
  leadNumber: string;
  clientName: string;
  clientPhone: string | null;
  city: string | null;
  projectTypeLabel: string;
  stage: LeadStage;
  stageLabel: string;
  /** The lead was deleted after the estimate was made. */
  deleted: boolean;
}

export interface EstimateSummary {
  id: string;
  estimateNumber: string;
  estimateDate: string;
  validUntil: string;
  status: EstimateStatus;
  statusLabel: string;
  /** Derived (D-29): sent and the validity date has passed. */
  expired: boolean;
  leadId: string;
  leadNumber: string;
  clientName: string;
  partner: PartnerRef;
  billTo: BillTo;
  taxableAmount: Money;
  grandTotal: Money;
  createdAt: string;
  version: number;
}

export interface EstimateLine {
  id: string;
  lineNo: number;
  productId: string | null;
  sku: string | null;
  /** False when the product was deactivated after the line was saved. */
  productActive: boolean;
  description: string;
  hsn: string | null;
  unit: string | null;
  quantity: Money;
  unitPrice: Money;
  gstPercent: Money;
  /** Quantity × partner rate, before the document discount. */
  amount: Money;
}

export interface Estimate {
  id: string;
  estimateNumber: string;
  estimateDate: string;
  validUntil: string;
  status: EstimateStatus;
  statusLabel: string;
  expired: boolean;
  sentOn: string | null;
  lead: EstimateLeadRef;
  partner: PartnerRef;
  billTo: BillTo;
  billToLabel: string;
  discountPercent: Money;
  igst: boolean;
  feePercent: Money;
  notes: string | null;
  lines: EstimateLine[];
  totals: Totals;
  createdAt: string;
  createdByName: string | null;
  updatedAt: string;
  version: number;
}

export interface EstimateOption<T extends string = string> {
  value: T;
  label: string;
}

export interface EstimateMeta {
  statuses: EstimateOption<EstimateStatus>[];
  billTo: EstimateOption<BillTo>[];
  validityDays: number;
  /** Default terms for the notes of a new estimate. */
  terms: string;
  defaultGstPercent: Money;
  maxLines: number;
  /** D-13: the caller may set a fee other than the default (Partners approve). */
  canOverrideFee: boolean;
}

export interface EstimateLineInput {
  /** Id of a saved line: keeps its product snapshot. Omitted for new lines. */
  id?: string;
  productId: string;
  quantity: string;
  unitPrice: string;
  gstPercent: string;
}

export interface EstimateInput {
  leadId: string;
  estimateDate: string | null;
  validUntil: string | null;
  discountPercent: string | null;
  igst: boolean;
  billTo: BillTo;
  /** Null keeps the stored fee, or uses the default. */
  feePercent: string | null;
  feeOverrideReason: string | null;
  notes: string;
  lines: EstimateLineInput[];
  version?: number;
}

export interface PreviewLine {
  lineNo: number;
  id: string | null;
  productId: string;
  sku: string | null;
  description: string;
  hsn: string | null;
  unit: string | null;
  quantity: Money;
  unitPrice: Money;
  gstPercent: Money;
  amount: Money;
}

export interface InternalPreviewLine {
  lineNo: number;
  b2cName: string | null;
  b2cRate: Money | null;
  amount: Money;
  b2cPriceMissing: boolean;
}

export interface EstimatePreview {
  lines: PreviewLine[];
  totals: Totals;
  defaultFeePercent: Money;
  feePercent: Money;
  feeOverride: boolean;
  /** Present only for PRODUCTS:VIEW_INTERNAL_B2C. */
  internal?: {
    lines: InternalPreviewLine[];
    totals: Totals;
    uplift: Money;
    missingB2cPrices: number;
  };
}

export interface InternalEstimateLine {
  lineNo: number;
  b2cName: string;
  partnerName: string;
  hsn: string | null;
  unit: string | null;
  quantity: Money;
  rate: Money;
  gstPercent: Money;
  amount: Money;
  b2cPriceMissing: boolean;
}

export interface InternalEstimateDocument {
  estimateId: string;
  internalNumber: string;
  parentNumber: string;
  documentDate: string;
  status: EstimateStatus;
  statusLabel: string;
  discountPercent: Money;
  igst: boolean;
  lines: InternalEstimateLine[];
  b2cTotals: Totals;
  partnerTotals: Totals;
  /** B2C taxable − partner taxable ("Partner discount vs B2C list"). */
  uplift: Money;
  b2cIncomplete: boolean;
  missingB2cPrices: number;
  updatedAt: string;
}

export interface EstimateHistoryEntry {
  id: string;
  at: string;
  actorName: string | null;
  action: string;
  changes: { field: string; from: unknown; to: unknown }[];
}

export interface EstimateFilters {
  q?: string;
  status?: EstimateStatus | "";
  partnerId?: string;
  leadId?: string;
  expired?: string;
  page: number;
  size: number;
  sort?: string;
}

/** A lead from the restricted picker (GET /estimates/lead-options): minimum fields only. */
export interface EstimateLeadOption {
  id: string;
  leadNumber: string;
  clientName: string;
  partner: PartnerRef;
}

/** Active product for the line picker (GET /products/options). B2C fields only with VIEW_INTERNAL_B2C. */
export interface ProductOption {
  id: string;
  sku: string;
  category: string;
  categoryLabel: string;
  name: string;
  hsn: string | null;
  unit: string | null;
  rate: Money;
  gstPercent: Money;
  b2cName?: string;
  b2cRate?: Money;
}
