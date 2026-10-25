import type { Me } from "@/modules/auth/types/auth.types";
import type { ActionCode, ModuleCode } from "@/types/common.types";

/**
 * Permission checks over the `/auth/me` authorities (`<MODULE>:<LEVEL|ACTION>`). They decide what the UI
 * shows; the backend enforces the same permissions on every request.
 */
export function canRead(me: Me | null | undefined, module: ModuleCode): boolean {
  return has(me, module, "READ");
}

export function canWrite(me: Me | null | undefined, module: ModuleCode): boolean {
  return has(me, module, "WRITE");
}

export function canPerform(me: Me | null | undefined, module: ModuleCode, action: ActionCode): boolean {
  return has(me, module, action);
}

function has(me: Me | null | undefined, module: string, level: string): boolean {
  return !!me && me.authorities.includes(`${module}:${level}`);
}

export interface Permissions {
  canRead: (module: ModuleCode) => boolean;
  canWrite: (module: ModuleCode) => boolean;
  canPerform: (module: ModuleCode, action: ActionCode) => boolean;
}

export function permissionsOf(me: Me | null | undefined): Permissions {
  return {
    canRead: (module) => canRead(me, module),
    canWrite: (module) => canWrite(me, module),
    canPerform: (module, action) => canPerform(me, module, action),
  };
}
