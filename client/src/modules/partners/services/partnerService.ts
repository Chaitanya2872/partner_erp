import { apiGet, apiPage, apiSend } from "@/services/api";
import type {
  Partner,
  PartnerFilters,
  PartnerInput,
  PartnerMeta,
  PartnerPerformance,
  PartnerStatus,
  PartnerSummary,
  TierReviewAction,
  TierReviewHistoryEntry,
  TierReviewItem,
  TierReviewPreview,
} from "../types/partner.types";

/** Empty filter values are dropped so the URL only carries active filters. */
const params = (filters: PartnerFilters) =>
  Object.fromEntries(Object.entries(filters).filter(([, v]) => v !== "" && v !== undefined && v !== null));

export const partnerService = {
  list: (filters: PartnerFilters, signal?: AbortSignal) => apiPage<PartnerSummary>("/partners", params(filters), signal),
  meta: (signal?: AbortSignal) => apiGet<PartnerMeta>("/partners/meta", undefined, signal),
  get: (id: string, signal?: AbortSignal) => apiGet<Partner>(`/partners/${id}`, undefined, signal),
  performance: (id: string, signal?: AbortSignal) => apiGet<PartnerPerformance>(`/partners/${id}/performance`, undefined, signal),
  tierHistory: (id: string, signal?: AbortSignal) => apiGet<TierReviewHistoryEntry[]>(`/partners/${id}/tier-reviews`, undefined, signal),
  create: (input: PartnerInput) => apiSend<Partner>("post", "/partners", input),
  update: (id: string, input: PartnerInput) => apiSend<Partner>("put", `/partners/${id}`, input),
  setStatus: (id: string, status: PartnerStatus, version: number) => apiSend<Partner>("patch", `/partners/${id}/status`, { status, version }),
  previewTierReview: (partnerIds?: string[]) => apiSend<TierReviewPreview>("post", "/partners/tier-review/preview", { partnerIds: partnerIds ?? null }),
  applyTierReview: (items: { partnerId: string; action: TierReviewAction; toLevel: number }[]) =>
    apiSend<{ reviewDate: string; applied: TierReviewItem[] }>("post", "/partners/tier-review/apply", { items }),
};
