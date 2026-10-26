import { useSearchParams } from "react-router";
import type { PeriodFilter, PeriodPreset } from "../types/dashboard.types";

export const PERIOD_PRESETS: { value: PeriodPreset; label: string }[] = [
  { value: "TODAY", label: "Today" },
  { value: "LAST_7_DAYS", label: "Last 7 days" },
  { value: "LAST_30_DAYS", label: "Last 30 days" },
  { value: "THIS_MONTH", label: "This month" },
  { value: "LAST_MONTH", label: "Last month" },
  { value: "THIS_QUARTER", label: "This quarter" },
  { value: "THIS_FY", label: "This financial year" },
  { value: "CUSTOM", label: "Custom range" },
];

const ISO = /^\d{4}-\d{2}-\d{2}$/;

/** A custom range is sent only once both dates are set and in order; the server enforces the 1,096-day limit. */
export function isComplete(period: PeriodFilter): boolean {
  return period.preset !== "CUSTOM" || (ISO.test(period.from) && ISO.test(period.to) && period.from <= period.to);
}

/** The period lives in the URL (`?preset=…&from=…&to=…`) so a filtered view can be bookmarked or shared. */
export function usePeriod(defaultPreset: PeriodPreset = "THIS_FY") {
  const [params, setParams] = useSearchParams();
  const raw = params.get("preset");
  const preset = PERIOD_PRESETS.some((p) => p.value === raw) ? (raw as PeriodPreset) : defaultPreset;
  const period: PeriodFilter = { preset, from: params.get("from") ?? "", to: params.get("to") ?? "" };
  const setPeriod = (next: PeriodFilter) =>
    setParams(
      (current) => {
        const out = new URLSearchParams(current);
        out.set("preset", next.preset);
        for (const key of ["from", "to"] as const) {
          if (next.preset === "CUSTOM" && next[key]) out.set(key, next[key]);
          else out.delete(key);
        }
        return out;
      },
      { replace: true },
    );
  return { period, setPeriod, complete: isComplete(period) };
}
