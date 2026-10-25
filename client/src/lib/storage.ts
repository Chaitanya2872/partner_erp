/**
 * Per-device UI preferences (theme, sidebar state). Only non-sensitive presentation settings live here; tokens
 * and server data are never written to web storage. Access is wrapped because storage can be unavailable
 * (private windows, blocked site data).
 */
export const preferences = {
  get(key: string): string | null {
    try {
      return window.localStorage.getItem(key);
    } catch {
      return null;
    }
  },
  set(key: string, value: string): void {
    try {
      window.localStorage.setItem(key, value);
    } catch {
      // Preference simply is not remembered.
    }
  },
};

export const PREFERENCE_KEYS = {
  theme: "iotiq.theme",
  sidebarCollapsed: "iotiq.sidebar.collapsed",
} as const;
