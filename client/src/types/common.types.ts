/** Stable module keys; they match the backend `modules.code` values. */
export const MODULES = [
  "DASHBOARD",
  "PARTNERS",
  "LEADS",
  "PRODUCTS",
  "ESTIMATES",
  "INVOICES",
  "PAYMENTS",
  "PAYOUTS",
  "INTERNAL_B2C",
  "REPORTS",
  "SETTINGS",
  "USER_MANAGEMENT",
  "ROLE_MANAGEMENT",
  "AUDIT_LOGS",
] as const;

export type ModuleCode = (typeof MODULES)[number];

/** Generic levels plus module-specific actions (e.g. `MARK_PAYOUT_PAID`). */
export type PermissionLevel = "READ" | "WRITE" | "DELETE" | "EXPORT" | "APPROVE";
export type ActionCode =
  | PermissionLevel
  | "MARK_PAYOUT_PAID"
  | "REVERSE_PAYMENT"
  | "VIEW_INTERNAL_B2C"
  | "VIEW_PARTNER_SENSITIVE"
  | "MANAGE_USERS"
  | "MANAGE_PERMISSIONS"
  | "MANAGE_SETTINGS";

export type DataScope = "SELF" | "TEAM" | "ALL";
export type RoleCode = "SUPER_ADMIN" | "ADMIN" | "USER";
export type UserStatus = "ACTIVE" | "DISABLED" | "LOCKED";

export interface RoleRef {
  id: string;
  code: RoleCode | string;
  displayName: string;
}
