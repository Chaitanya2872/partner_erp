import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/lib/queryKeys";
import { invoiceService } from "../services/invoiceService";
import type { InvoiceInput } from "../types/invoice.types";

/** Statuses, GST states, place-of-supply bases, defaults and the caller's approve/cancel/fee flags. */
export function useInvoiceMeta() {
  return useQuery({
    queryKey: queryKeys.invoices.meta,
    queryFn: ({ signal }) => invoiceService.meta(signal),
    staleTime: 60_000,
  });
}

export function useInvoice(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.invoices.detail(id ?? ""),
    queryFn: ({ signal }) => invoiceService.get(id!, signal),
    enabled: !!id,
  });
}

/** Server tax and totals for the editor (debounced input); the previous result stays while the next loads. */
export function useInvoicePreview(input: InvoiceInput | null, invoiceId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.invoices.preview(invoiceId, input),
    queryFn: () => invoiceService.preview(input!, invoiceId),
    enabled: input !== null,
    placeholderData: keepPreviousData,
    retry: false,
    staleTime: 30_000,
  });
}

/** Eligible leads matching `q` (server-searched; minimum fields only). */
export function useInvoiceLeadOptions(q: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.invoices.leadOptions(q),
    queryFn: ({ signal }) => invoiceService.leadOptions({ q }, signal),
    enabled,
    placeholderData: keepPreviousData,
    staleTime: 30_000,
  });
}
