import { apiGet, apiPage, apiSend } from "@/services/api";
import type { UserStatus } from "@/types/common.types";
import type { AuditLog } from "@/modules/audit/types/audit.types";
import type { CreateUserInput, PermissionDraft, PermissionPreview, UpdateUserInput, User, UserFilters, UserPermissions } from "../types/user.types";

export const userService = {
  list: (filters: UserFilters, signal?: AbortSignal) => apiPage<User>("/users", { ...filters }, signal),
  get: (id: string, signal?: AbortSignal) => apiGet<User>(`/users/${id}`, undefined, signal),
  create: (input: CreateUserInput) => apiSend<User>("post", "/users", input),
  update: (id: string, input: UpdateUserInput) => apiSend<User>("put", `/users/${id}`, input),
  setStatus: (id: string, status: Exclude<UserStatus, "LOCKED">, version: number) =>
    apiSend<User>("patch", `/users/${id}/status`, { status, version }),
  /** Ends a sign-in lockout early. */
  unlock: (id: string, version: number) => apiSend<User>("post", `/users/${id}/unlock`, { version }),
  /** Sets a temporary password the user must change at the next sign-in; every session is signed out. */
  resetPassword: (id: string, temporaryPassword: string, version: number) =>
    apiSend<User>("post", `/users/${id}/password-reset`, { temporaryPassword, version }),
  activity: (id: string, page: number, size: number, signal?: AbortSignal) =>
    apiPage<AuditLog>(`/users/${id}/activity`, { page, size }, signal),
  permissions: (id: string, signal?: AbortSignal) => apiGet<UserPermissions>(`/users/${id}/permissions`, undefined, signal),
  previewPermissions: (id: string, draft: PermissionDraft) =>
    apiSend<PermissionPreview>("post", `/users/${id}/permissions/preview`, draft),
  replacePermissions: (id: string, draft: PermissionDraft) =>
    apiSend<UserPermissions>("put", `/users/${id}/permissions`, draft),
};
