import { apiGet, apiPage, apiSend } from "@/services/api";
import type { UserStatus } from "@/types/common.types";
import type { CreateUserInput, PermissionDraft, UpdateUserInput, User, UserFilters, UserPermissions } from "../types/user.types";

export const userService = {
  list: (filters: UserFilters) => apiPage<User>("/users", { ...filters }),
  get: (id: string) => apiGet<User>(`/users/${id}`),
  create: (input: CreateUserInput) => apiSend<User>("post", "/users", input),
  update: (id: string, input: UpdateUserInput) => apiSend<User>("put", `/users/${id}`, input),
  setStatus: (id: string, status: Exclude<UserStatus, "LOCKED">, version: number) =>
    apiSend<User>("patch", `/users/${id}/status`, { status, version }),
  permissions: (id: string) => apiGet<UserPermissions>(`/users/${id}/permissions`),
  replacePermissions: (id: string, draft: PermissionDraft) =>
    apiSend<UserPermissions>("put", `/users/${id}/permissions`, draft),
};
