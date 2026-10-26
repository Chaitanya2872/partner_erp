import { Link } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { ErrorState, LoadingState } from "@/components/feedback/States";
import { ContentCard } from "@/components/layout/Page";
import { DescriptionList, DetailRow } from "@/components/data/DataDisplay";
import { queryKeys } from "@/lib/queryKeys";
import { formatCount, formatCurrency, formatDate, formatNumber, NOT_AVAILABLE } from "@/utils/format";
import { payoutService } from "../services/payoutService";
import { linkClass, PayoutStatusBadge, SignedAmount } from "./PayoutParts";

/** A partner's payout position and latest payouts, on the partner page (Payouts read). */
export function PartnerPayoutsCard({ partnerId }: { partnerId: string }) {
  const balance = useQuery({ queryKey: queryKeys.payouts.balance(partnerId), queryFn: ({ signal }) => payoutService.balance(partnerId, signal) });
  const filters = { partnerId, page: 0, size: 5 };
  const recent = useQuery({ queryKey: queryKeys.payouts.list(filters), queryFn: ({ signal }) => payoutService.list(filters, signal) });

  return (
    <ContentCard
      title="Payouts"
      description="Commission on money received from this partner's invoices, excl. GST."
      action={
        <Link to="/payouts?tab=partners" className={`text-sm ${linkClass}`}>
          All balances
        </Link>
      }
    >
      {balance.isPending ? (
        <LoadingState label="Loading payouts…" />
      ) : balance.isError ? (
        <ErrorState error={balance.error} onRetry={() => balance.refetch()} />
      ) : (
        <div className="space-y-4">
          <DescriptionList columns={2}>
            <DetailRow label="Eligible now">
              {formatCurrency(balance.data.eligibleCommission)} <span className="text-xs text-muted-foreground">({formatCount(balance.data.eligibleReceipts, "receipt")})</span>
            </DetailRow>
            <DetailRow label="Carried adjustments">
              <SignedAmount amount={balance.data.carriedAdjustments} />
              {balance.data.pendingAdjustments > 0 ? <span className="ml-1 text-xs text-muted-foreground">· {formatNumber(balance.data.pendingAdjustments)} awaiting approval</span> : null}
            </DetailRow>
            <DetailRow label="In progress">{formatCurrency(balance.data.inProgress)}</DetailRow>
            <DetailRow label="Paid this financial year">
              {formatCurrency(balance.data.paidFy)} <span className="text-xs text-muted-foreground">(TDS {formatCurrency(balance.data.tdsFy)})</span>
            </DetailRow>
            <DetailRow label="Paid in total">{formatCurrency(balance.data.paidTotal)}</DetailRow>
            <DetailRow label="Last paid">{balance.data.lastPaidDate ? formatDate(balance.data.lastPaidDate) : NOT_AVAILABLE}</DetailRow>
          </DescriptionList>
          {recent.data && recent.data.content.length > 0 ? (
            <ul className="divide-y divide-border/60 text-sm">
              {recent.data.content.map((p) => (
                <li key={p.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                  <span className="flex items-center gap-2">
                    <Link to={`/payouts/${p.id}`} className={`font-mono ${linkClass}`}>
                      {p.payoutNumber}
                    </Link>
                    <PayoutStatusBadge status={p.status} />
                  </span>
                  <span className="font-mono tabular">{p.netAmount !== null ? formatCurrency(p.netAmount) : formatCurrency(p.commissionTotal)}</span>
                </li>
              ))}
            </ul>
          ) : recent.isSuccess ? (
            <p className="text-sm text-muted-foreground">No payouts yet.</p>
          ) : null}
        </div>
      )}
    </ContentCard>
  );
}
