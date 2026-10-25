import {
  BarChart3,
  ClipboardList,
  FileSpreadsheet,
  FileText,
  Handshake,
  LayoutDashboard,
  Lock,
  Package,
  ScrollText,
  Settings,
  Users,
  Wallet,
  Banknote,
  type LucideIcon,
} from "lucide-react";
import type { Permissions } from "@/lib/permissions";
import type { ModuleCode } from "@/types/common.types";

export interface NavItem {
  path: string;
  /** Module whose display name (from the database) labels the item and whose READ permission shows it. */
  module: ModuleCode;
  /** Alternative modules that also make the item visible (Settings hosts Role Management). */
  alsoVisibleWith?: ModuleCode[];
  icon: LucideIcon;
  group: "Workspace" | "Administration";
  /** Phase that delivers the module's screens; undefined when available now. */
  phase?: number;
}

export const NAV_ITEMS: NavItem[] = [
  { path: "/dashboard", module: "DASHBOARD", icon: LayoutDashboard, group: "Workspace" },
  { path: "/partners", module: "PARTNERS", icon: Handshake, group: "Workspace", phase: 5 },
  { path: "/leads", module: "LEADS", icon: ClipboardList, group: "Workspace", phase: 6 },
  { path: "/products", module: "PRODUCTS", icon: Package, group: "Workspace" },
  { path: "/estimates", module: "ESTIMATES", icon: FileSpreadsheet, group: "Workspace", phase: 8 },
  { path: "/invoices", module: "INVOICES", icon: FileText, group: "Workspace", phase: 9 },
  { path: "/payments", module: "PAYMENTS", icon: Wallet, group: "Workspace", phase: 10 },
  { path: "/payouts", module: "PAYOUTS", icon: Banknote, group: "Workspace", phase: 11 },
  { path: "/b2c", module: "INTERNAL_B2C", icon: Lock, group: "Workspace", phase: 8 },
  { path: "/reports", module: "REPORTS", icon: BarChart3, group: "Workspace", phase: 12 },
  { path: "/users", module: "USER_MANAGEMENT", icon: Users, group: "Administration" },
  { path: "/settings", module: "SETTINGS", alsoVisibleWith: ["ROLE_MANAGEMENT"], icon: Settings, group: "Administration" },
  { path: "/audit-logs", module: "AUDIT_LOGS", icon: ScrollText, group: "Administration" },
];

export function isVisible(item: NavItem, permissions: Pick<Permissions, "canRead">): boolean {
  return [item.module, ...(item.alsoVisibleWith ?? [])].some((m) => permissions.canRead(m));
}

/** Fallback label used only until the module catalogue has loaded. */
export function fallbackLabel(module: string): string {
  return module
    .toLowerCase()
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}
