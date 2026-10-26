import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/lib/queryKeys";
import { estimateService } from "../services/estimateService";
import type { EstimateInput } from "../types/estimate.types";

/** Statuses, bill-to options, validity, default terms and whether the caller may override the fee. */
export function useEstimateMeta() {
  return useQuery({
    queryKey: queryKeys.estimates.meta,
    queryFn: ({ signal }) => estimateService.meta(signal),
    staleTime: 60_000,
  });
}

export function useEstimate(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.estimates.detail(id ?? ""),
    queryFn: ({ signal }) => estimateService.get(id!, signal),
    enabled: !!id,
  });
}

/**
 * Server totals for the editor (debounced input). The previous result stays on screen while the next one loads,
 * so the totals panel doesn't flicker while typing.
 */
export function useEstimatePreview(input: EstimateInput | null, estimateId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.estimates.preview(estimateId, input),
    queryFn: () => estimateService.preview(input!, estimateId),
    enabled: input !== null,
    placeholderData: keepPreviousData,
    retry: false,
    staleTime: 30_000,
  });
}

/** Eligible leads matching `q` (server-searched by lead number, client and partner only). */
export function useLeadOptions(q: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.estimates.leadOptions(q),
    queryFn: ({ signal }) => estimateService.leadOptions({ q }, signal),
    enabled,
    placeholderData: keepPreviousData,
    staleTime: 30_000,
  });
}

/** One eligible lead by id (the "New estimate" link); empty when the lead can't get an estimate. */
export function useLinkedLead(leadId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.estimates.leadOption(leadId ?? ""),
    queryFn: ({ signal }) => estimateService.leadOptions({ leadId }, signal),
    enabled: !!leadId,
  });
}

/** Active products matching `q` for the line picker (at most 50, server-filtered). */
export function useProductOptions(q: string, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.estimates.productOptions(q),
    queryFn: ({ signal }) => estimateService.productOptions(q, signal),
    enabled,
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  });
}
