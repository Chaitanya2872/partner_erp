import { formatCurrency, formatNumber, formatPercent, NOT_AVAILABLE } from "@/utils/format";

/** Chart helpers shared by the Phase 12 charts. */
export type Unit = "INR" | "COUNT" | string;

/** Plain values for the chart; the server sends decimals as strings. */
export const num = (value: unknown): number => (value === null || value === undefined || value === "" ? 0 : Number(value));

export function formatValue(value: unknown, unit: Unit): string {
  if (value === null || value === undefined) return NOT_AVAILABLE;
  if (unit === "INR") return formatCurrency(value as string | number);
  if (unit === "PERCENT") return formatPercent(value as string | number);
  return formatNumber(value as string | number);
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

/** Bucket label from an ISO date, without time-zone conversion: "27 Oct" for days, "Oct 2026" for months. */
export function bucketLabel(iso: string, granularity: "DAY" | "MONTH"): string {
  const [y, m, d] = iso.split("-").map(Number);
  return granularity === "MONTH" ? `${MONTHS[m - 1]} ${y}` : `${d} ${MONTHS[m - 1]}`;
}

export interface SliceData {
  key: string;
  label: string;
  count: number;
  amount: number | string | null;
}

export const hasAmounts = (slices: SliceData[]) => slices.some((s) => s.amount !== null && s.amount !== undefined);

/** Up to five categories in token order; any more fold into "Other" so no colour is generated or reused. */
export function foldSlices(slices: SliceData[], max = 5): SliceData[] {
  if (slices.length <= max) return slices;
  const head = slices.slice(0, max - 1);
  const rest = slices.slice(max - 1);
  const amounts = hasAmounts(rest);
  return [
    ...head,
    {
      key: "OTHER",
      label: "Other",
      count: rest.reduce((n, s) => n + s.count, 0),
      amount: amounts ? rest.reduce((n, s) => n + num(s.amount), 0) : null,
    },
  ];
}
