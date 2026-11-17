import { useState, type ReactNode } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { productService } from "../services/productService";
import type { Product } from "../types/product.types";

type ProductRef = Pick<Product, "id" | "sku" | "partnerName" | "active" | "version">;

/**
 * Activate, deactivate and delete with their confirmations (D-17). Activation is immediate; deactivation and
 * delete ask first. Pages call the `request…` functions and render `dialogs`.
 */
export function useProductActions({ onDeleted }: { onDeleted?: () => void } = {}) {
  const queryClient = useQueryClient();
  const [deactivateTarget, setDeactivateTarget] = useState<ProductRef | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<ProductRef | null>(null);
  const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.products.all });

  const statusMutation = useMutation({
    mutationFn: ({ product, active }: { product: ProductRef; active: boolean }) => productService.setActive(product.id, active, product.version),
    onSuccess: (updated) => {
      toast.success(updated.active ? `${updated.sku} activated.` : `${updated.sku} deactivated.`);
      setDeactivateTarget(null);
    },
    onError: (error) => {
      toast.error(errorMessage(error));
      setDeactivateTarget(null);
    },
    // A failure may be a stale version: reload either way.
    onSettled: () => refresh(),
  });

  const deleteMutation = useMutation({
    mutationFn: (product: ProductRef) => productService.remove(product.id),
    onSuccess: async (_, product) => {
      toast.success(`${product.sku} deleted.`);
      setDeleteTarget(null);
      onDeleted?.();
      await refresh();
    },
    onError: (error) => {
      toast.error(errorMessage(error));
      setDeleteTarget(null);
    },
  });

  const requestToggle = (product: ProductRef) => {
    if (product.active) setDeactivateTarget(product);
    else statusMutation.mutate({ product, active: true });
  };

  const dialogs: ReactNode = (
    <>
      <ConfirmDialog
        open={!!deactivateTarget}
        title="Deactivate product?"
        confirmLabel="Deactivate product"
        busy={statusMutation.isPending}
        onCancel={() => setDeactivateTarget(null)}
        onConfirm={() => deactivateTarget && statusMutation.mutate({ product: deactivateTarget, active: false })}
      >
        {deactivateTarget ? (
          <p>
            {deactivateTarget.sku} · {deactivateTarget.partnerName} stays on the price sheet and on existing documents, but can&apos;t be added to
            new estimates or invoices until it is activated again.
          </p>
        ) : null}
      </ConfirmDialog>

      <ConfirmDialog
        open={!!deleteTarget}
        title="Delete product?"
        confirmLabel="Delete product"
        danger
        busy={deleteMutation.isPending}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget)}
      >
        {deleteTarget ? (
          <p>
            Delete {deleteTarget.sku} · {deleteTarget.partnerName} permanently? This is only possible while no estimate or invoice uses it;
            otherwise deactivate it instead.
          </p>
        ) : null}
      </ConfirmDialog>
    </>
  );

  return {
    requestToggle,
    requestDelete: (product: ProductRef) => setDeleteTarget(product),
    togglePendingId: statusMutation.isPending ? statusMutation.variables?.product.id : undefined,
    dialogs,
  };
}
