import { useSyncExternalStore } from "react";

/** Live result of a CSS media query (false where matchMedia is unavailable). */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      if (typeof window.matchMedia !== "function") return () => undefined;
      const media = window.matchMedia(query);
      media.addEventListener("change", onChange);
      return () => media.removeEventListener("change", onChange);
    },
    () => typeof window.matchMedia === "function" && window.matchMedia(query).matches,
    () => false,
  );
}

/** Layout breakpoints (Tailwind md / lg): phones get a drawer, tablets a compact rail, desktops the full sidebar. */
export const BREAKPOINTS = {
  tablet: "(min-width: 768px)",
  desktop: "(min-width: 1024px)",
} as const;
