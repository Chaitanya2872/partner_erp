import type { ReactNode } from "react";
import type { PageMeta } from "@/types/api.types";

export interface Column<T> {
  key: string;
  header: ReactNode;
  cell: (row: T) => ReactNode;
  className?: string;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  empty?: ReactNode;
  caption?: string;
}

export function DataTable<T>({ columns, rows, rowKey, empty, caption }: DataTableProps<T>) {
  return (
    <div className="scroll">
      <table className="tbl">
        {caption ? <caption className="sr-only">{caption}</caption> : null}
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} scope="col" className={c.className}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={columns.length}>{empty ?? <div className="empty">No records.</div>}</td>
            </tr>
          ) : (
            rows.map((row) => (
              <tr key={rowKey(row)}>
                {columns.map((c) => (
                  <td key={c.key} className={c.className}>
                    {c.cell(row)}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}

export function Pager({ meta, onPage }: { meta: PageMeta; onPage: (page: number) => void }) {
  if (meta.totalPages <= 1) {
    return <div className="pager muted small">{meta.totalElements} record{meta.totalElements === 1 ? "" : "s"}</div>;
  }
  return (
    <nav className="pager" aria-label="Pagination">
      <span className="muted small">
        Page {meta.page + 1} of {meta.totalPages} · {meta.totalElements} records
      </span>
      <button type="button" className="btn sm" disabled={meta.page === 0} onClick={() => onPage(meta.page - 1)}>
        Previous
      </button>
      <button type="button" className="btn sm" disabled={meta.page + 1 >= meta.totalPages} onClick={() => onPage(meta.page + 1)}>
        Next
      </button>
    </nav>
  );
}
