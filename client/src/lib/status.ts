import type { BadgeTone } from "@/components/ui/Badge";

/**
 * Presentation of lifecycle statuses. Codes are exactly the values the backend stores (see the CHECK
 * constraints in db/migration) or derives (BUSINESS_RULES.md); this file adds labels and tones only and must not
 * invent new states. `derived: true` marks statuses computed from other fields rather than stored.
 */
export interface StatusDefinition {
  label: string;
  tone: BadgeTone;
  derived?: boolean;
  /** Short explanation for tooltips and screen readers. */
  hint?: string;
}

type Registry = Record<string, StatusDefinition>;

const user: Registry = {
  ACTIVE: { label: "Active", tone: "success" },
  DISABLED: { label: "Disabled", tone: "danger" },
  LOCKED: { label: "Locked", tone: "warning", hint: "Locked after repeated failed sign-ins; unlocks automatically." },
};

/** partners.status (V4). */
const partner: Registry = {
  PROSPECT: { label: "Prospect", tone: "neutral" },
  ONBOARDING: { label: "Onboarding", tone: "info" },
  ACTIVE: { label: "Active", tone: "success" },
  INACTIVE: { label: "Inactive", tone: "outline" },
};

/** partners.agreement_status (V4). Active partners without a signed agreement raise an alert (BR-P6). */
const agreement: Registry = {
  NOT_SENT: { label: "Not sent", tone: "neutral" },
  SENT: { label: "Sent", tone: "info" },
  SIGNED: { label: "Signed", tone: "success" },
};

/** leads.stage (V5). */
const lead: Registry = {
  NEW: { label: "New", tone: "neutral" },
  REGISTERED: { label: "Registered", tone: "info" },
  PRESALES: { label: "Presales", tone: "info" },
  SITE_VISIT: { label: "Site visit", tone: "info" },
  ESTIMATE_SENT: { label: "Estimate sent", tone: "accent" },
  NEGOTIATION: { label: "Negotiation", tone: "warning" },
  WON: { label: "Won", tone: "success" },
  LOST: { label: "Lost", tone: "danger" },
};

/** estimates.status (V8), plus Expired derived from status SENT and validity before today (BR-E7). */
const estimate: Registry = {
  DRAFT: { label: "Draft", tone: "neutral" },
  SENT: { label: "Sent", tone: "info" },
  ACCEPTED: { label: "Accepted", tone: "success" },
  REJECTED: { label: "Rejected", tone: "danger" },
  INVOICED: { label: "Invoiced", tone: "accent" },
  EXPIRED: { label: "Expired", tone: "warning", derived: true, hint: "Sent, and the validity date has passed." },
};

/** invoices.status (V16: DRAFT/ISSUED/CANCELLED). */
const invoiceStatus: Registry = {
  DRAFT: { label: "Draft", tone: "neutral", hint: "Not yet issued: no invoice number, and not a tax invoice." },
  ISSUED: { label: "Issued", tone: "accent" },
  CANCELLED: { label: "Cancelled", tone: "outline" },
};

/** The payment status of an issued invoice, derived from recorded payments and the due date (BR-V6). */
const invoice: Registry = {
  UNPAID: { label: "Unpaid", tone: "info", derived: true },
  PARTLY_PAID: { label: "Partially paid", tone: "warning", derived: true },
  PARTLY_PAID_OVERDUE: { label: "Partially paid · overdue", tone: "danger", derived: true },
  PAID: { label: "Paid", tone: "success", derived: true },
  OVERDUE: { label: "Overdue", tone: "danger", derived: true },
  CANCELLED: { label: "Cancelled", tone: "outline" },
};

/** Payouts store ACCRUED/PAID; Due and Clawback are derived display statuses (BR-O2). */
const payout: Registry = {
  ACCRUED: { label: "Accrued", tone: "neutral" },
  DUE: { label: "Due", tone: "warning", derived: true, hint: "The payout due date has been reached." },
  PAID: { label: "Paid", tone: "success" },
  CLAWBACK: { label: "Clawback", tone: "danger", derived: true, hint: "Negative fee from a refund." },
};

/** products.active (V6) shown as a status; inactive products stay on the sheet but leave the line pickers (D-17). */
const product: Registry = {
  ACTIVE: { label: "Active", tone: "success" },
  INACTIVE: { label: "Inactive", tone: "outline", hint: "Hidden from estimate and invoice line pickers; kept on existing documents." },
};

export const STATUS_REGISTRY = { user, partner, agreement, lead, product, estimate, invoiceStatus, invoice, payout } as const;

export type StatusDomain = keyof typeof STATUS_REGISTRY;

/** Definition for a status; unknown codes are shown neutrally with a humanised label rather than hidden. */
export function statusDefinition(domain: StatusDomain, code: string): StatusDefinition {
  const known = STATUS_REGISTRY[domain][code];
  if (known) return known;
  const label = code.charAt(0) + code.slice(1).toLowerCase().replaceAll("_", " ");
  return { label, tone: "neutral" };
}
