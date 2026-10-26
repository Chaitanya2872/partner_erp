import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/lib/queryKeys";
import { settingsService } from "../services/settingsService";

/** The supplier's GST registration (legal name, address, registered state, GSTIN). */
export function useGstRegistration(enabled = true) {
  return useQuery({
    queryKey: queryKeys.settings.gstRegistration,
    queryFn: ({ signal }) => settingsService.gstRegistration(signal),
    enabled,
  });
}
