import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/lib/queryKeys";
import { payoutService } from "../services/payoutService";

/** Statuses, limits, TDS readiness and the caller's payout actions. */
export function usePayoutMeta() {
  return useQuery({
    queryKey: queryKeys.payouts.meta,
    queryFn: ({ signal }) => payoutService.meta(signal),
    staleTime: 60_000,
  });
}

export function usePayout(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.payouts.detail(id ?? ""),
    queryFn: ({ signal }) => payoutService.get(id!, signal),
    enabled: !!id,
  });
}

/** TDS settings (Settings read or Payouts read). */
export function useTdsConfiguration(enabled = true) {
  return useQuery({
    queryKey: queryKeys.payouts.tds,
    queryFn: ({ signal }) => payoutService.tdsConfiguration(signal),
    enabled,
  });
}
