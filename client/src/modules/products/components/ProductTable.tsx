import type { ReactNode } from "react";
import { Link } from "react-router";
import { Pencil, Power, PowerOff, Trash2 } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { DataTable, type ColumnDef } from "@/components/tables/DataTable";
import type { SortState } from "@/components/tables/sorting";
import type { PageMeta } from "@/types/api.types";
import { Button } from "@/components/ui/Button";
import { formatCurrency, formatPercent, NOT_AVAILABLE } from "@/utils/format";
import { B2cPricePill, ProductStatusBadge, SameNamePill } from "./ProductBadges";
import type { useProductActions } from "./ProductDialogs";
import type { Product } from "../types/product.types";

const linkClass = "font-mono text-[0.8125rem] font-medium text-accent-ink underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-ring";

interface ProductTableProps {
  rows: Product[] | undefined;
  actions: ReturnType<typeof useProductActions>;
  loading?: boolean;
  fetching?: boolean;
  error?: unknown;
  onRetry?: () => void;
  sort?: SortState | null;
  onSortChange?: (sort: SortState) => void;
  page?: PageMeta;
  onPageChange?: (page: number) => void;
  pageSizeOptions?: number[];
  onPageSizeChange?: (size: number) => void;
  toolbar?: ReactNode;
  empty?: ReactNode;
}

/**
 * Price sheet columns from the source (BR-I5/I6): SKU, partner name (with the B2C name and the pills for internal
 * users), category, HSN, GST, B2C price, partner price, discount. Internal columns are added only with
 * PRODUCTS:VIEW_INTERNAL_B2C; the server leaves those fields out for everyone else, so this is presentation only.
 */
export function ProductTable({ rows, actions, ...table }: ProductTableProps) {
  const { canWrite, canPerform } = useAuth();
  const internal = canPerform("PRODUCTS", "VIEW_INTERNAL_B2C");
  const canEdit = canWrite("PRODUCTS");
  const canDelete = canPerform("PRODUCTS", "DELETE");

  const columns: ColumnDef<Product>[] = [
    {
      id: "sku",
      header: "SKU",
      sortKey: "sku",
      hideable: false,
      cell: (p) => (
        <Link to={`/products/${p.id}`} className={linkClass}>
          {p.sku}
        </Link>
      ),
    },
    {
      id: "name",
      header: "Product",
      sortKey: "partnerName",
      hideable: false,
      cell: (p) => (
        <div className="min-w-48">
          <div className="font-medium">{p.partnerName}</div>
          {internal && p.b2cName ? <div className="text-xs text-muted-foreground">B2C: {p.b2cName}</div> : null}
          {internal && p.sameNameAsB2c ? (
            <div className="mt-1">
              <SameNamePill product={p} />
            </div>
          ) : null}
        </div>
      ),
    },
    { id: "category", header: "Category", sortKey: "category", cell: (p) => p.categoryLabel },
    { id: "unit", header: "Unit", cell: (p) => p.unit, defaultHidden: true },
    { id: "hsn", header: "HSN", cell: (p) => <span className="font-mono text-[0.8125rem]">{p.hsn ?? NOT_AVAILABLE}</span> },
    { id: "gst", header: "GST", numeric: true, cell: (p) => formatPercent(p.gstPercent) },
  ];
  if (internal) {
    columns.push(
      {
        id: "aliases",
        header: "Alternatives",
        defaultHidden: true,
        cell: (p) => <span className="text-xs text-muted-foreground">{p.aliases?.length ?? 0} options</span>,
      },
      { id: "b2c", header: "B2C price", numeric: true, cell: (p) => (p.b2cPriceMissing ? <B2cPricePill product={p} /> : formatCurrency(p.b2cPrice)) },
    );
  }
  columns.push({ id: "price", header: "Partner price", numeric: true, sortKey: "partnerPrice", cell: (p) => formatCurrency(p.partnerPrice) });
  if (internal) {
    columns.push(
      { id: "discount", header: "Discount", numeric: true, cell: (p) => `${p.discountPercent ?? 0}%` },
      { id: "cost", header: "Landed cost", numeric: true, defaultHidden: true, cell: (p) => formatCurrency(p.costPrice) },
    );
  }
  columns.push({ id: "status", header: "Status", cell: (p) => <ProductStatusBadge product={p} /> });

  return (
    <DataTable
      caption="Products"
      itemLabel="products"
      columns={columns}
      rows={rows}
      rowKey={(p) => p.id}
      rowActions={
        canEdit || canDelete
          ? (p) => (
              <div className="flex justify-end gap-1.5">
                {canEdit ? (
                  <>
                    <Button asChild size="sm" variant="outline">
                      <Link to={`/products/${p.id}/edit`} aria-label={`Edit ${p.sku}`}>
                        <Pencil aria-hidden="true" /> Edit
                      </Link>
                    </Button>
                    <Button
                      size="icon-sm"
                      variant="ghost"
                      onClick={() => actions.requestToggle(p)}
                      loading={actions.togglePendingId === p.id}
                      aria-label={p.active ? `Deactivate ${p.sku}` : `Activate ${p.sku}`}
                      title={p.active ? "Deactivate" : "Activate"}
                    >
                      {p.active ? <PowerOff aria-hidden="true" /> : <Power aria-hidden="true" />}
                    </Button>
                  </>
                ) : null}
                {canDelete ? (
                  <Button size="icon-sm" variant="destructive-outline" onClick={() => actions.requestDelete(p)} aria-label={`Delete ${p.sku}`}>
                    <Trash2 aria-hidden="true" />
                  </Button>
                ) : null}
              </div>
            )
          : undefined
      }
      {...table}
    />
  );
}
