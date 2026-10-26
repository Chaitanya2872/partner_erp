import {
  Banknote,
  BarChart3,
  ClipboardList,
  FileSpreadsheet,
  FileText,
  Handshake,
  KeyRound,
  LayoutDashboard,
  Package,
  ScrollText,
  Settings,
  ShieldCheck,
  Users,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import type { Permissions } from "@/lib/permissions";
import type { ModuleCode } from "@/types/common.types";

/**
 * Sidebar structure, following the original application (Dashboard · Funnel · Sales · Master) plus an
 * Administration group for Phase 3 screens. Visibility comes from the user's effective READ permission on the
 * item's module (never from role names); labels come from the module catalogue in the database.
 *
 * Internal B2C is deliberately absent: it is a permission and a workflow inside Estimates/Invoices (Phase 8),
 * not a separate module page.
 */
export type NavGroup = "Funnel" | "Sales" | "Master" | "Administration";

export interface NavItem {
  path: string;
  /** Module whose display name labels the item and whose READ permission shows it. */
  module: ModuleCode;
  icon: LucideIcon;
  /** Undefined for top-level items (Dashboard). */
  group?: NavGroup;
  /** Phase that delivers the module's screens; undefined when available now. */
  phase?: number;
}

export const NAV_ITEMS: NavItem[] = [
  { path: "/dashboard", module: "DASHBOARD", icon: LayoutDashboard },
  { path: "/partners", module: "PARTNERS", icon: Handshake, group: "Funnel" },
  { path: "/leads", module: "LEADS", icon: ClipboardList, group: "Funnel" },
  { path: "/estimates", module: "ESTIMATES", icon: FileSpreadsheet, group: "Sales" },
  { path: "/invoices", module: "INVOICES", icon: FileText, group: "Sales" },
  { path: "/payments", module: "PAYMENTS", icon: Wallet, group: "Sales" },
  { path: "/payouts", module: "PAYOUTS", icon: Banknote, group: "Sales" },
  { path: "/products", module: "PRODUCTS", icon: Package, group: "Master" },
  { path: "/reports", module: "REPORTS", icon: BarChart3, group: "Master", phase: 12 },
  { path: "/settings", module: "SETTINGS", icon: Settings, group: "Master" },
  { path: "/users", module: "USER_MANAGEMENT", icon: Users, group: "Administration" },
  { path: "/roles", module: "ROLE_MANAGEMENT", icon: ShieldCheck, group: "Administration" },
  { path: "/audit-logs", module: "AUDIT_LOGS", icon: ScrollText, group: "Administration" },
];

export const NAV_GROUPS: NavGroup[] = ["Funnel", "Sales", "Master", "Administration"];

/** Icon for the account area (change password). */
export const ACCOUNT_ICON = KeyRound;

export function isVisible(item: NavItem, permissions: Pick<Permissions, "canRead">): boolean {
  return permissions.canRead(item.module);
}

export interface NavSection {
  group?: NavGroup;
  items: NavItem[];
}

/** Visible items grouped for rendering; groups with no visible item are dropped. */
export function visibleSections(permissions: Pick<Permissions, "canRead">): NavSection[] {
  const visible = NAV_ITEMS.filter((item) => isVisible(item, permissions));
  const sections: NavSection[] = [];
  const top = visible.filter((item) => !item.group);
  if (top.length > 0) sections.push({ items: top });
  for (const group of NAV_GROUPS) {
    const items = visible.filter((item) => item.group === group);
    if (items.length > 0) sections.push({ group, items });
  }
  return sections;
}

export function navItemByPath(path: string): NavItem | undefined {
  return NAV_ITEMS.find((item) => item.path === path);
}

/** Fallback label used only until the module catalogue has loaded. */
export function fallbackLabel(module: string): string {
  return module
    .toLowerCase()
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}
