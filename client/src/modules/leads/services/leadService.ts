import { apiDelete, apiGet, apiPage, apiSend } from "@/services/api";
import type { Lead, LeadFilters, LeadHistoryEntry, LeadInput, LeadMeta, LeadStage, LeadSummary } from "../types/lead.types";

/** Empty filter values are dropped so the URL only carries active filters. */
const params = (filters: LeadFilters) =>
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
  remove: (id: string) => apiDelete(`/leads/${id}`),
};
