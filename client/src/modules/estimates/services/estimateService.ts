import { apiDelete, apiDownload, apiGet, apiPage, apiSend } from "@/services/api";
import type {
  Estimate,
  EstimateFilters,
  EstimateHistoryEntry,
  EstimateLeadOption,
  EstimateInput,
  EstimateMeta,
  EstimatePreview,
  EstimateSummary,
  InternalEstimateDocument,
  ProductOption,
} from "../types/estimate.types";

/** Empty filter values are dropped so the URL only carries active filters. */
const params = (filters: Partial<EstimateFilters>) =>
  Object.fromEntries(Object.entries(filters).filter(([, v]) => v !== "" && v !== undefined && v !== null));

type ExportFilters = Omit<EstimateFilters, "page" | "size" | "sort">;

export const estimateService = {
  list: (filters: EstimateFilters, signal?: AbortSignal) => apiPage<EstimateSummary>("/estimates", params(filters), signal),
  meta: (signal?: AbortSignal) => apiGet<EstimateMeta>("/estimates/meta", undefined, signal),
  get: (id: string, signal?: AbortSignal) => apiGet<Estimate>(`/estimates/${id}`, undefined, signal),
  history: (id: string, signal?: AbortSignal) => apiGet<EstimateHistoryEntry[]>(`/estimates/${id}/history`, undefined, signal),
  /** Server totals for the editor; nothing is saved. */
  preview: (input: EstimateInput, estimateId?: string) =>
    apiSend<EstimatePreview>("post", estimateId ? `/estimates/preview?estimateId=${estimateId}` : "/estimates/preview", input),
  create: (input: EstimateInput) => apiSend<Estimate>("post", "/estimates", input),
  update: (id: string, input: EstimateInput) => apiSend<Estimate>("put", `/estimates/${id}`, input),
  send: (id: string, version: number) => apiSend<Estimate>("post", `/estimates/${id}/send`, { version }),
  accept: (id: string, version: number) => apiSend<Estimate>("post", `/estimates/${id}/accept`, { version }),
  reject: (id: string, version: number) => apiSend<Estimate>("post", `/estimates/${id}/reject`, { version }),
  remove: (id: string) => apiDelete(`/estimates/${id}`),
  exportCsv: (filters: ExportFilters) => apiDownload("/estimates/export", "iotiq-estimates.csv", params(filters)),
  /** Printable partner copy (HTML). */
  document: (id: string) => apiDownload(`/estimates/${id}/document`, "estimate.html"),
  /** Internal B2C twin: INTERNAL_B2C read only. */
  internal: (id: string, signal?: AbortSignal) => apiGet<InternalEstimateDocument>(`/internal/estimates/${id}`, undefined, signal),
  internalDocument: (id: string) => apiDownload(`/internal/estimates/${id}/document`, "internal-estimate.html"),
  /** Restricted lead picker (Estimates write): eligible leads, minimum fields, at most 50. */
  leadOptions: (params: { q?: string; leadId?: string }, signal?: AbortSignal) =>
    apiGet<EstimateLeadOption[]>("/estimates/lead-options", params, signal),
  /** Restricted active-product picker (Products read or Estimates write). */
  productOptions: (q: string, signal?: AbortSignal) => apiGet<ProductOption[]>("/products/options", q ? { q } : undefined, signal),
};
