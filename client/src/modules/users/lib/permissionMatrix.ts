import type { ModuleInfo, ModulePermission } from "@/modules/auth/types/auth.types";
import type { DataScope } from "@/types/common.types";
import type { PermissionDraft } from "../types/user.types";

export type Flag = "read" | "write" | "delete" | "export" | "approve";

export function emptyRow(moduleCode: string): ModulePermission {
  return { moduleCode, read: false, write: false, delete: false, export: false, approve: false, dataScope: null };
}

/** Whether a module can be granted to the role (mirrors PermissionService.grantable on the server). */
export function grantable(roleCode: string, module: ModuleInfo): boolean {
  if (roleCode === "SUPER_ADMIN") return true;
  if (module.superAdminOnly) return false;
  return !(roleCode === "USER" && module.code === "USER_MANAGEMENT");
}

export function supports(module: ModuleInfo, flag: Flag): boolean {
  switch (flag) {
    case "read":
      return true;
    case "write":
      return module.supportsWrite;
    case "delete":
      return module.supportsDelete;
    case "export":
      return module.supportsExport;
    case "approve":
      return module.supportsApprove;
  }
}

/**
 * Applies a checkbox change with its dependencies: any flag enables Read, Delete enables Write; clearing Read
 * clears every other flag, clearing Write clears Delete. Write never enables Delete/Export/Approve.
 */
export function toggleFlag(row: ModulePermission, flag: Flag, value: boolean): ModulePermission {
  const next: ModulePermission = { ...row, [flag]: value };
  if (value) {
    next.read = true;
    if (flag === "delete") next.write = true;
  } else if (flag === "read") {
    next.write = next.delete = next.export = next.approve = false;
    next.dataScope = null;
  } else if (flag === "write") {
    next.delete = false;
  }
  return next;
}

/** Normalises a server matrix into one row per catalogue module (catalogue order). */
export function toDraft(catalog: ModuleInfo[], modules: ModulePermission[], actions: string[]): PermissionDraft {
  const byCode = new Map(modules.map((m) => [m.moduleCode, m]));
  return {
    modules: [...catalog]
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map((def) => ({ ...(byCode.get(def.code) ?? emptyRow(def.code)) })),
    actions: [...actions].sort(),
  };
}

export function setFlag(draft: PermissionDraft, catalog: ModuleInfo[], moduleCode: string, flag: Flag, value: boolean): PermissionDraft {
  const modules = draft.modules.map((row) => (row.moduleCode === moduleCode ? toggleFlag(row, flag, value) : row));
  const readable = new Set(modules.filter((m) => m.read).map((m) => m.moduleCode));
  // Module actions require read access to their module.
  const actions = draft.actions.filter((code) => {
    const owner = actionModule(catalog, code);
    return owner === undefined || readable.has(owner);
  });
  return { modules, actions };
}

export function setScope(draft: PermissionDraft, moduleCode: string, scope: DataScope): PermissionDraft {
  return { ...draft, modules: draft.modules.map((row) => (row.moduleCode === moduleCode ? { ...row, dataScope: scope } : row)) };
}

export function setAction(draft: PermissionDraft, catalog: ModuleInfo[], code: string, value: boolean): PermissionDraft {
  const owner = actionModule(catalog, code);
  let modules = draft.modules;
  if (value && owner) {
    modules = modules.map((row) => (row.moduleCode === owner && !row.read ? toggleFlag(row, "read", true) : row));
  }
  const actions = value ? [...new Set([...draft.actions, code])].sort() : draft.actions.filter((a) => a !== code);
  return { modules, actions };
}

function actionModule(catalog: ModuleInfo[], code: string): string | undefined {
  for (const m of catalog) {
    if (m.actions.some((a) => a.kind === "MODULE" && a.code === code)) return m.code;
  }
  return undefined;
}

/** The request body: readable modules only, scope only where supported. */
export function toRequest(draft: PermissionDraft, catalog: ModuleInfo[]): PermissionDraft {
  const defs = new Map(catalog.map((m) => [m.code, m]));
  return {
    modules: draft.modules
      .filter((m) => m.read)
      .map((m) => ({ ...m, dataScope: defs.get(m.moduleCode)?.supportsScope ? m.dataScope : null })),
    actions: draft.actions,
  };
}

/**
 * Readable, scope-capable modules without an explicit scope. A data scope is never chosen silently; the
 * administrator must pick Self, Team or All.
 */
export function missingScopes(draft: PermissionDraft, catalog: ModuleInfo[]): string[] {
  const scoped = new Set(catalog.filter((m) => m.supportsScope).map((m) => m.code));
  return draft.modules.filter((m) => m.read && scoped.has(m.moduleCode) && !m.dataScope).map((m) => m.moduleCode);
}

export function sameDraft(a: PermissionDraft, b: PermissionDraft, catalog: ModuleInfo[]): boolean {
  return JSON.stringify(toRequest(a, catalog)) === JSON.stringify(toRequest(b, catalog));
}

/** Human-readable list of sensitive grants added by the draft (shown in the confirmation dialog). */
export function sensitiveAdditions(before: PermissionDraft, after: PermissionDraft, catalog: ModuleInfo[]): string[] {
  const labels = new Map(catalog.map((m) => [m.code, m.displayName]));
  const actionLabels = new Map(catalog.flatMap((m) => m.actions.map((a) => [a.code, a.displayName] as const)));
  const old = new Map(before.modules.map((m) => [m.moduleCode, m]));
  const out: string[] = [];
  for (const row of after.modules) {
    const prev = old.get(row.moduleCode) ?? emptyRow(row.moduleCode);
    const name = labels.get(row.moduleCode) ?? row.moduleCode;
    if (row.read && !prev.read && (row.moduleCode === "INTERNAL_B2C" || row.moduleCode === "AUDIT_LOGS")) out.push(`${name}: read`);
    if (row.write && !prev.write && (row.moduleCode === "USER_MANAGEMENT" || row.moduleCode === "SETTINGS")) out.push(`${name}: write`);
    if (row.delete && !prev.delete) out.push(`${name}: delete`);
    if (row.export && !prev.export) out.push(`${name}: export`);
    if (row.approve && !prev.approve) out.push(`${name}: approve`);
    if (row.read && prev.read && prev.dataScope !== "ALL" && row.dataScope === "ALL") out.push(`${name}: scope widened to All`);
  }
  for (const code of after.actions) {
    if (!before.actions.includes(code)) out.push(actionLabels.get(code) ?? code);
  }
  return out;
}
