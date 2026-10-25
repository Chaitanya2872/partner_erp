import { Link, useNavigate, useParams } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { LockKeyhole, Pencil, Power, PowerOff, Trash2 } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { ErrorState, LoadingState } from "@/components/feedback/States";
import { ContentCard, PageActions, PageContainer, PageHeader } from "@/components/layout/Page";
import { DescriptionList, DetailRow, Timeline, type TimelineEntry } from "@/components/data/DataDisplay";
import { Alert } from "@/components/ui/Alert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { queryKeys } from "@/lib/queryKeys";
import { formatCurrency, formatDateTime, formatPercent, NOT_AVAILABLE } from "@/utils/format";
import { productService } from "../services/productService";
import { useProduct } from "../hooks/useProducts";
import { useProductActions } from "../components/ProductDialogs";
import { B2cPricePill, ProductStatusBadge, SameNamePill } from "../components/ProductBadges";
import { describeChange, HISTORY_TITLES } from "../utils/productDisplay";
import type { ProductHistoryEntry } from "../types/product.types";

const HISTORY_TONE: Record<ProductHistoryEntry["action"], TimelineEntry["tone"]> = {
  PRODUCT_CREATED: "success",
  PRODUCT_UPDATED: "info",
  PRODUCT_STATUS_CHANGED: "warning",
  PRODUCT_DELETED: "danger",
  PRODUCT_IMPORTED: "info",
};

export function ProductDetailsPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const { canWrite, canPerform } = useAuth();
  const product = useProduct(id);
  const history = useQuery({
    queryKey: queryKeys.products.history(id),
    queryFn: ({ signal }) => productService.history(id, signal),
  });
  const actions = useProductActions({ onDeleted: () => navigate("/products", { replace: true }) });

  if (product.isPending) return <LoadingState label="Loading product…" />;
  if (product.isError) {
    return (
      <PageContainer>
        <PageHeader title="Product" back={{ to: "/products", label: "Products" }} />
        <ErrorState error={product.error} onRetry={() => product.refetch()} />
      </PageContainer>
    );
  }

  const p = product.data;
  const internal = canPerform("PRODUCTS", "VIEW_INTERNAL_B2C");
  const canEdit = canWrite("PRODUCTS");
  const canDelete = canPerform("PRODUCTS", "DELETE");
  const gross = (p.partnerPrice * (100 + p.gstPercent)) / 100;

  return (
    <PageContainer>
      <PageHeader
        title={p.partnerName}
        back={{ to: "/products", label: "Products" }}
        documentTitle={p.sku}
        badge={
          <>
            <ProductStatusBadge product={p} />
            {internal ? <SameNamePill product={p} /> : null}
          </>
        }
        description={`${p.sku} · ${p.categoryLabel}`}
        actions={
          canEdit || canDelete ? (
            <PageActions>
              {canEdit ? (
                <Button onClick={() => actions.requestToggle(p)} loading={actions.togglePendingId === p.id}>
                  {p.active ? <PowerOff aria-hidden="true" /> : <Power aria-hidden="true" />} {p.active ? "Deactivate" : "Activate"}
                </Button>
              ) : null}
              {canDelete ? (
                <Button variant="destructive-outline" onClick={() => actions.requestDelete(p)}>
                  <Trash2 aria-hidden="true" /> Delete
                </Button>
              ) : null}
              {canEdit ? (
                <Button asChild variant="primary">
                  <Link to={`/products/${p.id}/edit`}>
                    <Pencil aria-hidden="true" /> Edit
                  </Link>
                </Button>
              ) : null}
            </PageActions>
          ) : undefined
        }
      />

      {!p.active ? (
        <Alert tone="warning" title="Inactive product">
          It stays on existing documents but can&apos;t be added to new estimates or invoices.
        </Alert>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-2">
        <ContentCard title="Partner-facing" description="What partner documents show.">
          <DescriptionList>
            <DetailRow label="Name">{p.partnerName}</DetailRow>
            <DetailRow label="Partner price">{formatCurrency(p.partnerPrice)} excl. GST</DetailRow>
            <DetailRow label="GST">{formatPercent(p.gstPercent)}</DetailRow>
            <DetailRow label="Partner price incl. GST">{formatCurrency(gross)}</DetailRow>
            <DetailRow label="Unit">{p.unit}</DetailRow>
            <DetailRow label="HSN / SAC" mono>
              {p.hsn ?? NOT_AVAILABLE}
            </DetailRow>
            <DetailRow label="Category">{p.categoryLabel}</DetailRow>
          </DescriptionList>
        </ContentCard>

        {internal ? (
          <ContentCard
            title={
              <span className="inline-flex items-center gap-2">
                Internal B2C <Badge tone="accent"><LockKeyhole aria-hidden="true" /> Internal</Badge>
              </span>
            }
            description="Never shown on partner documents."
          >
            <DescriptionList>
              <DetailRow label="B2C name">{p.b2cName ?? NOT_AVAILABLE}</DetailRow>
              <DetailRow label="B2C price">{p.b2cPriceMissing ? <B2cPricePill product={p} /> : `${formatCurrency(p.b2cPrice)} excl. GST`}</DetailRow>
              <DetailRow label="Partner discount">{p.b2cPriceMissing ? NOT_AVAILABLE : `${p.discountPercent ?? 0}%`}</DetailRow>
              <DetailRow label="Landed cost">{p.costPrice === null || p.costPrice === undefined ? NOT_AVAILABLE : formatCurrency(p.costPrice)}</DetailRow>
              <DetailRow label="Alternative names">
                {p.aliases && p.aliases.length > 0 ? (
                  <ul className="space-y-0.5">
                    {p.aliases.map((a) => (
                      <li key={a}>{a}</li>
                    ))}
                  </ul>
                ) : (
                  NOT_AVAILABLE
                )}
              </DetailRow>
            </DescriptionList>
          </ContentCard>
        ) : null}

        <ContentCard title="History" description={internal ? undefined : "Changes to internal B2C fields are not shown."}>
          {history.isPending ? (
            <LoadingState label="Loading history…" />
          ) : history.isError ? (
            <ErrorState error={history.error} onRetry={() => history.refetch()} />
          ) : (
            <Timeline
              entries={history.data.map((h) => {
                const created = h.action === "PRODUCT_CREATED" || (h.action === "PRODUCT_IMPORTED" && h.changes.every((c) => c.from === null));
                return {
                  id: h.id,
                  tone: HISTORY_TONE[h.action],
                  title: h.action === "PRODUCT_IMPORTED" && created ? "Created by CSV import" : HISTORY_TITLES[h.action],
                  time: `${formatDateTime(h.at)}${h.actorName ? ` · ${h.actorName}` : ""}`,
                  description:
                    h.changes.length > 0 && !created ? (
                      <ul className="space-y-0.5">
                        {h.changes.map((c) => (
                          <li key={c.field}>{describeChange(c, false)}</li>
                        ))}
                      </ul>
                    ) : undefined,
                };
              })}
            />
          )}
        </ContentCard>
      </div>

      <p className="text-xs text-muted-foreground">Updated {formatDateTime(p.updatedAt)}</p>

      {actions.dialogs}
    </PageContainer>
  );
}
