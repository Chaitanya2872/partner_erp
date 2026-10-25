import { useNavigate, useParams } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";
import { ErrorState, LoadingState, PermissionDenied } from "@/components/feedback/States";
import { PageContainer, PageHeader } from "@/components/layout/Page";
import { Alert } from "@/components/ui/Alert";
import { queryKeys } from "@/lib/queryKeys";
import { productService } from "../services/productService";
import { useProduct, useProductMeta } from "../hooks/useProducts";
import { ProductForm } from "../components/ProductForm";
import type { ProductInput } from "../types/product.types";

/** `/products/new` and `/products/:id/edit`. */
export function ProductFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { canWrite, canPerform } = useAuth();
  const meta = useProductMeta();
  const product = useProduct(id);
  const editing = !!id;
  const internal = canPerform("PRODUCTS", "VIEW_INTERNAL_B2C");
  const back = editing ? { to: `/products/${id}`, label: product.data?.sku ?? "Product" } : { to: "/products", label: "Products" };
  const title = editing ? "Edit product" : "New product";

  // The route guard checks read access; saving needs write, and creating needs the internal action because the
  // B2C name is required (P7-04). The server enforces both either way.
  if (!canWrite("PRODUCTS") || (!editing && !internal)) return <PermissionDenied />;
  if (meta.isPending || (editing && product.isPending)) return <LoadingState label="Loading…" />;
  if (meta.isError || (editing && product.isError)) {
    return (
      <PageContainer width="narrow">
        <PageHeader title={title} back={back} />
        <ErrorState error={meta.error ?? product.error} onRetry={() => void Promise.all([meta.refetch(), editing ? product.refetch() : null])} />
      </PageContainer>
    );
  }

  const submit = async (input: ProductInput) => {
    const saved = editing ? await productService.update(id, input) : await productService.create(input);
    toast.success(editing ? `${saved.sku} updated.` : `${saved.sku} created.`);
    await queryClient.invalidateQueries({ queryKey: queryKeys.products.all });
    navigate(`/products/${saved.id}`, { replace: true });
  };

  return (
    <PageContainer width="narrow">
      <PageHeader title={title} back={back} documentTitle={editing ? `Edit ${product.data?.sku}` : title} />
      {editing && !internal ? (
        <Alert tone="info" title="Partner-facing fields only">
          The B2C name, alternative names, B2C price and landed cost need the View internal B2C prices permission. They stay as they are.
        </Alert>
      ) : null}
      <ProductForm meta={meta.data} product={product.data} internal={internal} onSubmit={submit} onCancel={() => navigate(back.to)} />
    </PageContainer>
  );
}
