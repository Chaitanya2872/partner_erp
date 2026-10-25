import { apiPage } from "@/services/api";
import type { Product, ProductCategory } from "../types/product.types";

export const productService = {
  list: (params: { q?: string; category?: ProductCategory | ""; page: number; size: number }) =>
    apiPage<Product>("/products", { ...params, sort: "category,asc" }),
};
