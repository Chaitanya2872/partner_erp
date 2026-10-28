import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/lib/queryKeys";
import { settingsService } from "../services/settingsService";

export function useCompanySettings(enabled = true) {
  return useQuery({ queryKey: queryKeys.settings.company, queryFn: ({ signal }) => settingsService.company(signal), enabled });
}

export function usePartnerTiers(enabled = true) {
  return useQuery({ queryKey: queryKeys.settings.tiers, queryFn: ({ signal }) => settingsService.tiers(signal), enabled });
}

export function useNumbering() {
  return useQuery({ queryKey: queryKeys.settings.numbering, queryFn: ({ signal }) => settingsService.numbering(signal) });
}

export function useSecurityPolicy() {
  return useQuery({ queryKey: queryKeys.settings.security, queryFn: ({ signal }) => settingsService.security(signal) });
}

export function useControls() {
  return useQuery({ queryKey: queryKeys.settings.controls, queryFn: ({ signal }) => settingsService.controls(signal), staleTime: 5 * 60_000 });
}
