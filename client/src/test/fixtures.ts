import type { Me, ModuleInfo, ModulePermission } from "@/modules/auth/types/auth.types";

/** Test fixtures mirroring the V12 module catalogue and role templates (tests only). */
type Flags = "W" | "D" | "E" | "A" | "S";
const defs: [string, string, Flags[], boolean?][] = [
  ["DASHBOARD", "Dashboard", []],
  ["PARTNERS", "Partners", ["W", "D", "E", "A", "S"]],
  ["LEADS", "Leads", ["W", "D", "E", "A", "S"]],
  ["PRODUCTS", "Products", ["W", "D", "E"]],
  ["ESTIMATES", "Estimates", ["W", "D", "E", "S"]],
  ["INVOICES", "Invoices", ["W", "D", "E", "S"]],
  ["PAYMENTS", "Payments", ["W", "E", "S"]],
  ["PAYOUTS", "Payouts", ["W", "E", "S"]],
  ["INTERNAL_B2C", "Internal B2C", ["E"]],
  ["REPORTS", "Reports", ["E"]],
  ["SETTINGS", "Settings", ["W"]],
  ["USER_MANAGEMENT", "User Management", ["W"]],
  ["ROLE_MANAGEMENT", "Role Management", ["W"], true],
  ["AUDIT_LOGS", "Audit Logs", ["E"]],
];

const actions: Record<string, { code: string; displayName: string; superAdminOnly?: boolean }[]> = {
  PRODUCTS: [{ code: "VIEW_INTERNAL_B2C", displayName: "View internal B2C prices" }],
  PAYMENTS: [{ code: "REVERSE_PAYMENT", displayName: "Reverse payment" }],
  PAYOUTS: [{ code: "MARK_PAYOUT_PAID", displayName: "Mark payout paid" }],
  SETTINGS: [{ code: "MANAGE_SETTINGS", displayName: "Manage settings" }],
  USER_MANAGEMENT: [
    { code: "MANAGE_USERS", displayName: "Activate / deactivate users" },
    { code: "MANAGE_PERMISSIONS", displayName: "Manage permissions", superAdminOnly: true },
  ],
};

export const catalog: ModuleInfo[] = defs.map(([code, displayName, flags, superAdminOnly], i) => ({
  code,
  displayName,
  description: null,
  sortOrder: i + 1,
  supportsWrite: flags.includes("W"),
  supportsDelete: flags.includes("D"),
  supportsExport: flags.includes("E"),
  supportsApprove: flags.includes("A"),
  supportsScope: flags.includes("S"),
  superAdminOnly: !!superAdminOnly,
  actions: (actions[code] ?? []).map((a) => ({
    code: a.code,
    displayName: a.displayName,
    description: null,
    kind: "MODULE" as const,
    moduleCode: code,
    superAdminOnly: !!a.superAdminOnly,
  })),
}));

export function row(moduleCode: string, flags: Partial<ModulePermission> = {}): ModulePermission {
  return { moduleCode, read: true, write: false, delete: false, export: false, approve: false, dataScope: null, ...flags };
}

function authoritiesOf(modules: ModulePermission[], actions: string[]): string[] {
  const out: string[] = [];
  for (const m of modules) {
    out.push(`${m.moduleCode}:READ`);
    if (m.write) out.push(`${m.moduleCode}:WRITE`);
    if (m.delete) out.push(`${m.moduleCode}:DELETE`);
    if (m.export) out.push(`${m.moduleCode}:EXPORT`);
    if (m.approve) out.push(`${m.moduleCode}:APPROVE`);
  }
  return [...out, ...actions];
}

export function makeMe(role: "SUPER_ADMIN" | "ADMIN" | "USER", overrides: Partial<Me> = {}, actions: string[] = []): Me {
  const roleNames = { SUPER_ADMIN: "Super Admin", ADMIN: "Admin", USER: "User" };
  let modules: ModulePermission[];
  if (role === "SUPER_ADMIN") {
    modules = catalog.map((m) =>
      row(m.code, { write: m.supportsWrite, delete: m.supportsDelete, export: m.supportsExport, approve: m.supportsApprove }),
    );
    actions = catalog.flatMap((m) => m.actions.map((a) => `${m.code}:${a.code}`));
  } else if (role === "ADMIN") {
    modules = [
      row("DASHBOARD"),
      row("PARTNERS", { write: true, dataScope: "ALL" }),
      row("LEADS", { write: true, dataScope: "ALL" }),
      row("PRODUCTS", { write: true }),
      row("ESTIMATES", { write: true, dataScope: "ALL" }),
      row("INVOICES", { write: true, dataScope: "ALL" }),
      row("PAYMENTS", { write: true, dataScope: "ALL" }),
      row("PAYOUTS", { dataScope: "ALL" }),
      row("REPORTS"),
    ];
  } else {
    modules = [row("DASHBOARD"), row("PRODUCTS")];
  }
  const finalModules = overrides.modules ?? modules;
  return {
    id: `${role.toLowerCase()}-id`,
    fullName: `Test ${roleNames[role]}`,
    email: `${role.toLowerCase()}@example.test`,
    role: { id: `role-${role}`, code: role, displayName: roleNames[role] },
    superAdmin: role === "SUPER_ADMIN",
    mustChangePassword: false,
    lastLoginAt: null,
    modules: finalModules,
    authorities: authoritiesOf(finalModules, actions),
    ...overrides,
  };
}

export const roles = [
  { id: "role-SUPER_ADMIN", code: "SUPER_ADMIN", displayName: "Super Admin", description: null, userCount: 1, updatedAt: "2026-10-25T00:00:00Z", version: 0 },
  { id: "role-ADMIN", code: "ADMIN", displayName: "Admin", description: null, userCount: 2, updatedAt: "2026-10-25T00:00:00Z", version: 0 },
  { id: "role-USER", code: "USER", displayName: "User", description: null, userCount: 5, updatedAt: "2026-10-25T00:00:00Z", version: 0 },
];
