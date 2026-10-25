import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/lib/queryKeys";
import { partnerService } from "../services/partnerService";

/** Categories, statuses and tiers for forms and filters. Tier names change only in Settings, so cache for a while. */
export function usePartnerMeta() {
  return useQuery({
    queryKey: queryKeys.partners.meta,
    queryFn: ({ signal }) => partnerService.meta(signal),
    staleTime: 5 * 60_000,
  });
}

export function usePartner(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.partners.detail(id ?? ""),
    queryFn: ({ signal }) => partnerService.get(id!, signal),
    enabled: !!id,
  });
}
