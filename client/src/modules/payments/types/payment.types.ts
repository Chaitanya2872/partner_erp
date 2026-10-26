/** Wire types for /api/v1/payments (server: modules/payments/dto). Money arrives as JSON numbers or decimal strings. */
import type { Money } from "@/modules/partners/types/partner.types";
import type { InvoiceStatus, PaymentStatus } from "@/modules/invoices/types/invoice.types";

export type PaymentMethod = "UPI" | "NEFT_RTGS" | "CHEQUE" | "CASH" | "CARD";
/** Receipts are positive; a reversal mirrors a receipt with negative amounts; refunds exist only from before Phase 10. */
export type PaymentEntryType = "RECEIPT" | "REVERSAL" | "REFUND";
/** List filter: receipts still standing, or receipts that were reversed. */
export type PaymentState = "ACTIVE" | "REVERSED";

export interface PaymentRef {
  id: string;
  paymentNumber: string | null;
}

export interface PaymentSummary {
  id: string;
  paymentNumber: string | null;
  entryType: PaymentEntryType;
  entryTypeLabel: string;
  paymentDate: string;
  amount: Money;
  netAmount: Money;
  method: PaymentMethod;
  methodLabel: string;
  reference: string | null;
  invoice: { id: string; invoiceNumber: string | null };
  billingName: string | null;
  partner: { id: string; name: string };
  reversed: boolean;
  reversalOf: PaymentRef | null;
  reversalReason: string | null;
  createdAt: string;
}

export interface PaymentInvoiceBalance {
  id: string;
  invoiceNumber: string | null;
  status: InvoiceStatus;
  billingName: string | null;
  invoiceDate: string | null;
  dueDate: string | null;
  grandTotal: Money;
  taxableAmount: Money;
  paidAmount: Money;
  balance: Money;
  paymentStatus: PaymentStatus | null;
  paymentStatusLabel: string | null;
}

export interface Payment {
  id: string;
  paymentNumber: string | null;
  entryType: PaymentEntryType;
  entryTypeLabel: string;
  paymentDate: string;
  amount: Money;
  netAmount: Money;
  method: PaymentMethod;
  methodLabel: string;
  reference: string | null;
  notes: string | null;
  partner: { id: string; name: string };
  invoice: PaymentInvoiceBalance;
  /** For a receipt that was reversed: the reversal entry. */
  reversal: { id: string; paymentNumber: string | null; paymentDate: string; reason: string; createdAt: string; createdByName: string | null } | null;
  /** For a reversal: the receipt it reverses. */
  reversalOf: PaymentRef | null;
  reversalReason: string | null;
  canReverse: boolean;
  /** Why this receipt can't be reversed now (e.g. a linked partner payout). */
  reverseBlockedBy: string | null;
  createdAt: string;
  createdByName: string | null;
}

export interface PaymentInput {
  invoiceId: string;
  paymentDate: string;
  amount: string;
  method: PaymentMethod;
  reference?: string;
  notes?: string;
  /** Generated once per form, so a resubmission returns the entry already recorded. */
  idempotencyKey: string;
  /** Set after the user confirmed a possible-duplicate warning. */
  confirmDuplicate?: boolean;
}

export interface PaymentInvoiceOption {
  id: string;
  invoiceNumber: string;
  invoiceDate: string;
  dueDate: string | null;
  billingName: string | null;
  partner: { id: string; name: string };
  grandTotal: Money;
  paidAmount: Money;
  balance: Money;
  paymentStatusLabel: string | null;
}

export interface PaymentReport {
  entries: number;
  receipts: number;
  reversals: number;
  received: Money;
  reversed: Money;
  refunded: Money;
  netReceived: Money;
  netReceivedExGst: Money;
  byMethod: { method: PaymentMethod; label: string; receipts: number; netReceived: Money }[];
  receivables: { outstandingInvoices: number; outstandingAmount: Money; overdueInvoices: number; overdueAmount: Money };
}

export interface PaymentMeta {
  methods: { value: PaymentMethod; label: string }[];
  entryTypes: { value: PaymentEntryType; label: string }[];
  states: { value: PaymentState; label: string }[];
  referenceOptional: PaymentMethod[];
  maxReason: number;
  maxNotes: number;
  canWrite: boolean;
  canReverse: boolean;
  canExport: boolean;
}

export interface PaymentHistoryEntry {
  id: string;
  at: string;
  actorName: string | null;
  action: string;
  changes: { field: string; from: unknown; to: unknown }[];
}

export interface PaymentFilters {
  q: string;
  invoiceId: string;
  method: PaymentMethod | "";
  type: PaymentEntryType | "";
  state: PaymentState | "";
  from: string;
  to: string;
  page: number;
  size: number;
  sort?: string;
}
