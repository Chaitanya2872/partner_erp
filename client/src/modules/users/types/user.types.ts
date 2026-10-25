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
  lockedUntil: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface UserFilters {
  q?: string;
  roleId?: string;
  status?: UserStatus | "";
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
}

export interface PermissionDraft {
  modules: ModulePermission[];
  actions: string[];
}
