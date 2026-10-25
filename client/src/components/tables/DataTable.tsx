import { useId, useState, type ReactNode } from "react";
import { ArrowDown, ArrowUp, ArrowUpDown, Columns3 } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from "@/components/ui/DropdownMenu";
import { EmptyState, ErrorState } from "@/components/feedback/States";
import { Pagination } from "@/components/navigation/Pagination";
import type { PageMeta } from "@/types/api.types";
import { cn } from "@/lib/cn";
import type { SortState } from "./sorting";

export interface ColumnDef<T> {
  id: string;
  header: ReactNode;
  /** Plain-text name for the column menu when `header` is not a string. */
  label?: string;
  cell: (row: T) => ReactNode;
  /** Right-aligned, monospaced, tabular figures (amounts, counts). */
  numeric?: boolean;
  align?: "left" | "center" | "right";
  /** Backend sort property; makes the header a sort button. Must be in the endpoint's sort whitelist. */
  sortKey?: string;
  /** Whether the user may hide the column (default: true, except the first column). */
  hideable?: boolean;
  defaultHidden?: boolean;
  className?: string;
}

interface DataTableProps<T> {
  /** Accessible name of the table (also used for the scroll region). */
  caption: string;
  columns: ColumnDef<T>[];
  rows: T[] | undefined;
  rowKey: (row: T) => string;
  /** First load: skeleton rows. */
  loading?: boolean;
  /** Background refetch (filters or page changed): rows stay visible and the table is marked busy. */
  fetching?: boolean;
  error?: unknown;
  onRetry?: () => void;
  empty?: ReactNode;
  sort?: SortState | null;
  onSortChange?: (sort: SortState) => void;
  page?: PageMeta;
  onPageChange?: (page: number) => void;
  pageSizeOptions?: number[];
  onPageSizeChange?: (size: number) => void;
  itemLabel?: string;
  /** Search and filter controls shown above the table. */
  toolbar?: ReactNode;
  /** Per-row actions; build them from permission checks so unauthorised actions are not rendered at all. */
  rowActions?: (row: T) => ReactNode;
  /** Offer the column-visibility menu (default true when any column is hideable). */
  columnMenu?: boolean;
  skeletonRows?: number;
  className?: string;
}

/**
 * Enterprise table for server-paginated lists: sorting, column visibility, loading/empty/error states, row
 * actions and pagination. The table scrolls horizontally inside its own keyboard-focusable region on narrow
 * screens instead of shrinking text. All data comes from the caller; the component holds only presentation state.
 */
