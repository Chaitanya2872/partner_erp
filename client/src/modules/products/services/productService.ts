import { apiDelete, apiDownload, apiGet, apiPage, apiSend, apiUpload } from "@/services/api";
import type { Product, ProductFilters, ProductHistoryEntry, ProductImportResult, ProductInput, ProductMeta } from "../types/product.types";

export type { ProductFilters } from "../types/product.types";

/** Filters without paging, for the CSV export. */
type ExportFilters = Omit<ProductFilters, "page" | "size" | "sort">;

export const productService = {
  list: (params: ProductFilters, signal?: AbortSignal) =>
    apiPage<Product>("/products", { ...params, sort: params.sort ?? "category,asc" }, signal),
  meta: (signal?: AbortSignal) => apiGet<ProductMeta>("/products/meta", undefined, signal),
  get: (id: string, signal?: AbortSignal) => apiGet<Product>(`/products/${id}`, undefined, signal),
  history: (id: string, signal?: AbortSignal) => apiGet<ProductHistoryEntry[]>(`/products/${id}/history`, undefined, signal),
  create: (input: ProductInput) => apiSend<Product>("post", "/products", input),
  update: (id: string, input: ProductInput) => apiSend<Product>("put", `/products/${id}`, input),
  setActive: (id: string, active: boolean, version: number) => apiSend<Product>("patch", `/products/${id}/status`, { active, version }),
  remove: (id: string) => apiDelete(`/products/${id}`),
  exportCsv: (filters: ExportFilters) => apiDownload("/products/export", "iotiq-item-sheet.csv", { ...filters }),
  template: () => apiDownload("/products/import-template", "iotiq-item-template.csv"),
  /** dryRun previews without saving; false saves the valid rows. */
  importCsv: (file: File, dryRun: boolean) => apiUpload<ProductImportResult>("/products/import", file, { dryRun }),
};
