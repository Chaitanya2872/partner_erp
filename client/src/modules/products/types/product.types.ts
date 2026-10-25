export type ProductCategory = "SMART_TOUCH_INTERFACES" | "SMART_RELAY_SENSING" | "SMART_GLIDE" | "LOCKS_ENTRY" | "SERVICES";

/** The fixed source category list (BR-I2, P7-03); the server rejects anything else. */
export const PRODUCT_CATEGORIES: { value: ProductCategory; label: string }[] = [
  { value: "SMART_TOUCH_INTERFACES", label: "Smart Touch Interfaces" },
  { value: "SMART_RELAY_SENSING", label: "Smart Relay & Sensing" },
  { value: "SMART_GLIDE", label: "Smart Glide" },
  { value: "LOCKS_ENTRY", label: "Locks & Entry" },
  { value: "SERVICES", label: "Services" },
];

/** Partner-safe fields, returned to every reader of the price sheet. Amounts are decimal numbers in INR. */
export interface Product {
  id: string;
  sku: string;
  category: ProductCategory;
  categoryLabel: string;
  partnerName: string;
  unit: string;
  hsn: string | null;
  partnerPrice: number;
  gstPercent: number;
  active: boolean;
  version: number;
  updatedAt: string;
  /** Internal fields: present only when the server granted PRODUCTS:VIEW_INTERNAL_B2C. */
  b2cName?: string | null;
  aliases?: string[];
  b2cPrice?: number | null;
  costPrice?: number | null;
  /** BR-I6 discount of the partner price against the B2C price (internal only). */
  discountPercent?: number;
  /** BR-I5 "B2C price needed" (internal only). */
  b2cPriceMissing?: boolean;
  /** BR-I5 "same as B2C" warning (internal only). */
  sameNameAsB2c?: boolean;
}

export interface ProductFilters {
  q?: string;
  category?: ProductCategory | "";
  /** "true" / "false" / "" for all. */
  active?: string;
  /** Internal only: "true" = B2C price needed, "false" = set. Ignored by the server for other callers. */
  b2cMissing?: string;
  page: number;
  size: number;
  /** One of the server whitelist: sku, partnerName, category, partnerPrice, updatedAt (default category). */
  sort?: string;
}

/** Create/update body. Internal fields are omitted entirely by callers without VIEW_INTERNAL_B2C. */
export interface ProductInput {
  sku: string;
  category: ProductCategory;
  unit: string;
  hsn: string;
  partnerName: string;
  partnerPrice: string;
  gstPercent: string;
  b2cName?: string;
  aliases?: string[];
  b2cPrice?: string | null;
  costPrice?: string | null;
  version?: number;
}

export interface ProductMeta {
  categories: { value: ProductCategory; label: string; total: number; active: number }[];
  defaultGstPercent: number;
  defaultUnit: string;
  maxAliases: number;
}

export interface ProductHistoryEntry {
  id: string;
  at: string;
  actorName: string | null;
  action: "PRODUCT_CREATED" | "PRODUCT_UPDATED" | "PRODUCT_STATUS_CHANGED" | "PRODUCT_DELETED" | "PRODUCT_IMPORTED";
  changes: { field: string; from: unknown; to: unknown }[];
}

export type ImportOutcome = "CREATE" | "UPDATE" | "UNCHANGED" | "ERROR";

export interface ProductImportResult {
  dryRun: boolean;
  total: number;
  created: number;
  updated: number;
  unchanged: number;
  failed: number;
  columns: string[];
  rows: { row: number; sku: string | null; outcome: ImportOutcome; changes: string[]; errors: string[]; warnings: string[] }[];
}
