import { apiGet, apiSend } from "@/services/api";

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

export const roleService = {
  list: () => apiGet<Role[]>("/roles"),
  update: (id: string, input: UpdateRoleInput) => apiSend<Role>("patch", `/roles/${id}`, input),
};
