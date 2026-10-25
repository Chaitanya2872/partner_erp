import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/lib/queryKeys";
import { productService } from "../services/productService";

/** Categories with counts and form defaults (default GST from the company settings). */
export function useProductMeta() {
  return useQuery({
    queryKey: queryKeys.products.meta,
    queryFn: ({ signal }) => productService.meta(signal),
    staleTime: 60_000,
  });
}

export function useProduct(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.products.detail(id ?? ""),
    queryFn: ({ signal }) => productService.get(id!, signal),
    enabled: !!id,
  });
}
