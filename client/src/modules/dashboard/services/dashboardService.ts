import { apiGet } from "@/services/api";
import type { Alerts, Finance, Overview, Partners, PeriodFilter, Products, Sales } from "../types/dashboard.types";

/** Custom dates only travel with the CUSTOM preset; the server resolves presets in Asia/Kolkata. */
export const periodParams = (period: PeriodFilter) =>
  period.preset === "CUSTOM" ? { preset: period.preset, from: period.from, to: period.to } : { preset: period.preset };

export const dashboardService = {
  overview: (period: PeriodFilter, signal?: AbortSignal) => apiGet<Overview>("/dashboard/overview", periodParams(period), signal),
  sales: (period: PeriodFilter, signal?: AbortSignal) => apiGet<Sales>("/dashboard/sales", periodParams(period), signal),
  partners: (period: PeriodFilter, signal?: AbortSignal) => apiGet<Partners>("/dashboard/partners", periodParams(period), signal),
  products: (period: PeriodFilter, signal?: AbortSignal) => apiGet<Products>("/dashboard/products", periodParams(period), signal),
  finance: (period: PeriodFilter, signal?: AbortSignal) => apiGet<Finance>("/dashboard/finance", periodParams(period), signal),
  /** Current operational alerts (not period-bound). */
  alerts: (signal?: AbortSignal) => apiGet<Alerts>("/dashboard/alerts", undefined, signal),
};
