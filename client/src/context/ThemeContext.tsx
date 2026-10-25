import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { PREFERENCE_KEYS, preferences } from "@/lib/storage";
import {
  applyTheme,
  readThemePreference,
  resolveTheme,
  watchSystemTheme,
  type ResolvedTheme,
  type ThemePreference,
} from "@/lib/theme";

export interface ThemeContextValue {
  /** What the user chose. */
  preference: ThemePreference;
  /** What is shown (System resolved against the OS setting). */
  resolved: ResolvedTheme;
  setPreference: (preference: ThemePreference) => void;
}

export const ThemeContext = createContext<ThemeContextValue | null>(null);

/** Light / Dark / System theme, remembered on this device and applied to <html data-theme>. */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preference, setPreferenceState] = useState<ThemePreference>(readThemePreference);
  const [resolved, setResolved] = useState<ResolvedTheme>(() => resolveTheme(preference));

  useEffect(() => {
    const sync = () => {
      const next = resolveTheme(preference);
      setResolved(next);
      applyTheme(next);
    };
    sync();
    // Only System follows OS changes; an explicit choice stays put.
    return preference === "system" ? watchSystemTheme(sync) : undefined;
  }, [preference]);

  const setPreference = useCallback((next: ThemePreference) => {
    preferences.set(PREFERENCE_KEYS.theme, next);
    setPreferenceState(next);
  }, []);

  const value = useMemo(() => ({ preference, resolved, setPreference }), [preference, resolved, setPreference]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
