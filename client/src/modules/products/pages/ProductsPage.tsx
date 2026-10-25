import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { LockKeyhole } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useDebounce } from "@/hooks/useDebounce";
import { DataTable, type ColumnDef } from "@/components/tables/DataTable";
import { toSortParam, type SortState } from "@/components/tables/sorting";
import { EmptyState } from "@/components/feedback/States";
import { PageContainer, PageHeader, Toolbar } from "@/components/layout/Page";
import { SearchInput } from "@/components/forms/SearchInput";
import { Select } from "@/components/ui/Input";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { formatCurrency, formatPercent } from "@/utils/format";
import { queryKeys } from "@/lib/queryKeys";
import { productService } from "../services/productService";
import { PRODUCT_CATEGORIES, type Product, type ProductCategory } from "../types/product.types";

const PAGE_SIZES = [25, 50, 100];

/**
 * Read-only price sheet (maintenance arrives in Phase 7). Internal B2C columns appear only for users with
 * PRODUCTS:VIEW_INTERNAL_B2C; the server omits those fields for everyone else, so hiding them here is
 * presentation, not protection.
 */
export function ProductsPage() {
  const { canPerform } = useAuth();
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState<ProductCategory | "">("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(50);
  const [sort, setSort] = useState<SortState | null>(null);
  const q = useDebounce(search.trim());
  const internal = canPerform("PRODUCTS", "VIEW_INTERNAL_B2C");

  const filters = { q, category, page, size, sort: toSortParam(sort) };
  const products = useQuery({
    queryKey: queryKeys.products.list(filters),
    queryFn: ({ signal }) => productService.list(filters, signal),
    placeholderData: keepPreviousData,
  });

  const columns: ColumnDef<Product>[] = [
    { id: "sku", header: "SKU", sortKey: "sku", hideable: false, cell: (p) => <span className="font-mono text-[0.8125rem]">{p.sku}</span> },
    {
      id: "name",
      header: "Product",
      sortKey: "partnerName",
      hideable: false,
      cell: (p) => (
        <div className="min-w-48">
          <div className="font-medium">{p.partnerName}</div>
          {internal && p.b2cName ? <div className="text-xs text-muted-foreground">B2C: {p.b2cName}</div> : null}
        </div>
      ),
    },
    { id: "category", header: "Category", sortKey: "category", cell: (p) => p.categoryLabel },
    { id: "unit", header: "Unit", cell: (p) => p.unit, defaultHidden: true },
    { id: "hsn", header: "HSN", cell: (p) => <span className="font-mono text-[0.8125rem]">{p.hsn ?? "—"}</span>, defaultHidden: true },
    { id: "price", header: "Partner price", numeric: true, sortKey: "partnerPrice", cell: (p) => formatCurrency(p.partnerPrice) },
    { id: "gst", header: "GST", numeric: true, cell: (p) => formatPercent(p.gstPercent) },
  ];
  if (internal) {
    columns.push(
      { id: "b2c", header: "B2C price", numeric: true, cell: (p) => formatCurrency(p.b2cPrice) },
      { id: "cost", header: "Landed cost", numeric: true, cell: (p) => formatCurrency(p.costPrice) },
    );
  }

  const filtered = !!q || !!category;
  const resetPage = () => setPage(0);

  return (
    <PageContainer>
      <PageHeader
        title="Products"
        description="Price sheet · read only. Product maintenance and CSV import arrive in Phase 7."
        badge={
          internal ? (
            <Badge tone="accent">
              <LockKeyhole aria-hidden="true" /> Internal B2C view
            </Badge>
          ) : undefined
        }
      />
      <DataTable
        caption="Products"
        itemLabel="products"
        columns={columns}
        rows={products.data?.content}
        rowKey={(p) => p.id}
        loading={products.isPending}
        fetching={products.isFetching && !products.isPending}
        error={products.error}
        onRetry={() => products.refetch()}
        sort={sort}
        onSortChange={(next) => {
          setSort(next);
          resetPage();
        }}
        page={products.data?.meta}
        onPageChange={setPage}
        pageSizeOptions={PAGE_SIZES}
        onPageSizeChange={(next) => {
          setSize(next);
          resetPage();
        }}
        empty={
          <EmptyState
            title={filtered ? "No products match" : "The price sheet is empty"}
            action={
              filtered ? (
                <Button
                  size="sm"
                  onClick={() => {
                    setSearch("");
                    setCategory("");
                    resetPage();
                  }}
                >
                  Clear filters
                </Button>
              ) : undefined
            }
          >
            {filtered ? "Try another search or category." : undefined}
          </EmptyState>
        }
        toolbar={
          <Toolbar label="Product filters">
            <SearchInput
              placeholder={internal ? "Search SKU, product or B2C name" : "Search SKU or product"}
              aria-label="Search products"
              value={search}
              onValueChange={(value) => {
                setSearch(value);
                resetPage();
              }}
            />
            <Select
              aria-label="Filter by category"
              value={category}
              wrapperClassName="sm:w-56"
              onChange={(e) => {
                setCategory(e.target.value as ProductCategory | "");
                resetPage();
              }}
            >
              <option value="">All categories</option>
              {PRODUCT_CATEGORIES.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </Select>
          </Toolbar>
        }
      />
    </PageContainer>
  );
}
