import type { ReactNode } from "react";
import { StatusBadge } from "@/components/data/StatusBadge";
import { Alert } from "@/components/ui/Alert";
import { cn } from "@/lib/cn";
import { formatCurrency, formatPercent } from "@/utils/format";
import type { Money } from "@/modules/partners/types/partner.types";
import type { EstimateComparison, InvoiceLine, InvoiceStatus, InvoiceTotals, PaymentStatus, TaxTreatment } from "../types/invoice.types";

/** Stored status for drafts and cancelled invoices; issued invoices show their derived payment status (BR-V6). */
export function InvoiceStatusBadge({ status, paymentStatus }: { status: InvoiceStatus; paymentStatus: PaymentStatus | null }) {
  if (status === "ISSUED" && paymentStatus) return <StatusBadge domain="invoice" status={paymentStatus} />;
  return <StatusBadge domain="invoiceStatus" status={status} />;
}

/**
 * Totals with the tax split the server applied: CGST + SGST/UTGST (intra-state) or IGST (inter-state). Without a
 * tax type the draft carries no tax yet, and says so rather than guessing.
 */
export function InvoiceTotalsCard({
  totals,
  tax,
  discountPercent,
  busy,
  title = "Totals",
}: {
  totals: InvoiceTotals;
  tax: Pick<TaxTreatment, "taxType" | "stateTaxLabel">;
  discountPercent: Money;
  busy?: boolean;
  title?: string;
}) {
  const hasDiscount = Number(totals.discountAmount) !== 0;
  return (
    <div className={cn("rounded-lg border border-border bg-surface-sunken p-4 transition-opacity", busy && "opacity-60")} aria-busy={busy || undefined}>
      <h3 className="mb-2 text-sm font-semibold">{title}</h3>
      <dl className="space-y-1 text-sm">
        <Row label="Item total">{formatCurrency(totals.subtotal)}</Row>
        {hasDiscount ? <Row label={`Discount (${formatPercent(discountPercent)})`}>−{formatCurrency(totals.discountAmount)}</Row> : null}
        <Row label="Taxable value">{formatCurrency(totals.taxableAmount)}</Row>
        {tax.taxType === "INTER" ? (
          <Row label="IGST">{formatCurrency(totals.igstAmount)}</Row>
        ) : tax.taxType === "INTRA" ? (
          <>
            <Row label="CGST">{formatCurrency(totals.cgstAmount)}</Row>
            <Row label={tax.stateTaxLabel ?? "SGST"}>{formatCurrency(totals.sgstAmount)}</Row>
          </>
        ) : (
          <Row label="GST">Set the place of supply</Row>
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

/** Per-line tax breakdown: every line keeps its taxable value, rate, components, tax and total. */
export function InvoiceLinesTable({ lines, tax, caption = "Invoice items" }: { lines: InvoiceLine[]; tax: Pick<TaxTreatment, "taxType" | "stateTaxLabel">; caption?: string }) {
  const intra = tax.taxType === "INTRA";
  const inter = tax.taxType === "INTER";
  return (
    <div className="relative overflow-x-auto rounded-lg border border-border">
      <table className="w-full min-w-[860px] text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead className="bg-surface-sunken text-left text-xs text-muted-foreground">
          <tr>
            <th scope="col" className="px-3 py-2 font-medium">Item</th>
            <th scope="col" className="px-3 py-2 font-medium">HSN/SAC</th>
            <th scope="col" className="px-3 py-2 text-right font-medium">Qty</th>
            <th scope="col" className="px-3 py-2 text-right font-medium">Rate</th>
            <th scope="col" className="px-3 py-2 text-right font-medium">Taxable</th>
            <th scope="col" className="px-3 py-2 text-right font-medium">GST</th>
            {intra ? (
              <>
                <th scope="col" className="px-3 py-2 text-right font-medium">CGST</th>
                <th scope="col" className="px-3 py-2 text-right font-medium">{tax.stateTaxLabel ?? "SGST"}</th>
              </>
            ) : null}
            {inter ? <th scope="col" className="px-3 py-2 text-right font-medium">IGST</th> : null}
            <th scope="col" className="px-3 py-2 text-right font-medium">Tax</th>
            <th scope="col" className="px-3 py-2 text-right font-medium">Total</th>
          </tr>
        </thead>
        <tbody>
          {lines.map((l) => (
            <tr key={l.id ?? l.lineNo} className="border-t border-border align-top">
              <td className="px-3 py-2">
                <div className="font-medium">{l.description}</div>
                <div className="text-xs text-muted-foreground">{[l.sku, l.unit].filter(Boolean).join(" · ")}</div>
              </td>
              <td className="px-3 py-2 font-mono text-xs">{l.hsn ?? "—"}</td>
              <Num>{String(Number(l.quantity))}</Num>
              <Num>{formatCurrency(l.unitPrice)}</Num>
              <Num>{formatCurrency(l.taxableValue)}</Num>
              <Num>{formatPercent(l.gstPercent)}</Num>
              {intra ? (
                <>
                  <Num>{formatCurrency(l.cgstAmount)}</Num>
                  <Num>{formatCurrency(l.sgstAmount)}</Num>
                </>
              ) : null}
              {inter ? <Num>{formatCurrency(l.igstAmount)}</Num> : null}
              <Num>{formatCurrency(l.taxAmount)}</Num>
              <Num strong>{formatCurrency(l.lineTotal)}</Num>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Num({ children, strong }: { children: ReactNode; strong?: boolean }) {
  return <td className={cn("px-3 py-2 text-right font-mono tabular whitespace-nowrap", strong && "font-semibold")}>{children}</td>;
}

/** What the server concluded about the tax, what is missing, and what needs an authorised confirmation. */
export function TaxTreatmentNotice({ tax }: { tax: TaxTreatment }) {
  if (tax.missing.length > 0) {
    return (
      <Alert tone="info" title="Tax treatment not decided yet">
        Set the place of supply and its basis{tax.missing.includes("supplierState") ? ", and complete the GST registration in Settings" : ""}. The tax is never
        guessed from the billing address.
      </Alert>
    );
  }
  if (!tax.confirmationRequired) return null;
  return (
    <Alert tone={tax.confirmed ? "success" : "warning"} title={tax.confirmed ? "Tax treatment confirmed" : "Tax treatment needs confirmation"}>
      <ul className="list-disc space-y-0.5 pl-5">
        {tax.reasons.map((r) => (
          <li key={r.code}>{r.message}</li>
        ))}
      </ul>
      {tax.confirmed ? (
        <p className="mt-1">
          Confirmed{tax.confirmedByName ? ` by ${tax.confirmedByName}` : ""}
          {tax.confirmationNote ? `: “${tax.confirmationNote}”` : "."}
        </p>
      ) : (
        <p className="mt-1">Someone with Invoices approve must confirm the {tax.taxTypeLabel ?? "tax"} treatment before the invoice can be issued.</p>
      )}
    </Alert>
  );
}

/** The estimate's agreed totals against the invoice's per-line tax, shown before issue (Phase 9 decision 2). */
export function EstimateComparisonNotice({ comparison, taxDecided = true }: { comparison: EstimateComparison; taxDecided?: boolean }) {
  const diff = Number(comparison.grandTotalDifference);
  if (!taxDecided) {
    return (
      <Alert tone="info" title={`From estimate ${comparison.estimateNumber}`}>
        Estimate total {formatCurrency(comparison.estimateGrandTotal)} incl. GST {formatCurrency(comparison.estimateGst)}. GST is added to this invoice once the place of
        supply is set; any difference from the estimate is then shown here.
      </Alert>
    );
  }
  if (diff === 0 && !comparison.taxTypeChanged) {
    return (
      <Alert tone="info" title={`Matches estimate ${comparison.estimateNumber}`}>
        The invoice total equals the estimate total of {formatCurrency(comparison.estimateGrandTotal)}.
      </Alert>
    );
  }
  return (
    <Alert tone="warning" title={`Differs from estimate ${comparison.estimateNumber}`}>
      Estimate total {formatCurrency(comparison.estimateGrandTotal)} (GST {formatCurrency(comparison.estimateGst)}); invoice difference{" "}
      {diff > 0 ? "+" : diff < 0 ? "−" : ""}
      {formatCurrency(Math.abs(diff))} (GST {formatCurrency(comparison.gstDifference)}).
      {comparison.taxTypeChanged ? ` The estimate used ${comparison.estimateIgst ? "IGST" : "CGST + SGST"}; the place of supply gives a different tax type.` : ""}
      {!comparison.taxTypeChanged && diff !== 0 ? " The invoice rounds GST per line; the estimate rounded it once over the whole document." : ""}
    </Alert>
  );
}
