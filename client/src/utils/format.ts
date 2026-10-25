/**
 * Display formatting for amounts, percentages, dates and identifiers (Indian conventions, en-IN). Formatting
 * only: values are never rounded for storage or used in calculations here. The backend is the calculation
 * authority. Decimal strings from the API are passed to Intl unchanged, so no binary floating-point conversion
 * happens on the way to the screen.
 */

/** Placeholder for null, undefined or unparseable values. */
export const NOT_AVAILABLE = "—";

/** A decimal amount as sent by the API (JSON number or decimal string). */
export type DecimalInput = number | string | null | undefined;

const inr = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", minimumFractionDigits: 2, maximumFractionDigits: 2 });
const inrWhole = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });
const inrCompact = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", notation: "compact", maximumFractionDigits: 2 });
const grouped = new Intl.NumberFormat("en-IN", { maximumFractionDigits: 2 });
const percent = new Intl.NumberFormat("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const DECIMAL = /^-?\d+(\.\d+)?$/;

/** Normalises API input for Intl. Strings stay strings (exact decimal formatting); invalid input gives null. */
function toIntlValue(value: DecimalInput): number | `${number}` | null {
  if (value === null || value === undefined) return null;
  if (typeof value === "number") return Number.isFinite(value) ? value : null;
  const trimmed = value.trim();
  return DECIMAL.test(trimmed) ? (trimmed as `${number}`) : null;
}

/** Intl accepts exact decimal strings at runtime (ES2023); the cast satisfies older lib typings. */
function fmt(formatter: Intl.NumberFormat, value: number | `${number}`): string {
  return formatter.format(value as number);
}

/** ₹1,25,000.00: Indian grouping, always two decimals. */
export function formatCurrency(value: DecimalInput, options: { whole?: boolean } = {}): string {
  const v = toIntlValue(value);
  if (v === null) return NOT_AVAILABLE;
  return fmt(options.whole ? inrWhole : inr, v);
}

/** ₹12.5L or ₹1.2Cr style for space-constrained tiles; never use it in documents or tables. */
export function formatCurrencyCompact(value: DecimalInput): string {
  const v = toIntlValue(value);
  return v === null ? NOT_AVAILABLE : fmt(inrCompact, v);
}

/** 12,50,000 (Indian grouping, up to two decimals). */
export function formatNumber(value: DecimalInput): string {
  const v = toIntlValue(value);
  return v === null ? NOT_AVAILABLE : fmt(grouped, v);
}

/** 18.00%. The input is already a percentage (18 means 18%), as the API sends GST and fee rates. */
export function formatPercent(value: DecimalInput): string {
  const v = toIntlValue(value);
  return v === null ? NOT_AVAILABLE : `${fmt(percent, v)}%`;
}

/** Calendar dates from the API are ISO `yyyy-mm-dd`; they are shown as 25 Oct 2026 without timezone shifts. */
export function formatDate(value: string | null | undefined): string {
  if (!value) return NOT_AVAILABLE;
  const dateOnly = /^\d{4}-\d{2}-\d{2}$/.test(value);
  const date = dateOnly ? new Date(`${value}T00:00:00`) : new Date(value);
  if (Number.isNaN(date.getTime())) return NOT_AVAILABLE;
  return date.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
}

/** 25 Oct 2026, 2:30 pm in the viewer's timezone (timestamps are stored in UTC). */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return NOT_AVAILABLE;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return NOT_AVAILABLE;
  return date.toLocaleString("en-IN", { day: "2-digit", month: "short", year: "numeric", hour: "numeric", minute: "2-digit" });
}

/** "3 minutes ago" style, falling back to the date after a week. */
export function formatRelative(value: string | null | undefined, now: Date = new Date()): string {
  if (!value) return NOT_AVAILABLE;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return NOT_AVAILABLE;
  const seconds = Math.round((date.getTime() - now.getTime()) / 1000);
  const abs = Math.abs(seconds);
  const rtf = new Intl.RelativeTimeFormat("en-IN", { numeric: "auto" });
  if (abs < 60) return rtf.format(seconds, "second");
  if (abs < 3600) return rtf.format(Math.round(seconds / 60), "minute");
  if (abs < 86400) return rtf.format(Math.round(seconds / 3600), "hour");
  if (abs < 7 * 86400) return rtf.format(Math.round(seconds / 86400), "day");
  return formatDate(value);
}

/**
 * Financial document numbers (estimates, invoices, payments) are shown exactly as issued by the server,
 * trimmed, never re-generated on the client.
 */
export function formatDocumentNumber(value: string | null | undefined): string {
  const trimmed = value?.trim();
  return trimmed ? trimmed : NOT_AVAILABLE;
}

/** Short form of a UUID for tables (full value in a tooltip or detail view). */
export function shortId(value: string | null | undefined): string {
  return value ? value.slice(0, 8) : NOT_AVAILABLE;
}

/** "PASSWORD_CHANGED" → "Password changed". */
export function humanizeCode(code: string): string {
  if (!code) return NOT_AVAILABLE;
  const words = code.toLowerCase().replaceAll("_", " ");
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** Initials from a display name ("Asha Rao" gives "AR"). */
export function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  const first = parts[0][0] ?? "";
  const last = parts.length > 1 ? (parts[parts.length - 1][0] ?? "") : "";
  return (first + last).toUpperCase();
}
