import { forwardRef, useId, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { Popover } from "radix-ui";
import { Check, ChevronsUpDown, Search } from "lucide-react";
import { controlClass } from "@/components/ui/Input";
import { cn } from "@/lib/cn";

export interface SelectOption {
  value: string;
  label: string;
  description?: string;
}

interface SearchableSelectProps {
  options: SelectOption[];
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  searchPlaceholder?: string;
  emptyText?: string;
  /** Adds an option that clears the value (e.g. "All users" in a filter). */
  clearLabel?: string;
  disabled?: boolean;
  loading?: boolean;
  /** Called with the typed search text, for lists searched on the server; matching options still filter locally. */
  onQueryChange?: (query: string) => void;
  className?: string;
  id?: string;
  "aria-label"?: string;
  "aria-invalid"?: boolean;
  "aria-describedby"?: string;
  "aria-required"?: boolean;
}

/**
 * Combobox for long option lists (partners, users, products): type to filter, arrow keys to move, Enter to choose,
 * Esc to close. Follows the WAI-ARIA combobox pattern with a listbox popup.
 */
export const SearchableSelect = forwardRef<HTMLButtonElement, SearchableSelectProps>(function SearchableSelect(
  {
    options,
    value,
    onChange,
    placeholder = "Select…",
    searchPlaceholder = "Search…",
    emptyText = "No matches",
    clearLabel,
    disabled,
    loading,
    onQueryChange,
    className,
    id,
    ...aria
  },
  ref,
) {
  const listId = useId();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const listRef = useRef<HTMLUListElement>(null);

  const all = useMemo<SelectOption[]>(() => (clearLabel ? [{ value: "", label: clearLabel }, ...options] : options), [clearLabel, options]);
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return q ? all.filter((o) => o.label.toLowerCase().includes(q) || o.description?.toLowerCase().includes(q)) : all;
  }, [all, query]);
  const selected = all.find((o) => o.value === value);

  const choose = (option: SelectOption) => {
    onChange(option.value);
    setOpen(false);
  };

  const onOpenChange = (next: boolean) => {
    setOpen(next);
    if (next) {
      setQuery("");
      onQueryChange?.("");
      setActive(Math.max(0, all.findIndex((o) => o.value === value)));
    }
  };

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      e.preventDefault();
      const delta = e.key === "ArrowDown" ? 1 : -1;
      const next = Math.min(filtered.length - 1, Math.max(0, active + delta));
      setActive(next);
      listRef.current?.querySelectorAll('[role="option"]')[next]?.scrollIntoView?.({ block: "nearest" });
    } else if (e.key === "Enter") {
      e.preventDefault();
      if (filtered[active]) choose(filtered[active]);
    } else if (e.key === "Home" || e.key === "End") {
      e.preventDefault();
      setActive(e.key === "Home" ? 0 : filtered.length - 1);
    }
  };

  return (
    <Popover.Root open={open} onOpenChange={onOpenChange}>
      <Popover.Trigger asChild disabled={disabled}>
        <button
          ref={ref}
          id={id}
          type="button"
          role="combobox"
          aria-expanded={open}
          aria-haspopup="listbox"
          aria-controls={open ? listId : undefined}
          {...aria}
          className={cn(controlClass, "flex h-9 items-center justify-between gap-2 text-left", className)}
        >
          <span className={cn("truncate", !selected && "text-muted-foreground")}>{loading ? "Loading…" : (selected?.label ?? placeholder)}</span>
          <ChevronsUpDown className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
        </button>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content
          align="start"
          sideOffset={4}
          collisionPadding={8}
          className="z-50 w-[var(--radix-popover-trigger-width)] min-w-56 max-w-[calc(100vw-16px)] rounded-lg border border-border bg-surface p-1 shadow-md origin-[var(--radix-popover-content-transform-origin)] data-[state=open]:animate-pop-in data-[state=closed]:animate-pop-out"
        >
          <div className="relative mb-1">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
            <input
              autoFocus
              type="text"
              role="searchbox"
              aria-label={searchPlaceholder}
              aria-controls={listId}
              aria-activedescendant={filtered[active] ? `${listId}-${active}` : undefined}
              value={query}
              placeholder={searchPlaceholder}
              onChange={(e) => {
                setQuery(e.target.value);
                onQueryChange?.(e.target.value);
                setActive(0);
              }}
              onKeyDown={onKeyDown}
              className="h-9 w-full rounded-md bg-surface-sunken pl-8 pr-2 text-sm outline-none placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-ring"
            />
          </div>
          <ul ref={listRef} id={listId} role="listbox" aria-label={aria["aria-label"] ?? placeholder} className="max-h-64 overflow-y-auto">
            {filtered.length === 0 ? (
              <li className="px-2.5 py-2 text-sm text-muted-foreground">{emptyText}</li>
            ) : (
              filtered.map((option, index) => (
                <li
                  key={option.value || "__clear"}
                  id={`${listId}-${index}`}
                  role="option"
                  aria-selected={option.value === value}
                  onMouseEnter={() => setActive(index)}
                  onClick={() => choose(option)}
                  className={cn(
                    "flex cursor-pointer items-start gap-2 rounded-md px-2.5 py-1.5 text-sm",
                    index === active && "bg-hover",
                  )}
                >
                  <Check className={cn("mt-0.5 size-4 shrink-0", option.value === value ? "opacity-100" : "opacity-0")} aria-hidden="true" />
                  <span className="min-w-0">
                    <span className="block truncate">{option.label}</span>
                    {option.description ? <span className="block truncate text-xs text-muted-foreground">{option.description}</span> : null}
                  </span>
                </li>
              ))
            )}
          </ul>
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
});
