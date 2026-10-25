import { formatCurrency, formatPercent, NOT_AVAILABLE } from "@/utils/format";
import { PRODUCT_CATEGORIES, type ProductHistoryEntry } from "../types/product.types";

const FIELD_LABELS: Record<string, string> = {
  sku: "SKU",
  category: "Category",
  unit: "Unit",
  hsn: "HSN / SAC",
  partnerName: "Partner-facing name",
  partnerPrice: "Partner price",
  gstPercent: "GST",
  b2cName: "B2C name",
  aliases: "Alternative names",
  b2cPrice: "B2C price",
  costPrice: "Landed cost",
  active: "Status",
};

const MONEY = new Set(["partnerPrice", "b2cPrice", "costPrice"]);

export const HISTORY_TITLES: Record<ProductHistoryEntry["action"], string> = {
  PRODUCT_CREATED: "Created",
  PRODUCT_UPDATED: "Updated",
  PRODUCT_STATUS_CHANGED: "Status changed",
  PRODUCT_DELETED: "Deleted",
  PRODUCT_IMPORTED: "Imported from CSV",
};

export function fieldLabel(field: string): string {
  return FIELD_LABELS[field] ?? field;
}

/** A history value as the user would see it on the form. */
export function historyValue(field: string, value: unknown): string {
  if (value === null || value === undefined || value === "") return NOT_AVAILABLE;
  if (field === "active") return value ? "Active" : "Inactive";
  if (MONEY.has(field)) return formatCurrency(value as number);
  if (field === "gstPercent") return formatPercent(value as number);
  if (field === "category") return PRODUCT_CATEGORIES.find((c) => c.value === value)?.label ?? String(value);
  if (Array.isArray(value)) return value.length === 0 ? NOT_AVAILABLE : value.join(", ");
  return String(value);
}

/** "Partner price: ₹8,000.00 → ₹8,100.00"; a creation lists the values only. */
export function describeChange(change: ProductHistoryEntry["changes"][number], created: boolean): string {
  const label = fieldLabel(change.field);
  if (created) return `${label}: ${historyValue(change.field, change.to)}`;
  return `${label}: ${historyValue(change.field, change.from)} → ${historyValue(change.field, change.to)}`;
}

/** BR-I6 discount from form strings, for the live preview (the server computes the stored figure). */
export function discountPreview(partnerPrice: string, b2cPrice: string): number | null {
  const cp = Number(partnerPrice);
  const b2c = Number(b2cPrice);
  if (!partnerPrice || !b2cPrice || !Number.isFinite(cp) || !Number.isFinite(b2c) || b2c <= 0) return null;
  return Math.round((1 - cp / b2c) * 100);
}
