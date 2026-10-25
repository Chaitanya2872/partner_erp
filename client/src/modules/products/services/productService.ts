import { apiPage } from "@/services/api";
import type { Product, ProductCategory } from "../types/product.types";

export interface ProductFilters {
  q?: string;
  category?: ProductCategory | "";
  page: number;
  size: number;
  /** One of the server whitelist: sku, partnerName, category, partnerPrice (default category). */
  sort?: string;
}

export const productService = {
  list: (params: ProductFilters, signal?: AbortSignal) =>
    apiPage<Product>("/products", { ...params, sort: params.sort ?? "category,asc" }, signal),
};
