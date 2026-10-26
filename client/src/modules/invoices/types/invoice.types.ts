/**
 * Wire types for /api/v1/invoices and /api/v1/internal/invoices (server: modules/invoices/dto). Money arrives as JSON
 * numbers or decimal strings; every amount, including each line's tax, is computed by the server.
 *
 * The partner copy (Invoice, InvoiceSummary, InvoicePreview without `internal`) never carries B2C names, B2C prices,
 * the twin number or the uplift. Those live only in InternalInvoiceDocument (INTERNAL_B2C read) and in the
 * preview's `internal` block (PRODUCTS:VIEW_INTERNAL_B2C), which the server omits for everyone else.
 */

import type { Money } from "@/modules/partners/types/partner.types";
import type { PartnerRef } from "@/modules/leads/types/lead.types";
import type { BillTo, EstimateLeadRef, Totals } from "@/modules/estimates/types/estimate.types";

export type InvoiceStatus = "DRAFT" | "ISSUED" | "CANCELLED";
/** Derived from recorded payments and the due date (BR-V6); null for drafts. */
export type PaymentStatus = "UNPAID" | "PARTLY_PAID" | "PARTLY_PAID_OVERDUE" | "OVERDUE" | "PAID" | "CANCELLED";
/** List filter values (the two partly-paid states share one filter). */
export type PaymentFilter = "UNPAID" | "PARTIALLY_PAID" | "PAID" | "OVERDUE";
export type TaxType = "INTRA" | "INTER";
export type PlaceOfSupplyBasis = "RECIPIENT_LOCATION" | "DELIVERY_LOCATION" | "INSTALLATION_SITE" | "OTHER";

export interface InvoiceTotals {
  subtotal: Money;
  discountAmount: Money;
  taxableAmount: Money;
  cgstAmount: Money;
  sgstAmount: Money;
  igstAmount: Money;
  gstAmount: Money;
  grandTotal: Money;
}

export interface InvoiceParty {
  name: string | null;
  address: string | null;
  stateCode: string | null;
  stateName: string | null;
  gstin: string | null;
}

export interface TaxReason {
  code: string;
  message: string;
}

export interface TaxTreatment {
  /** Null while the place of supply or the supplier's state is missing: nothing is guessed. */
  taxType: TaxType | null;
  taxTypeLabel: string | null;
  utgst: boolean;
  /** "SGST" or "UTGST" for intra-state supplies; null otherwise. */
  stateTaxLabel: string | null;
  missing: string[];
  reasons: TaxReason[];
  confirmationRequired: boolean;
  confirmed: boolean;
  confirmedAt: string | null;
  confirmedByName: string | null;
  confirmationNote: string | null;
}

export interface EstimateComparison {
  estimateId: string;
  estimateNumber: string;
  estimateIgst: boolean;
  estimateTaxable: Money;
  estimateGst: Money;
  estimateGrandTotal: Money;
  taxableDifference: Money;
  gstDifference: Money;
  grandTotalDifference: Money;
  taxTypeChanged: boolean;
}

export interface InvoiceLine {
  id: string | null;
  lineNo: number;
  productId: string | null;
  sku: string | null;
  productActive: boolean;
  description: string;
  hsn: string | null;
  unit: string | null;
  quantity: Money;
  unitPrice: Money;
  gstPercent: Money;
  amount: Money;
  taxableValue: Money;
  cgstAmount: Money;
  sgstAmount: Money;
  igstAmount: Money;
  taxAmount: Money;
  lineTotal: Money;
}

export interface Invoice {
  id: string;
  /** Null until issued (INV-YYYY-NNNNNN). */
  invoiceNumber: string | null;
  status: InvoiceStatus;
  statusLabel: string;
  invoiceDate: string | null;
  dueDate: string | null;
  dueDays: number;
  paymentStatus: PaymentStatus | null;
  paymentStatusLabel: string | null;
  paidAmount: Money | null;
  balance: Money | null;
  lead: EstimateLeadRef | null;
  partner: PartnerRef;
  estimate: { id: string; estimateNumber: string } | null;
  billTo: BillTo;
  billToLabel: string;
  discountPercent: Money;
  feePercent: Money;
  notes: string | null;
  supplier: InvoiceParty;
  billing: InvoiceParty;
  placeOfSupplyCode: string | null;
  placeOfSupplyName: string | null;
  placeOfSupplyBasis: PlaceOfSupplyBasis | null;
  placeOfSupplyBasisLabel: string | null;
  tax: TaxTreatment;
  lines: InvoiceLine[];
  totals: InvoiceTotals;
  comparison: EstimateComparison | null;
  issuedAt: string | null;
  issuedByName: string | null;
  cancelledAt: string | null;
  cancelledByName: string | null;
  cancelReason: string | null;
  createdAt: string;
  createdByName: string | null;
  updatedAt: string;
  version: number;
}

