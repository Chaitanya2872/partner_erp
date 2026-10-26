import { StatusBadge } from "@/components/data/StatusBadge";
import { cn } from "@/lib/cn";
import { formatCurrency } from "@/utils/format";
import type { Money } from "@/modules/partners/types/partner.types";
import type { PaymentEntryType } from "../types/payment.types";

/** Receipt, reversal or (carried-over) refund; a receipt that was reversed shows Reversed. */
export function PaymentEntryBadge({ entryType, reversed }: { entryType: PaymentEntryType; reversed?: boolean }) {
  return <StatusBadge domain="payment" status={entryType === "RECEIPT" && reversed ? "REVERSED" : entryType} />;
}

/** Ledger amount: negative entries (reversals, refunds) are marked with a minus sign; reversed receipts are struck. */
export function LedgerAmount({ amount, reversed, className }: { amount: Money; reversed?: boolean; className?: string }) {
  const negative = Number(amount) < 0;
  return (
    <span className={cn("whitespace-nowrap font-mono tabular", negative && "text-danger", reversed && "text-muted-foreground line-through", className)}>
      {negative ? `−${formatCurrency(String(amount).replace("-", ""))}` : formatCurrency(amount)}
    </span>
  );
}
