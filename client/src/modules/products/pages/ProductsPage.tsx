import { useState } from "react";
import { Link } from "react-router";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { Download, LockKeyhole, Plus, Upload } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useDebounce } from "@/hooks/useDebounce";
import { toSortParam, type SortState } from "@/components/tables/sorting";
import { EmptyState } from "@/components/feedback/States";
import { PageActions, PageContainer, PageHeader, Toolbar } from "@/components/layout/Page";
import { SearchInput } from "@/components/forms/SearchInput";
import { Select } from "@/components/ui/Input";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { saveTextFile } from "@/utils/download";
import { productService } from "../services/productService";
import { useProductActions } from "../components/ProductDialogs";
import { ProductTable } from "../components/ProductTable";
import { ImportDialog } from "../components/ImportDialog";
import { PRODUCT_CATEGORIES, type ProductCategory } from "../types/product.types";

const PAGE_SIZES = [25, 50, 100];

/**
 * Item & price sheet. Internal B2C columns, the B2C filter, creating and importing appear only for users with
 * PRODUCTS:VIEW_INTERNAL_B2C; export follows PRODUCTS:EXPORT. The server enforces every one of these, so hiding
 * them here is presentation, not protection.
 */
export function ProductsPage() {
  const { canWrite, canPerform } = useAuth();
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState<ProductCategory | "">("");
  const [active, setActive] = useState("");
  const [b2cMissing, setB2cMissing] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(50);
  const [sort, setSort] = useState<SortState | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [exporting, setExporting] = useState(false);
  const q = useDebounce(search.trim());
  const internal = canPerform("PRODUCTS", "VIEW_INTERNAL_B2C");
  const canEdit = canWrite("PRODUCTS");
  const canCreate = canEdit && internal;
  const canExport = canPerform("PRODUCTS", "EXPORT");

  const filters = { q, category, active, b2cMissing: internal ? b2cMissing : "", page, size, sort: toSortParam(sort) };
  const products = useQuery({
    queryKey: queryKeys.products.list(filters),
    queryFn: ({ signal }) => productService.list(filters, signal),
    placeholderData: keepPreviousData,
  });
  const actions = useProductActions();

  /** Every filter change starts again from the first page. */
  const set =
    <T,>(setter: (value: T) => void) =>
    (value: T) => {
      setter(value);
      setPage(0);
    };
  const filtered = !!q || !!category || !!active || (internal && !!b2cMissing);

  const exportCsv = async () => {
    setExporting(true);
    try {
      const { text, filename } = await productService.exportCsv({ q, category, active, b2cMissing: filters.b2cMissing });
      saveTextFile(text, filename);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setExporting(false);
    }
  };

  const headerActions =
    canCreate || canExport ? (
      <PageActions>
        {canExport ? (
          <Button onClick={exportCsv} loading={exporting} loadingText="Exporting…">
            <Download aria-hidden="true" /> Export CSV
          </Button>
        ) : null}
        {canCreate ? (
          <>
            <Button onClick={() => setImportOpen(true)}>
              <Upload aria-hidden="true" /> Import CSV
            </Button>
            <Button asChild variant="primary">
              <Link to="/products/new">
                <Plus aria-hidden="true" /> New product
              </Link>
            </Button>
          </>
        ) : null}
      </PageActions>
    ) : undefined;

  return (
    <PageContainer>
      <PageHeader
        title="Products"
        description={
          internal
            ? "One master: B2C name and price for internal use, partner name and price for partner documents."
            : "Partner price sheet."
        }
        badge={
          internal ? (
            <Badge tone="accent">
              <LockKeyhole aria-hidden="true" /> Internal B2C view
            </Badge>
          ) : undefined
        }
        actions={headerActions}
      />
      <ProductTable
        rows={products.data?.content}
        actions={actions}
        loading={products.isPending}
        fetching={products.isFetching && !products.isPending}
        error={products.error}
        onRetry={() => products.refetch()}
        sort={sort}
        onSortChange={set(setSort)}
        page={products.data?.meta}
        onPageChange={setPage}
        pageSizeOptions={PAGE_SIZES}
        onPageSizeChange={set(setSize)}
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
                    setActive("");
                    setB2cMissing("");
                    setPage(0);
                  }}
                >
                  Clear filters
                </Button>
              ) : undefined
            }
          >
            {filtered ? "Try another search or filter." : undefined}
          </EmptyState>
        }
        toolbar={
          <Toolbar label="Product filters">
            <SearchInput
              placeholder={internal ? "Search SKU, product, B2C name or category" : "Search SKU, product or category"}
              aria-label="Search products"
              value={search}
              onValueChange={set(setSearch)}
            />
            <Select aria-label="Filter by category" value={category} wrapperClassName="sm:w-56" onChange={(e) => set(setCategory)(e.target.value as ProductCategory | "")}>
              <option value="">All categories</option>
              {PRODUCT_CATEGORIES.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </Select>
            <Select aria-label="Filter by status" value={active} wrapperClassName="sm:w-40" onChange={(e) => set(setActive)(e.target.value)}>
              <option value="">All statuses</option>
              <option value="true">Active</option>
              <option value="false">Inactive</option>
            </Select>
            {internal ? (
              <Select aria-label="Filter by B2C price" value={b2cMissing} wrapperClassName="sm:w-48" onChange={(e) => set(setB2cMissing)(e.target.value)}>
                <option value="">Any B2C price</option>
                <option value="true">B2C price needed</option>
                <option value="false">B2C price set</option>
              </Select>
            ) : null}
          </Toolbar>
        }
      />
      {actions.dialogs}
      {canCreate ? <ImportDialog open={importOpen} onOpenChange={setImportOpen} /> : null}
    </PageContainer>
  );
}
