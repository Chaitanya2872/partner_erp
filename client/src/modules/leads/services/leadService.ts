import { apiDelete, apiGet, apiPage, apiSend } from "@/services/api";
import type { ArchiveFilters, Lead, LeadArchiveDetail, LeadArchiveEntry, LeadFilters, LeadHistoryEntry, LeadInput, LeadMeta, LeadStage, LeadSummary } from "../types/lead.types";

/** Empty filter values are dropped so the URL only carries active filters. */
const params = (filters: LeadFilters | ArchiveFilters) =>
  Object.fromEntries(Object.entries(filters).filter(([, v]) => v !== "" && v !== undefined && v !== null));

export const leadService = {
  list: (filters: LeadFilters, signal?: AbortSignal) => apiPage<LeadSummary>("/leads", params(filters), signal),
  meta: (signal?: AbortSignal) => apiGet<LeadMeta>("/leads/meta", undefined, signal),
  get: (id: string, signal?: AbortSignal) => apiGet<Lead>(`/leads/${id}`, undefined, signal),
  history: (id: string, signal?: AbortSignal) => apiGet<LeadHistoryEntry[]>(`/leads/${id}/history`, undefined, signal),
  create: (input: LeadInput) => apiSend<Lead>("post", "/leads", input),
  update: (id: string, input: LeadInput) => apiSend<Lead>("put", `/leads/${id}`, input),
  setStage: (id: string, stage: LeadStage, version: number, reason?: string) =>
    apiSend<Lead>("patch", `/leads/${id}/stage`, { stage, version, reason: reason || null }),
  acknowledge: (id: string) => apiSend<Lead>("post", `/leads/${id}/acknowledge`),
  /** Archives the lead (soft delete); the reason is required. */
  remove: (id: string, reason: string) => apiDelete(`/leads/${id}`, { reason }),
  archive: (filters: ArchiveFilters, signal?: AbortSignal) => apiPage<LeadArchiveEntry>("/leads/archive", params(filters), signal),
  archived: (id: string, signal?: AbortSignal) => apiGet<LeadArchiveDetail>(`/leads/archive/${id}`, undefined, signal),
  restore: (id: string) => apiSend<Lead>("post", `/leads/${id}/restore`),
};
