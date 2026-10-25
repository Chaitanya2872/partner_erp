export type ProductCategory = "SMART_TOUCH_INTERFACES" | "SMART_RELAY_SENSING" | "SMART_GLIDE" | "LOCKS_ENTRY" | "SERVICES";

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
  /** Internal fields: present only when the server granted PRODUCTS:VIEW_INTERNAL_B2C. */
  b2cName?: string | null;
  aliases?: string[];
  b2cPrice?: number | null;
  costPrice?: number | null;
}
