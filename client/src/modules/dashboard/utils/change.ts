import type { Money } from "../types/dashboard.types";

const n = (value: Money | null | undefined) => (value === null || value === undefined ? null : Number(value));

/** Percentage change against the previous period; null when the previous value is zero (no meaningful ratio). */
export function percentChange(current: Money | null | undefined, previous: Money | null | undefined): number | null {
  const c = n(current);
  const p = n(previous);
  if (c === null || p === null || p === 0) return null;
  return ((c - p) / Math.abs(p)) * 100;
}
