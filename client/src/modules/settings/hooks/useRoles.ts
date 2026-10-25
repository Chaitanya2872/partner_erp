import { useQuery } from "@tanstack/react-query";
import { roleService } from "../services/roleService";

/** Role display names always come from the database (never hard-coded in the UI). */
export function useRoles(enabled = true) {
  return useQuery({ queryKey: ["roles"], queryFn: roleService.list, enabled, staleTime: 60_000 });
}
