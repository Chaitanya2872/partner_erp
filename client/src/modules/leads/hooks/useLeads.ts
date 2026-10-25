import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/lib/queryKeys";
import { leadService } from "../services/leadService";

/** Stages, project types, referring partners and SLA targets for forms and filters. */
export function useLeadMeta() {
  return useQuery({
    queryKey: queryKeys.leads.meta,
    queryFn: ({ signal }) => leadService.meta(signal),
    staleTime: 60_000,
  });
}

export function useLead(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.leads.detail(id ?? ""),
    queryFn: ({ signal }) => leadService.get(id!, signal),
    enabled: !!id,
  });
}
