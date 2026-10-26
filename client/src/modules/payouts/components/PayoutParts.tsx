import { Link } from "react-router";
import { StatusBadge } from "@/components/data/StatusBadge";
import { DescriptionList, DetailRow } from "@/components/data/DataDisplay";
import { cn } from "@/lib/cn";
import { formatCurrency, formatDate, formatPercent, NOT_AVAILABLE } from "@/utils/format";
import type { Money } from "@/modules/partners/types/partner.types";
import type { PayoutLine, PayoutStatus, PayoutTds } from "../types/payout.types";

export const linkClass = "font-medium text-accent-ink underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-ring";

export function PayoutStatusBadge({ status }: { status: PayoutStatus }) {
  return <StatusBadge domain="payout" status={status} />;
}

/** Signed amount: negatives (deductions) carry a minus sign in the danger colour. */
export function SignedAmount({ amount, className }: { amount: Money | null | undefined; className?: string }) {
  if (amount === null || amount === undefined) return <span className={cn("font-mono tabular", className)}>{NOT_AVAILABLE}</span>;
  const negative = Number(amount) < 0;
  return (
    <span className={cn("whitespace-nowrap font-mono tabular", negative && "text-danger", className)}>
      {negative ? `−${formatCurrency(String(amount).replace("-", ""))}` : formatCurrency(amount)}
    </span>
  );
}

const TDS_STATUS: Record<string, string> = {
  CALCULATED: "Calculated",
  CONFIG_INCOMPLETE: "Settings not confirmed",
  PAN_NOT_VERIFIED: "PAN not verified",
  LEGACY: "Carried over (Phase 2)",
};

/** TDS breakdown: the rule applied, the financial-year position and the amount (with any manual adjustment). */
export function TdsBreakdown({ tds, commission, fyLabel }: { tds: PayoutTds; commission: Money; fyLabel: string }) {
  return (
    <DescriptionList columns={2}>
      <DetailRow label="Status">{TDS_STATUS[tds.status] ?? tds.status}</DetailRow>
      <DetailRow label="Section · rate">
        {tds.sectionCode ?? NOT_AVAILABLE} · {tds.ratePercent !== null ? formatPercent(tds.ratePercent) : NOT_AVAILABLE}
      </DetailRow>
      <DetailRow label="Financial-year threshold">{tds.fyThreshold !== null ? formatCurrency(tds.fyThreshold) : NOT_AVAILABLE}</DetailRow>
      <DetailRow label="PAN verified">{tds.panVerified ? "Yes" : "No"}</DetailRow>
      <DetailRow label={`Earlier commission in ${fyLabel}`}>{tds.priorFyCommission !== null ? formatCurrency(tds.priorFyCommission) : NOT_AVAILABLE}</DetailRow>
      <DetailRow label="Of which TDS already calculated on">{tds.priorFyTdsBase !== null ? formatCurrency(tds.priorFyTdsBase) : NOT_AVAILABLE}</DetailRow>
      <DetailRow label="This payout's commission">
        <SignedAmount amount={commission} />
      </DetailRow>
      <DetailRow label="TDS base">{tds.tdsBase !== null ? formatCurrency(tds.tdsBase) : NOT_AVAILABLE}</DetailRow>
      <DetailRow label="TDS calculated">{tds.computedTds !== null ? formatCurrency(tds.computedTds) : NOT_AVAILABLE}</DetailRow>
      <DetailRow label="TDS deducted">
        {tds.tdsAmount !== null ? formatCurrency(tds.tdsAmount) : NOT_AVAILABLE}
        {tds.adjusted ? <span className="ml-1 text-xs text-warning-ink">(adjusted by hand)</span> : null}
      </DetailRow>
    </DescriptionList>
  );
}

/** Commission lines and carried adjustments of a payout or preview. */
export function PayoutLinesTable({ lines, caption, canOpenPayments, canOpenInvoices }: { lines: PayoutLine[]; caption: string; canOpenPayments: boolean; canOpenInvoices: boolean }) {
  if (lines.length === 0) return <p className="py-4 text-sm text-muted-foreground">No lines.</p>;
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[40rem] text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
            <th scope="col" className="py-2 pr-3 font-medium">Source</th>
            <th scope="col" className="py-2 pr-3 font-medium">Invoice</th>
            <th scope="col" className="py-2 pr-3 text-right font-medium">Excl. GST</th>
            <th scope="col" className="py-2 pr-3 text-right font-medium">Rate</th>
            <th scope="col" className="py-2 text-right font-medium">Amount</th>
          </tr>
        </thead>
        <tbody>
          {lines.map((l, i) => (
            <tr key={l.id ?? `${l.paymentId ?? l.adjustmentId}-${i}`} className="border-b border-border/60 align-top last:border-0">
              <td className="py-2 pr-3">
                {l.lineType === "COMMISSION" ? (
                  <>
                    {canOpenPayments && l.paymentId ? (
                      <Link to={`/payments/${l.paymentId}`} className={`font-mono ${linkClass}`}>
                        {l.paymentNumber}
                      </Link>
                    ) : (
                      <span className="font-mono">{l.paymentNumber}</span>
                    )}
                    <div className="text-xs text-muted-foreground">
                      Received {formatDate(l.paymentDate)}
                      {l.sourceReversed ? <span className="ml-1 font-medium text-danger">· reversed</span> : null}
                      {l.releaseReason ? ` · released (${l.releaseReason.toLowerCase()})` : ""}
                    </div>
                  </>
                ) : (
                  <>
                    <span className="font-mono">{l.adjustmentNumber}</span>
                    <div className="max-w-72 text-xs text-muted-foreground">
                      {l.adjustmentKind} adjustment: {l.adjustmentReason}
                      {l.releaseReason ? ` · released (${l.releaseReason.toLowerCase()})` : ""}
                    </div>
                  </>
                )}
              </td>
              <td className="py-2 pr-3">
                {l.invoiceId ? (
                  canOpenInvoices ? (
                    <Link to={`/invoices/${l.invoiceId}`} className={`font-mono ${linkClass}`}>
                      {l.invoiceNumber}
                    </Link>
                  ) : (
                    <span className="font-mono">{l.invoiceNumber}</span>
                  )
                ) : (
                  NOT_AVAILABLE
                )}
              </td>
              <td className="py-2 pr-3 text-right font-mono tabular">{l.baseAmount !== null ? formatCurrency(l.baseAmount) : NOT_AVAILABLE}</td>
              <td className="py-2 pr-3 text-right font-mono tabular">{l.commissionPercent !== null ? formatPercent(l.commissionPercent) : NOT_AVAILABLE}</td>
              <td className="py-2 text-right">
                <SignedAmount amount={l.amount} className={cn(l.releasedAt && "text-muted-foreground line-through")} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
