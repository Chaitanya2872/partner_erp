import type { ModulePermission } from "@/modules/auth/types/auth.types";
import type { RoleRef, UserStatus } from "@/types/common.types";

export interface User {
  id: string;
  fullName: string;
  email: string;
  role: RoleRef;
  status: UserStatus;
  mustChangePassword: boolean;
  lastLoginAt: string | null;
  /** When a temporary sign-in lockout ends; null when none is in force. */
  lockedUntil: string | null;
  locked: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface UserFilters {
  q?: string;
  roleId?: string;
  status?: UserStatus | "";
  /** true: only accounts under a sign-in lockout. */
  locked?: boolean;
  page: number;
  size: number;
  sort?: string;
}

export interface CreateUserInput {
  fullName: string;
  email: string;
  roleId: string;
  temporaryPassword: string;
}

export interface UpdateUserInput {
  fullName: string;
  email: string;
  roleId: string;
  version: number;
}

export interface UserPermissions {
  userId: string;
  role: RoleRef;
  superAdmin: boolean;
  /** Whether the current viewer may change this matrix (decided by the server). */
  editable: boolean;
  modules: ModulePermission[];
  actions: string[];
  /** Modules (and granted actions) that differ from the role default. */
  differsFromRole: string[];
}

export interface PermissionDraft {
  modules: ModulePermission[];
  actions: string[];
}

/** What saving a proposed matrix would change (POST /users/{id}/permissions/preview; nothing is saved). */
export interface PermissionPreview {
  userId: string;
  changed: boolean;
  granted: string[];
  revoked: string[];
  scopeChanges: { moduleCode: string; from: string | null; to: string | null }[];
  effective: string[];
  differsFromRole: string[];
}
