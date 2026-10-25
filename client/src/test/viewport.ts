/**
 * Controllable matchMedia for tests: evaluates min-width / max-width / prefers-color-scheme queries against a
 * simulated viewport and notifies listeners when it changes. Defaults to a 1280px desktop in light mode.
 */
type Listener = (event: MediaQueryListEvent) => void;

const state = { width: 1280, dark: false };
const lists = new Map<string, { query: string; listeners: Set<Listener>; last: boolean }>();

function evaluate(query: string): boolean {
  return query
    .split(",")
    .some((part) =>
      part
        .split(/\band\b/)
        .map((p) => p.trim())
        .every((clause) => {
          const min = clause.match(/min-width:\s*(\d+)px/);
          if (min) return state.width >= Number(min[1]);
          const max = clause.match(/max-width:\s*(\d+)px/);
          if (max) return state.width <= Number(max[1]);
          const scheme = clause.match(/prefers-color-scheme:\s*(dark|light)/);
          if (scheme) return (scheme[1] === "dark") === state.dark;
          return true;
        }),
    );
}

function notify() {
  for (const entry of lists.values()) {
    const matches = evaluate(entry.query);
    if (matches !== entry.last) {
      entry.last = matches;
      entry.listeners.forEach((l) => l({ matches, media: entry.query } as MediaQueryListEvent));
    }
  }
}

export function installMatchMedia() {
  window.matchMedia = (query: string): MediaQueryList => {
    let entry = lists.get(query);
    if (!entry) {
      entry = { query, listeners: new Set<Listener>(), last: evaluate(query) };
      lists.set(query, entry);
    }
    const listeners = entry.listeners;
    return {
      get matches() {
        return evaluate(query);
      },
      media: query,
      onchange: null,
      addEventListener: (_: string, listener: Listener) => listeners.add(listener),
      removeEventListener: (_: string, listener: Listener) => listeners.delete(listener),
      addListener: (listener: Listener) => listeners.add(listener),
      removeListener: (listener: Listener) => listeners.delete(listener),
      dispatchEvent: () => true,
    } as unknown as MediaQueryList;
  };
}

export function setViewport(width: number) {
  state.width = width;
  window.innerWidth = width;
  notify();
}

export function setSystemDark(dark: boolean) {
  state.dark = dark;
  notify();
}

export function resetViewport() {
  state.width = 1280;
  state.dark = false;
  for (const entry of lists.values()) entry.last = evaluate(entry.query);
}
