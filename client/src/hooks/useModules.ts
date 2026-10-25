import { useQuery } from "@tanstack/react-query";
import { authService } from "@/services/auth.service";
import { fallbackLabel } from "@/app/navigation";

/** Module catalogue (labels, supported permission columns, actions) from GET /modules. */
export function useModules() {
  const query = useQuery({ queryKey: ["modules"], queryFn: authService.modules, staleTime: 10 * 60_000 });
  const labelOf = (code: string): string =>
    query.data?.find((m) => m.code === code)?.displayName ?? fallbackLabel(code);
  return { ...query, labelOf };
}
