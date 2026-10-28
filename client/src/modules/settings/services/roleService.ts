import { apiGet, apiSend } from "@/services/api";
import type { ModulePermission } from "@/modules/auth/types/auth.types";

export interface Role {
  id: string;
  code: string;
  displayName: string;
  description: string | null;
  /** Present only for viewers with Role Management access. */
  userCount?: number;
  updatedAt: string;
  version: number;
}

export interface UpdateRoleInput {
  displayName: string;
  description: string | null;
  version: number;
}

/** A role's default permissions: what new accounts and accounts moved to the role start with. */
export interface RolePermissions {
  roleId: string;
  code: string;
  displayName: string;
  superAdmin: boolean;
  /** Super Admin viewer and not the Super Admin role (decided by the server). */
  editable: boolean;
  modules: ModulePermission[];
  userCount: number;
  version: number;
}

export const roleService = {
  list: () => apiGet<Role[]>("/roles"),
  update: (id: string, input: UpdateRoleInput) => apiSend<Role>("patch", `/roles/${id}`, input),
  permissions: (id: string, signal?: AbortSignal) => apiGet<RolePermissions>(`/roles/${id}/permissions`, undefined, signal),
  /** Changes the defaults only; existing accounts keep their permissions. */
  updatePermissions: (id: string, modules: ModulePermission[], version: number) =>
    apiSend<RolePermissions>("put", `/roles/${id}/permissions`, { modules, version }),
};
