import { apiGet, apiPage, apiSend } from "@/services/api";
import type { UserStatus } from "@/types/common.types";
import type { CreateUserInput, PermissionDraft, UpdateUserInput, User, UserFilters, UserPermissions } from "../types/user.types";

export const userService = {
  list: (filters: UserFilters, signal?: AbortSignal) => apiPage<User>("/users", { ...filters }, signal),
  get: (id: string, signal?: AbortSignal) => apiGet<User>(`/users/${id}`, undefined, signal),
  create: (input: CreateUserInput) => apiSend<User>("post", "/users", input),
  update: (id: string, input: UpdateUserInput) => apiSend<User>("put", `/users/${id}`, input),
  setStatus: (id: string, status: Exclude<UserStatus, "LOCKED">, version: number) =>
    apiSend<User>("patch", `/users/${id}/status`, { status, version }),
  permissions: (id: string, signal?: AbortSignal) => apiGet<UserPermissions>(`/users/${id}/permissions`, undefined, signal),
  replacePermissions: (id: string, draft: PermissionDraft) =>
    apiSend<UserPermissions>("put", `/users/${id}/permissions`, draft),
};
