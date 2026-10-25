import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Search } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useDebounce } from "@/hooks/useDebounce";
import { DataTable, Pager, type Column } from "@/components/tables/DataTable";
import { EmptyState, ErrorState } from "@/components/common/EmptyState";
import { LoadingSpinner } from "@/components/common/LoadingSpinner";
import { formatCurrency } from "@/utils/formatCurrency";
import { productService } from "../services/productService";
import { PRODUCT_CATEGORIES, type Product, type ProductCategory } from "../types/product.types";

/**
 * Read-only price sheet (maintenance arrives in Phase 7). Internal B2C columns appear only for users with
 * PRODUCTS:VIEW_INTERNAL_B2C; the server omits those fields for everyone else.
 */
export function ProductsPage() {
  const { canPerform } = useAuth();
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState<ProductCategory | "">("");
  const [page, setPage] = useState(0);
  const q = useDebounce(search.trim());
  const internal = canPerform("PRODUCTS", "VIEW_INTERNAL_B2C");

  const products = useQuery({
    queryKey: ["products", { q, category, page }],
    queryFn: () => productService.list({ q, category, page, size: 50 }),
    placeholderData: keepPreviousData,
  });

  const columns: Column<Product>[] = [
    { key: "sku", header: "SKU", cell: (p) => <span className="mono">{p.sku}</span> },
    {
      key: "name",
      header: "Product",
      cell: (p) => (
        <div>
          <div style={{ fontWeight: 600 }}>{p.partnerName}</div>
          {internal && p.b2cName ? <div className="muted small">B2C: {p.b2cName}</div> : null}
        </div>
      ),
    },
    { key: "category", header: "Category", cell: (p) => p.categoryLabel },
    { key: "price", header: "Partner price", className: "r", cell: (p) => formatCurrency(p.partnerPrice) },
    { key: "gst", header: "GST", className: "r", cell: (p) => `${p.gstPercent}%` },
  ];
  if (internal) {
    columns.push(
      { key: "b2c", header: "B2C price", className: "r", cell: (p) => formatCurrency(p.b2cPrice) },
      { key: "cost", header: "Landed cost", className: "r", cell: (p) => formatCurrency(p.costPrice) },
    );
  }

  return (
    <>
      <div className="top">
        <div>
          <h1>Products</h1>
          <p>Price sheet · read only (product maintenance arrives in Phase 7).</p>
        </div>
        {internal ? <span className="pill gold">Internal B2C view</span> : null}
      </div>
      <div className="tools" role="search">
        <Search size={16} aria-hidden="true" className="muted" />
        <input
          type="search"
          placeholder={internal ? "Search SKU, product or B2C name" : "Search SKU or product"}
          aria-label="Search products"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
        />
        <select
          aria-label="Filter by category"
          value={category}
          onChange={(e) => {
            setCategory(e.target.value as ProductCategory | "");
            setPage(0);
          }}
        >
          <option value="">All categories</option>
          {PRODUCT_CATEGORIES.map((c) => (
            <option key={c.value} value={c.value}>
              {c.label}
            </option>
          ))}
        </select>
      </div>
      {products.isPending ? (
        <LoadingSpinner label="Loading products…" />
      ) : products.isError ? (
        <ErrorState error={products.error} onRetry={() => products.refetch()} />
      ) : (
        <>
          <DataTable
            caption="Products"
            columns={columns}
            rows={products.data.content}
            rowKey={(p) => p.id}
            empty={<EmptyState title="No products match" />}
          />
          <Pager meta={products.data.meta} onPage={setPage} />
        </>
      )}
    </>
  );
}
