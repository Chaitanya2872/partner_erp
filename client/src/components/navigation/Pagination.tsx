import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Input";
import { formatNumber } from "@/utils/format";
import type { PageMeta } from "@/types/api.types";
import { cn } from "@/lib/cn";
import { pageWindow } from "./pageWindow";

interface PaginationProps {
  meta: PageMeta;
  onPageChange: (page: number) => void;
  /** Offer a page-size selector when given. */
  pageSizeOptions?: number[];
  onPageSizeChange?: (size: number) => void;
  /** Noun for the record count ("users", "products"). */
  itemLabel?: string;
  className?: string;
}

/** Server-side pagination control (zero-based pages, as the API returns them). */
export function Pagination({ meta, onPageChange, pageSizeOptions, onPageSizeChange, itemLabel = "records", className }: PaginationProps) {
  const { page, size, totalElements, totalPages } = meta;
  const from = totalElements === 0 ? 0 : page * size + 1;
  const to = Math.min(totalElements, (page + 1) * size);

  return (
    <div className={cn("flex flex-col gap-3 py-3 text-sm sm:flex-row sm:items-center sm:justify-between", className)}>
      <p className="text-muted-foreground" aria-live="polite">
        {totalElements === 0 ? (
          `No ${itemLabel}`
        ) : (
          <>
            Showing <span className="font-medium text-foreground">{formatNumber(from)}–{formatNumber(to)}</span> of{" "}
            <span className="font-medium text-foreground">{formatNumber(totalElements)}</span> {itemLabel}
          </>
        )}
      </p>
      <div className="flex flex-wrap items-center gap-3">
        {pageSizeOptions && onPageSizeChange ? (
          <label className="flex items-center gap-2 text-muted-foreground">
            Rows
            <Select
              aria-label="Rows per page"
              value={size}
              onChange={(e) => onPageSizeChange(Number(e.target.value))}
              className="h-8 w-[4.5rem] text-sm"
            >
              {pageSizeOptions.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </Select>
          </label>
        ) : null}
        {totalPages > 1 ? (
          <nav aria-label="Pagination" className="flex items-center gap-1">
            <Button size="icon-sm" variant="outline" disabled={page === 0} onClick={() => onPageChange(page - 1)} aria-label="Previous page">
              <ChevronLeft aria-hidden="true" />
            </Button>
            <ul className="hidden items-center gap-1 sm:flex">
              {pageWindow(page, totalPages).map((p, i) =>
                p === null ? (
                  <li key={`gap-${i}`} className="px-1 text-muted-foreground" aria-hidden="true">
                    …
                  </li>
                ) : (
                  <li key={p}>
                    <Button
                      size="icon-sm"
                      variant={p === page ? "primary" : "ghost"}
                      aria-current={p === page ? "page" : undefined}
                      aria-label={`Page ${p + 1}`}
                      onClick={() => onPageChange(p)}
                      className="tabular"
                    >
                      {p + 1}
                    </Button>
                  </li>
                ),
              )}
            </ul>
            <span className="px-2 text-muted-foreground sm:hidden">
              Page {page + 1} of {totalPages}
            </span>
            <Button
              size="icon-sm"
              variant="outline"
              disabled={page + 1 >= totalPages}
              onClick={() => onPageChange(page + 1)}
              aria-label="Next page"
            >
              <ChevronRight aria-hidden="true" />
            </Button>
          </nav>
        ) : null}
      </div>
    </div>
  );
}
