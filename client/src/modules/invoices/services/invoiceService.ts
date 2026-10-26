import { apiDelete, apiDownload, apiGet, apiPage, apiSend } from "@/services/api";
import type {
  InternalInvoiceDocument,
  Invoice,
  InvoiceActionInput,
  InvoiceFilters,
  InvoiceHistoryEntry,
  InvoiceInput,
  InvoiceLeadOption,
  InvoiceMeta,
  InvoicePreview,
  InvoiceSummary,
} from "../types/invoice.types";

/** Empty filter values are dropped so the URL only carries active filters. */
const params = (filters: Partial<InvoiceFilters>) =>
  Object.fromEntries(Object.entries(filters).filter(([, v]) => v !== "" && v !== undefined && v !== null));

type ExportFilters = Omit<InvoiceFilters, "page" | "size" | "sort">;

export const invoiceService = {
  list: (filters: InvoiceFilters, signal?: AbortSignal) => apiPage<InvoiceSummary>("/invoices", params(filters), signal),
  meta: (signal?: AbortSignal) => apiGet<InvoiceMeta>("/invoices/meta", undefined, signal),
  get: (id: string, signal?: AbortSignal) => apiGet<Invoice>(`/invoices/${id}`, undefined, signal),
  history: (id: string, signal?: AbortSignal) => apiGet<InvoiceHistoryEntry[]>(`/invoices/${id}/history`, undefined, signal),
  /** Server tax and totals for the editor; nothing is saved. */
  preview: (input: InvoiceInput, invoiceId?: string) =>
    apiSend<InvoicePreview>("post", invoiceId ? `/invoices/preview?invoiceId=${invoiceId}` : "/invoices/preview", input),
  create: (input: InvoiceInput) => apiSend<Invoice>("post", "/invoices", input),
  /** A draft from a sent or accepted estimate (Invoices write + Estimates read). */
  convert: (estimateId: string) => apiSend<Invoice>("post", "/invoices/from-estimate", { estimateId }),
  update: (id: string, input: InvoiceInput) => apiSend<Invoice>("put", `/invoices/${id}`, input),
  /** Invoices approve: confirms a tax treatment the server can't settle on its own. */
  confirmTax: (id: string, input: InvoiceActionInput) => apiSend<Invoice>("post", `/invoices/${id}/confirm-tax`, input),
  issue: (id: string, input: InvoiceActionInput) => apiSend<Invoice>("post", `/invoices/${id}/issue`, input),
  cancel: (id: string, input: InvoiceActionInput) => apiSend<Invoice>("post", `/invoices/${id}/cancel`, input),
  /** Deletes a draft (Invoices delete); issued invoices are cancelled instead. */
  discard: (id: string) => apiDelete(`/invoices/${id}`),
  exportCsv: (filters: ExportFilters) => apiDownload("/invoices/export", "iotiq-invoices.csv", params(filters)),
  /** Printable partner copy (HTML). */
  document: (id: string) => apiDownload(`/invoices/${id}/document`, "invoice.html"),
  /** Internal B2C twin: INTERNAL_B2C read only, and only once issued. */
  internal: (id: string, signal?: AbortSignal) => apiGet<InternalInvoiceDocument>(`/internal/invoices/${id}`, undefined, signal),
  internalDocument: (id: string) => apiDownload(`/internal/invoices/${id}/document`, "internal-invoice.html"),
  /** Restricted lead picker (Invoices write): eligible leads, minimum fields, at most 50. */
  leadOptions: (p: { q?: string; leadId?: string }, signal?: AbortSignal) => apiGet<InvoiceLeadOption[]>("/invoices/lead-options", p, signal),
};
