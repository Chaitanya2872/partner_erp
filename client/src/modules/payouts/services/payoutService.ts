import { apiDownload, apiGet, apiPage, apiSend } from "@/services/api";
import type {
  AdjustmentFilters,
  PartnerAdjustment,
  PartnerPayoutBalance,
  Payout,
  PayoutDashboard,
  PayoutFilters,
  PayoutHistoryEntry,
  PayoutMeta,
  PayoutPreview,
  PayoutSummary,
  TdsConfiguration,
  TdsConfigurationInput,
} from "../types/payout.types";

/** Empty filter values are dropped so the URL only carries active filters. */
const params = (filters: object) =>
  Object.fromEntries(Object.entries(filters).filter(([, v]) => v !== "" && v !== undefined && v !== null));

/** Optional decimal fields are sent as null when empty, never as "". */
const decimalOrNull = (value: string) => (value.trim() === "" ? null : value.trim());

/** Transitions carry the version the user saw; the server refuses a stale one. */
type Action = "recalculate" | "submit" | "approve" | "reject" | "cancel" | "processing" | "fail";

export const payoutService = {
  list: (filters: PayoutFilters, signal?: AbortSignal) => apiPage<PayoutSummary>("/payouts", params(filters), signal),
  dashboard: (signal?: AbortSignal) => apiGet<PayoutDashboard>("/payouts/dashboard", undefined, signal),
  meta: (signal?: AbortSignal) => apiGet<PayoutMeta>("/payouts/meta", undefined, signal),
  get: (id: string, signal?: AbortSignal) => apiGet<Payout>(`/payouts/${id}`, undefined, signal),
  history: (id: string, signal?: AbortSignal) => apiGet<PayoutHistoryEntry[]>(`/payouts/${id}/history`, undefined, signal),
  balances: (q: string, signal?: AbortSignal) => apiGet<PartnerPayoutBalance[]>("/payouts/partners", params({ q }), signal),
  balance: (partnerId: string, signal?: AbortSignal) => apiGet<PartnerPayoutBalance>(`/payouts/partners/${partnerId}`, undefined, signal),
  preview: (partnerId: string, asOfDate?: string) => apiSend<PayoutPreview>("post", "/payouts/preview", { partnerId, asOfDate: asOfDate || null }),
  /** Payouts write. 201 for a new payout; 200 with the same payout when the idempotency key was already used. */
  create: (input: { partnerId: string; asOfDate?: string; notes?: string; idempotencyKey: string }) =>
    apiSend<Payout>("post", "/payouts", { ...input, asOfDate: input.asOfDate || null, notes: input.notes?.trim() || null }),
  action: (id: string, action: Action, version: number, extra: { reason?: string; note?: string } = {}) =>
    apiSend<Payout>("post", `/payouts/${id}/${action}`, { version, ...extra }),
  adjustTds: (id: string, version: number, tdsAmount: string, reason: string) =>
    apiSend<Payout>("post", `/payouts/${id}/tds-adjustment`, { version, tdsAmount, reason }),
  markPaid: (id: string, version: number, input: { paidDate: string; reference: string; acknowledgeReversedSources?: boolean }) =>
    apiSend<Payout>("post", `/payouts/${id}/mark-paid`, { version, ...input }),
  exportCsv: (filters: Omit<PayoutFilters, "page" | "size" | "sort">) => apiDownload("/payouts/export", "iotiq-payouts.csv", params(filters)),

  adjustments: (filters: AdjustmentFilters, signal?: AbortSignal) => apiPage<PartnerAdjustment>("/payouts/adjustments", params(filters), signal),
  createAdjustment: (input: { partnerId: string; amount: string; reason: string; idempotencyKey: string }) =>
    apiSend<PartnerAdjustment>("post", "/payouts/adjustments", input),
  decideAdjustment: (id: string, decision: "approve" | "reject", note?: string) =>
    apiSend<PartnerAdjustment>("post", `/payouts/adjustments/${id}/${decision}`, { note: note?.trim() || null }),

  tdsConfiguration: (signal?: AbortSignal) => apiGet<TdsConfiguration>("/settings/tds", undefined, signal),
  saveTdsDraft: (input: TdsConfigurationInput) =>
    apiSend<TdsConfiguration>("put", "/settings/tds", {
      sectionCode: input.sectionCode.trim() || null,
      description: input.description.trim() || null,
      ratePercent: decimalOrNull(input.ratePercent),
      rateWithoutPanPercent: decimalOrNull(input.rateWithoutPanPercent),
      fyThreshold: decimalOrNull(input.fyThreshold),
      effectiveFrom: input.effectiveFrom || null,
      version: input.version ?? null,
    }),
  confirmTds: (version: number, note: string) => apiSend<TdsConfiguration>("post", "/settings/tds/confirm", { version, note }),
};
