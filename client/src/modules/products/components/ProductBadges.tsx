import { StatusBadge } from "@/components/data/StatusBadge";
import { Badge } from "@/components/ui/Badge";
import type { Product } from "../types/product.types";

/**
 * BR-I5 pills. Both rely on internal fields, so they only render when the server sent them (callers with
 * PRODUCTS:VIEW_INTERNAL_B2C).
 */
export function B2cPricePill({ product }: { product: Product }) {
  return product.b2cPriceMissing ? <Badge tone="danger">B2C price needed</Badge> : null;
}

export function SameNamePill({ product }: { product: Product }) {
  return product.sameNameAsB2c ? (
    <Badge tone="warning" title="The partner-facing name is the same as the B2C name, so partners could match it to B2C listings.">
      same as B2C
    </Badge>
  ) : null;
}

export function ProductStatusBadge({ product }: { product: Pick<Product, "active"> }) {
  return <StatusBadge domain="product" status={product.active ? "ACTIVE" : "INACTIVE"} />;
}
