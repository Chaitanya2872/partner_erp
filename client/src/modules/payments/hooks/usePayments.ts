import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/lib/queryKeys";
import { paymentService } from "../services/paymentService";

/** Methods, entry types and the caller's payment actions. */
export function usePaymentMeta() {
  return useQuery({
    queryKey: queryKeys.payments.meta,
    queryFn: ({ signal }) => paymentService.meta(signal),
    staleTime: 60_000,
  });
}

export function usePayment(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.payments.detail(id ?? ""),
    queryFn: ({ signal }) => paymentService.get(id!, signal),
    enabled: !!id,
  });
}

/** Ledger entries of one invoice (Payments read). */
export function useInvoicePayments(invoiceId: string) {
  return useQuery({
    queryKey: queryKeys.payments.list({ invoiceId, size: 100 }),
    queryFn: ({ signal }) => paymentService.list({ invoiceId, page: 0, size: 100, sort: "paymentDate,asc" }, signal),
  });
}

/** Issued invoices with a balance matching `q` (server-searched; minimum fields only). */
export function usePaymentInvoiceOptions(q: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.payments.invoiceOptions(q),
    queryFn: ({ signal }) => paymentService.invoiceOptions({ q }, signal),
    enabled,
    placeholderData: keepPreviousData,
    staleTime: 15_000,
  });
}
