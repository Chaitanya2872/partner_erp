import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { onSessionEnded } from "@/services/api";
import { authService } from "@/services/auth.service";
import { permissionsOf, type Permissions } from "@/lib/permissions";
import type { Me } from "@/modules/auth/types/auth.types";

export type AuthStatus = "loading" | "anonymous" | "authenticated";

export interface AuthContextValue extends Permissions {
  status: AuthStatus;
  user: Me | null;
  /** True after the server ended the session (expired/revoked) until the next sign-in. */
  sessionExpired: boolean;
  login: (email: string, password: string) => Promise<Me>;
  logout: () => Promise<void>;
  changePassword: (currentPassword: string, newPassword: string) => Promise<Me>;
  /** Re-reads `/auth/me`, e.g. after an administrator changed this user's permissions or role name. */
  refreshUser: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [user, setUser] = useState<Me | null>(null);
  const [sessionExpired, setSessionExpired] = useState(false);

  const signedIn = useCallback((me: Me) => {
    setUser(me);
    setStatus("authenticated");
    setSessionExpired(false);
  }, []);

  const signedOut = useCallback(
    (expired: boolean) => {
      setUser(null);
      setStatus("anonymous");
      setSessionExpired(expired);
      queryClient.clear();
    },
    [queryClient],
  );

  useEffect(() => {
    let cancelled = false;
    authService
      .restore()
      .then((me) => {
        if (cancelled) return;
        if (me) signedIn(me);
        else signedOut(false);
      })
      .catch(() => !cancelled && signedOut(false));
    return () => {
      cancelled = true;
    };
  }, [signedIn, signedOut]);

  useEffect(() => onSessionEnded(() => signedOut(true)), [signedOut]);

  // Permissions are resolved per request on the server; re-read them when the user returns to the tab so the
  // navigation reflects changes an administrator made meanwhile.
  useEffect(() => {
    if (status !== "authenticated") return;
    const onFocus = () => {
      authService.me().then(setUser).catch(() => undefined);
    };
    window.addEventListener("focus", onFocus);
    return () => window.removeEventListener("focus", onFocus);
  }, [status]);

  const login = useCallback(
    async (email: string, password: string) => {
      const me = await authService.login(email, password);
      signedIn(me);
      return me;
    },
    [signedIn],
  );

  const logout = useCallback(async () => {
    try {
      await authService.logout();
    } finally {
      signedOut(false);
    }
  }, [signedOut]);

  const changePassword = useCallback(
    async (currentPassword: string, newPassword: string) => {
      const me = await authService.changePassword(currentPassword, newPassword);
      signedIn(me);
      return me;
    },
    [signedIn],
  );

  const refreshUser = useCallback(async () => {
    const me = await authService.me();
    setUser(me);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      sessionExpired,
      login,
      logout,
      changePassword,
      refreshUser,
      ...permissionsOf(user),
    }),
    [status, user, sessionExpired, login, logout, changePassword, refreshUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
