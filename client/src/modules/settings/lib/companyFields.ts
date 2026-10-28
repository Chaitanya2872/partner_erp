import type { ReactNode } from "react";
import type { CompanySettings as Company, CompanySettingsInput } from "../types/settings.types";

/** Field sets of the Company settings sections (rendered by CompanySection). */
export type CompanyKey = Exclude<keyof CompanySettingsInput, "version"> | "leadProtectionDays";

export interface FieldDef {
  key: CompanyKey;
  label: string;
  kind: "text" | "email" | "tel" | "textarea" | "int" | "decimal";
  hint?: string;
  required?: boolean;
  min?: number;
  max?: (c: Company) => number;
  /** How the stored value reads when not editing. */
  show?: (c: Company) => ReactNode;
}

const days = (n: number) => `${n} day${n === 1 ? "" : "s"}`;

/** Contact details printed on estimates and invoices. */
export const CONTACT_FIELDS: FieldDef[] = [
  { key: "phone", label: "Phone", kind: "tel", hint: "6 to 15 digits; spaces, +, - and brackets are allowed." },
  { key: "email", label: "Email", kind: "email" },
];

export const DOCUMENT_FIELDS: FieldDef[] = [
  { key: "estimateValidityDays", label: "Estimate validity", kind: "int", required: true, min: 1, max: (c) => c.limits.maxValidityDays, hint: "Days after the estimate date.", show: (c) => days(c.estimateValidityDays) },
  { key: "invoiceDueDays", label: "Invoice payment terms", kind: "int", required: true, min: 0, max: (c) => c.limits.maxDueDays, hint: "Days after the invoice date; fixed on the invoice when it is issued.", show: (c) => days(c.invoiceDueDays) },
  { key: "terms", label: "Default notes and terms", kind: "textarea", max: (c) => c.limits.maxTerms, hint: "Pre-filled on new estimates and invoices; each document keeps its own copy." },
];

export const GST_FIELDS: FieldDef[] = [
  { key: "defaultGstPercent", label: "Default GST rate for new products", kind: "decimal", required: true, min: 0, max: () => 100, hint: "0 to 100, at most 3 decimals. Each product and document line keeps its own rate.", show: (c) => `${Number(c.defaultGstPercent)}%` },
];

export const SLA_FIELDS: FieldDef[] = [
  { key: "acknowledgementHours", label: "Acknowledge a registration within", kind: "int", required: true, min: 1, max: (c) => c.limits.maxAcknowledgementHours, hint: "Hours after the lead is registered.", show: (c) => `${c.acknowledgementHours} hours` },
  { key: "estimateSlaDays", label: "Send an estimate within", kind: "int", required: true, min: 1, max: (c) => c.limits.maxSlaDays, hint: "Days after acknowledgement.", show: (c) => days(c.estimateSlaDays) },
];

export const PROTECTION_FIELDS: FieldDef[] = [
  { key: "leadProtectionDays", label: "Lead protection", kind: "int", required: true, min: 1, max: (c) => c.limits.maxProtectionDays, hint: "Days from acknowledgement. Leads already acknowledged keep their protection date.", show: (c) => days(c.leadProtectionDays) },
];