export interface InvoiceSummary {
  id: string;
  invoiceNumber: string | null;
  status: InvoiceStatus;
  statusLabel: string;
  invoiceDate: string | null;
  dueDate: string | null;
  paymentStatus: PaymentStatus | null;
  paymentStatusLabel: string | null;
  leadId: string | null;
  leadNumber: string | null;
  clientName: string | null;
  billingName: string | null;
  partner: PartnerRef;
  estimateNumber: string | null;
  taxType: TaxType | null;
  taxableAmount: Money;
  grandTotal: Money;
  paidAmount: Money | null;
  balance: Money | null;
  createdAt: string;
  version: number;
}

export interface InvoiceOption {
  value: string;
  label: string;
}

export interface GstStateOption {
  code: string;
  name: string;
  utgst: boolean;
}

export interface InvoiceMeta {
  statuses: InvoiceOption[];
  paymentStatuses: InvoiceOption[];
  billTo: InvoiceOption[];
  states: GstStateOption[];
  placeOfSupplyBases: InvoiceOption[];
  dueDays: number;
  terms: string;
  defaultGstPercent: Money;
  maxLines: number;
  canOverrideFee: boolean;
  /** INVOICES:APPROVE: confirm tax treatment, change a line's GST %. */
  canApprove: boolean;
  canCancel: boolean;
  supplierComplete: boolean;
  supplierStateCode: string | null;
}

export interface InvoiceLineInput {
  id?: string;
  productId: string;
  quantity: string;
  unitPrice: string;
  gstPercent: string;
}

/**
 * Create/update body. On a draft converted from an estimate, lines and the commercial fields (lead, bill to,
 * discount, fee) are locked: they are sent as null and the server keeps the estimate's values.
 */
export interface InvoiceInput {
  leadId: string | null;
  billTo: BillTo | null;
  discountPercent: string | null;
  feePercent: string | null;
  feeOverrideReason: string | null;
  notes: string;
  dueDays: number | null;
  billingName: string | null;
  billingAddress: string | null;
  billingStateCode: string | null;
  billingGstin: string | null;
  placeOfSupplyCode: string | null;
  placeOfSupplyBasis: PlaceOfSupplyBasis | null;
  lines: InvoiceLineInput[] | null;
  version?: number;
}

export interface InvoicePreview {
  lines: InvoiceLine[];
  totals: InvoiceTotals;
  tax: TaxTreatment;
  comparison: EstimateComparison | null;
  defaultFeePercent: Money;
  feePercent: Money;
  feeOverride: boolean;
  /** Present only for PRODUCTS:VIEW_INTERNAL_B2C. */
  internal?: {
    lines: { lineNo: number; productId: string | null; b2cName: string | null; b2cRate: Money; b2cAmount: Money; b2cPriceMissing: boolean }[];
    b2cTaxable: Money;
    b2cGrandTotal: Money;
    uplift: Money;
    missingB2cPrices: number;
  } | null;
}

export interface InternalInvoiceLine {
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

export interface InternalInvoiceDocument {
  invoiceId: string;
  internalNumber: string;
  parentNumber: string;
  documentDate: string;
  status: InvoiceStatus;
  statusLabel: string;
  discountPercent: Money;
  igst: boolean;
  lines: InternalInvoiceLine[];
  b2cTotals: Totals;
  partnerTotals: Totals;
  uplift: Money;
  b2cIncomplete: boolean;
  missingB2cPrices: number;
  updatedAt: string;
}

export interface InvoiceHistoryEntry {
  id: string;
  at: string;
  actorName: string | null;
  action: string;
  changes: { field: string; from: unknown; to: unknown }[];
}

export interface InvoiceFilters {
  q?: string;
  status?: InvoiceStatus | "";
  payment?: PaymentFilter | "";
  partnerId?: string;
  leadId?: string;
  estimateId?: string;
  from?: string;
  to?: string;
  page: number;
  size: number;
  sort?: string;
}

/** A lead from the restricted picker (GET /invoices/lead-options): minimum fields only. */
export interface InvoiceLeadOption {
  id: string;
  leadNumber: string;
  clientName: string;
  partner: PartnerRef;
}

export interface InvoiceActionInput {
  version: number;
  expectedGrandTotal?: Money;
  note?: string;
  reason?: string;
}

/** GET/PUT /settings/gst-registration: the supplier details printed on every invoice. */
export interface GstRegistration {
  legalName: string | null;
  address: string | null;
  stateCode: string | null;
  stateName: string | null;
  gstin: string | null;
  complete: boolean;
  version: number;
  states: GstStateOption[];
}

export interface GstRegistrationInput {
  legalName: string;
  address: string;
  stateCode: string;
  gstin: string;
  version: number;
}
