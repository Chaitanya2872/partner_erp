import { apiGet, apiSend, CSRF_HEADERS, http, refreshAccessToken } from "./api";
import { tokenStore } from "./storage.service";
import type { ApiEnvelope } from "@/types/api.types";
import type { AuthResponse, Me, ModuleInfo } from "@/modules/auth/types/auth.types";

export const authService = {
  async login(email: string, password: string): Promise<Me> {
    const res = await apiSend<AuthResponse>("post", "/auth/login", { email, password });
    tokenStore.set(res.accessToken);
    return res.user;
  },

  /** Restores a session from the refresh cookie (page load); null when there is no valid session. */
  async restore(): Promise<Me | null> {
    const token = await refreshAccessToken();
    if (!token) {
      return null;
    }
    return authService.me();
  },

  me(): Promise<Me> {
    return apiGet<Me>("/auth/me");
  },

  async logout(): Promise<void> {
    try {
      await http.post<ApiEnvelope<void>>("/auth/logout", null, { headers: CSRF_HEADERS });
    } finally {
      tokenStore.clear();
    }
  },

  async changePassword(currentPassword: string, newPassword: string): Promise<Me> {
    const res = await apiSend<AuthResponse>("post", "/auth/change-password", { currentPassword, newPassword });
    tokenStore.set(res.accessToken);
    return res.user;
  },

  modules(): Promise<ModuleInfo[]> {
    return apiGet<ModuleInfo[]>("/modules");
  },
};