export function DataTable<T>({
  caption,
  columns,
  rows,
  rowKey,
  loading,
  fetching,
  error,
  onRetry,
  empty,
  sort,
  onSortChange,
  page,
  onPageChange,
  pageSizeOptions,
  onPageSizeChange,
  itemLabel,
  toolbar,
  rowActions,
  columnMenu,
  skeletonRows = 6,
  className,
}: DataTableProps<T>) {
  const captionId = useId();
  const [hidden, setHidden] = useState<Set<string>>(() => new Set(columns.filter((c) => c.defaultHidden).map((c) => c.id)));
  const hideable = columns.filter((c, i) => c.hideable ?? i > 0);
  const showColumnMenu = (columnMenu ?? true) && hideable.length > 0;
  const visible = columns.filter((c) => !hidden.has(c.id));
  const colCount = visible.length + (rowActions ? 1 : 0);

  const toggleColumn = (id: string, show: boolean) => {
    setHidden((prev) => {
      const next = new Set(prev);
      if (show) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const align = (c: ColumnDef<T>) => (c.numeric ? "right" : (c.align ?? "left"));
  const alignClass = (c: ColumnDef<T>) => ({ left: "text-left", center: "text-center", right: "text-right" })[align(c)];

  let body: ReactNode;
  if (error && !rows) {
    body = (
      <tr>
        <td colSpan={colCount}>
          <ErrorState error={error} onRetry={onRetry} />
        </td>
      </tr>
    );
  } else if (loading || !rows) {
    body = Array.from({ length: skeletonRows }, (_, i) => (
      <tr key={`sk-${i}`} className="border-b border-border last:border-b-0">
        {Array.from({ length: colCount }, (_, j) => (
          <td key={j} className="px-3 py-3">
            <Skeleton className={cn("h-4", j === 0 ? "w-3/4" : "w-1/2")} />
          </td>
        ))}
      </tr>
    ));
  } else if (rows.length === 0) {
    body = (
      <tr>
        <td colSpan={colCount}>{empty ?? <EmptyState title="No records" />}</td>
      </tr>
    );
  } else {
    body = rows.map((row) => (
      <tr key={rowKey(row)} className="border-b border-border transition-colors last:border-b-0 even:bg-surface-muted hover:bg-hover">
        {visible.map((c) => (
          <td
            key={c.id}
            className={cn("px-3 py-2.5 align-middle", alignClass(c), c.numeric && "whitespace-nowrap font-mono text-[0.8125rem] tabular", c.className)}
          >
            {c.cell(row)}
          </td>
        ))}
        {rowActions ? (
          <td className="whitespace-nowrap px-3 py-2 text-right align-middle">
            <div className="flex items-center justify-end gap-1.5">{rowActions(row)}</div>
          </td>
        ) : null}
      </tr>
    ));
  }

  return (
    <div className={cn("space-y-3", className)}>
      {toolbar || showColumnMenu ? (
        <div className="flex flex-col gap-2 sm:flex-row sm:items-start">
          <div className="min-w-0 flex-1">{toolbar}</div>
          {showColumnMenu ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline" className="self-start">
                  <Columns3 aria-hidden="true" /> Columns
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent>
                <DropdownMenuLabel>Show columns</DropdownMenuLabel>
                {hideable.map((c) => (
                  <DropdownMenuCheckboxItem
                    key={c.id}
                    checked={!hidden.has(c.id)}
                    onCheckedChange={(checked) => toggleColumn(c.id, checked === true)}
                    onSelect={(e) => e.preventDefault()}
                  >
                    {c.label ?? (typeof c.header === "string" ? c.header : c.id)}
                  </DropdownMenuCheckboxItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          ) : null}
        </div>
      ) : null}

      <div
        role="region"
        aria-labelledby={captionId}
        tabIndex={0}
        className="relative overflow-x-auto rounded-lg border border-border bg-surface shadow-xs focus-visible:outline-2 focus-visible:outline-ring"
      >
        <table className={cn("w-full min-w-[40rem] border-collapse text-sm", fetching && "opacity-70 transition-opacity")} aria-busy={loading || fetching || undefined}>
          <caption id={captionId} className="sr-only">
            {caption}
            {sort ? `, sorted by ${columns.find((c) => c.sortKey === sort.key)?.label ?? sort.key} ${sort.direction === "asc" ? "ascending" : "descending"}` : ""}
          </caption>
          <thead className="border-b border-border">
            <tr>
              {visible.map((c) => {
                const sorted = sort && c.sortKey && sort.key === c.sortKey ? sort.direction : null;
                return (
                  <th
                    key={c.id}
                    scope="col"
                    aria-sort={c.sortKey ? (sorted === "asc" ? "ascending" : sorted === "desc" ? "descending" : "none") : undefined}
                    className={cn(
                      "whitespace-nowrap px-3 py-2.5 text-2xs font-semibold uppercase tracking-wider text-muted-foreground",
                      alignClass(c),
                    )}
                  >
                    {c.sortKey && onSortChange ? (
                      <button
                        type="button"
                        onClick={() =>
                          onSortChange({ key: c.sortKey!, direction: sorted === "asc" ? "desc" : "asc" })
                        }
                        className={cn(
                          "-mx-1 inline-flex items-center gap-1 rounded-sm px-1 uppercase tracking-wider hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring",
                          sorted && "text-foreground",
                        )}
                      >
                        {c.header}
                        {sorted === "asc" ? (
                          <ArrowUp className="size-3" aria-hidden="true" />
                        ) : sorted === "desc" ? (
                          <ArrowDown className="size-3" aria-hidden="true" />
                        ) : (
                          <ArrowUpDown className="size-3 opacity-50" aria-hidden="true" />
                        )}
                      </button>
                    ) : (
                      c.header
                    )}
                  </th>
                );
              })}
              {rowActions ? (
                <th scope="col" className="px-3 py-2.5">
                  <span className="sr-only">Actions</span>
                </th>
              ) : null}
            </tr>
          </thead>
          <tbody>{body}</tbody>
        </table>
      </div>
      {loading ? (
        <span role="status" className="sr-only">
          Loading {itemLabel ?? "records"}…
        </span>
      ) : null}

      {error && rows ? <ErrorState error={error} onRetry={onRetry} className="py-4" /> : null}

      {page && onPageChange && rows ? (
        <Pagination
          meta={page}
          onPageChange={onPageChange}
          pageSizeOptions={pageSizeOptions}
          onPageSizeChange={onPageSizeChange}
          itemLabel={itemLabel}
        />
      ) : null}
    </div>
  );
}
