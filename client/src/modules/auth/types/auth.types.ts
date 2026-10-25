import type { DataScope, RoleRef } from "@/types/common.types";

export interface ModulePermission {
  moduleCode: string;
  read: boolean;
  write: boolean;
  delete: boolean;
  export: boolean;
  approve: boolean;
  dataScope: DataScope | null;
}

/** GET /auth/me: profile plus effective permissions (presentation only; the server enforces them). */
export interface Me {
  id: string;
  fullName: string;
  email: string;
  role: RoleRef;
  superAdmin: boolean;
  mustChangePassword: boolean;
  lastLoginAt: string | null;
  modules: ModulePermission[];
  authorities: string[];
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  expiresAt: string;
  user: Me;
}

export interface ModuleAction {
  code: string;
  displayName: string;
  description: string | null;
  kind: "GENERIC" | "MODULE";
  moduleCode: string | null;
  superAdminOnly: boolean;
}

/** GET /modules: the catalogue driving navigation labels and the permission matrix. */
export interface ModuleInfo {
  code: string;
  displayName: string;
  description: string | null;
  sortOrder: number;
  supportsWrite: boolean;
  supportsDelete: boolean;
  supportsExport: boolean;
  supportsApprove: boolean;
  supportsScope: boolean;
  superAdminOnly: boolean;
  actions: ModuleAction[];
}
