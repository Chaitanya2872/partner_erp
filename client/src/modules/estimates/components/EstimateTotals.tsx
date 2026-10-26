import type { ReactNode } from "react";
import { cn } from "@/lib/cn";
import { formatCurrency, formatPercent } from "@/utils/format";
import type { Money } from "@/modules/partners/types/partner.types";
import type { Totals } from "../types/estimate.types";

/** Totals block (§6.1) with the CGST/SGST split or one IGST line (D-21). Figures are the server's. */
export function EstimateTotals({ totals, discountPercent, igst, busy, title = "Totals" }: { totals: Totals; discountPercent: Money; igst: boolean; busy?: boolean; title?: string }) {
  const hasDiscount = Number(totals.discountAmount) !== 0;
  return (
    <div className={cn("rounded-lg border border-border bg-surface-sunken p-4 transition-opacity", busy && "opacity-60")} aria-busy={busy || undefined}>
      <h3 className="mb-2 text-sm font-semibold">{title}</h3>
      <dl className="space-y-1 text-sm">
        <Row label="Item total">{formatCurrency(totals.subtotal)}</Row>
        {hasDiscount ? <Row label={`Discount (${formatPercent(discountPercent)})`}>−{formatCurrency(totals.discountAmount)}</Row> : null}
        <Row label="Taxable amount">{formatCurrency(totals.taxableAmount)}</Row>
        {igst ? (
          <Row label="IGST">{formatCurrency(totals.gstAmount)}</Row>
        ) : (
          <>
            <Row label="CGST">{formatCurrency(totals.cgstAmount)}</Row>
            <Row label="SGST">{formatCurrency(totals.sgstAmount)}</Row>
          </>
        )}
        <Row label="Total incl. GST" strong>
          {formatCurrency(totals.grandTotal)}
        </Row>
      </dl>
    </div>
  );
}

function Row({ label, children, strong }: { label: string; children: ReactNode; strong?: boolean }) {
  return (
    <div className={cn("flex justify-between gap-4", strong && "mt-2 border-t border-border pt-2 text-base font-semibold")}>
      <dt className={strong ? undefined : "text-muted-foreground"}>{label}</dt>
      <dd className="font-mono tabular">{children}</dd>
    </div>
  );
}
