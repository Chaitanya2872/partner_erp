import { apiDownload, apiGet, apiPage, apiSend } from "@/services/api";
import type {
  Payment,
  PaymentFilters,
  PaymentHistoryEntry,
  PaymentInput,
  PaymentInvoiceOption,
  PaymentMeta,
  PaymentReport,
  PaymentSummary,
} from "../types/payment.types";

/** Empty filter values are dropped so the URL only carries active filters. */
const params = (filters: Partial<PaymentFilters>) =>
  Object.fromEntries(Object.entries(filters).filter(([, v]) => v !== "" && v !== undefined && v !== null));

type ReportFilters = Omit<PaymentFilters, "page" | "size" | "sort">;

export const paymentService = {
  list: (filters: Partial<PaymentFilters>, signal?: AbortSignal) => apiPage<PaymentSummary>("/payments", params(filters), signal),
  report: (filters: Partial<ReportFilters>, signal?: AbortSignal) => apiGet<PaymentReport>("/payments/report", params(filters), signal),
  meta: (signal?: AbortSignal) => apiGet<PaymentMeta>("/payments/meta", undefined, signal),
  get: (id: string, signal?: AbortSignal) => apiGet<Payment>(`/payments/${id}`, undefined, signal),
  history: (id: string, signal?: AbortSignal) => apiGet<PaymentHistoryEntry[]>(`/payments/${id}/history`, undefined, signal),
  /** Payments write. 201 for a new entry; 200 with the same entry when the idempotency key was already used. */
  record: (input: PaymentInput) => apiSend<Payment>("post", "/payments", input),
  /** Payments reverse: keeps the receipt and adds a reversal entry. */
  reverse: (id: string, reason: string) => apiSend<Payment>("post", `/payments/${id}/reverse`, { reason }),
  exportCsv: (filters: Partial<ReportFilters>) => apiDownload("/payments/export", "iotiq-payments.csv", params(filters)),
  /** Restricted picker (Payments write): issued invoices with a balance, minimum fields, at most 50. */
  invoiceOptions: (p: { q?: string; invoiceId?: string }, signal?: AbortSignal) =>
    apiGet<PaymentInvoiceOption[]>("/payments/invoice-options", params(p), signal),
};
