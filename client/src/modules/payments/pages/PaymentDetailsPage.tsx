import { useState } from "react";
import { Link, useParams } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { Undo2 } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { ErrorState, LoadingState } from "@/components/feedback/States";
import { ContentCard, PageActions, PageContainer, PageHeader } from "@/components/layout/Page";
import { DescriptionList, DetailRow, Timeline, type TimelineEntry } from "@/components/data/DataDisplay";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { queryKeys } from "@/lib/queryKeys";
import { formatCurrency, formatDate, formatDateTime, NOT_AVAILABLE } from "@/utils/format";
import { paymentService } from "../services/paymentService";
import { usePayment, usePaymentMeta } from "../hooks/usePayments";
import { LedgerAmount, PaymentEntryBadge } from "../components/PaymentParts";
import { ReversePaymentDialog } from "../components/ReversePaymentDialog";
import type { PaymentHistoryEntry } from "../types/payment.types";

const linkClass = "font-medium text-accent-ink underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-ring";

const HISTORY_TITLES: Record<string, string> = { PAYMENT_RECORDED: "Recorded", PAYMENT_REVERSED: "Reversed" };
const FIELD_LABELS: Record<string, string> = {
  paymentNumber: "Number", invoiceNumber: "Invoice", paymentDate: "Received on", amount: "Amount", netAmount: "Excl. GST", method: "Method",
  reference: "Reference", balanceAfter: "Balance after", duplicateConfirmed: "Possible duplicate confirmed", reversalNumber: "Reversal",
  reversalDate: "Reversal date", reason: "Reason",
};
const MONEY_FIELDS = new Set(["amount", "netAmount", "balanceAfter"]);
const HIDDEN_FIELDS = new Set(["reversed"]);

export function PaymentDetailsPage() {
  const { id = "" } = useParams();
  const { canRead } = useAuth();
  const payment = usePayment(id);
  const meta = usePaymentMeta();
  const history = useQuery({
    queryKey: queryKeys.payments.history(id),
    queryFn: ({ signal }) => paymentService.history(id, signal),
  });
  const [reversing, setReversing] = useState(false);

  if (payment.isPending) return <LoadingState label="Loading payment…" />;
  if (payment.isError) {
    return (
      <PageContainer>
        <PageHeader title="Payment" back={{ to: "/payments", label: "Payments" }} />
        <ErrorState error={payment.error} onRetry={() => payment.refetch()} />
      </PageContainer>
    );
  }

  const p = payment.data;
  const inv = p.invoice;
  const reversed = p.reversal !== null;
  const isReceipt = p.entryType === "RECEIPT";
  const title = p.paymentNumber ?? "Payment";

  return (
    <PageContainer>
      <PageHeader
        title={title}
        back={{ to: "/payments", label: "Payments" }}
        badge={<PaymentEntryBadge entryType={p.entryType} reversed={reversed} />}
        description={`${inv.invoiceNumber ?? NOT_AVAILABLE} · ${inv.billingName ?? p.partner.name} · ${formatCurrency(p.amount)}`}
        actions={
          p.canReverse ? (
            <PageActions>
              <Button variant="destructive-outline" onClick={() => setReversing(true)}>
                <Undo2 aria-hidden="true" /> Reverse payment
              </Button>
            </PageActions>
          ) : undefined
        }
      />

      {p.reversal ? (
        <Alert tone="warning" title="Reversed">
          Reversed by{" "}
          <Link to={`/payments/${p.reversal.id}`} className={`font-mono ${linkClass}`}>
            {p.reversal.paymentNumber}
          </Link>{" "}
          on {formatDate(p.reversal.paymentDate)}
          {p.reversal.createdByName ? ` by ${p.reversal.createdByName}` : ""}: “{p.reversal.reason}”. This receipt no longer counts towards the invoice.
        </Alert>
      ) : null}
      {p.entryType === "REVERSAL" ? (
        <Alert tone="info" title="Reversal entry">
          Reverses{" "}
          {p.reversalOf ? (
            <Link to={`/payments/${p.reversalOf.id}`} className={`font-mono ${linkClass}`}>
              {p.reversalOf.paymentNumber}
            </Link>
          ) : (
            "a receipt"
          )}
          : “{p.reversalReason}”. Reversal entries can&apos;t be reversed.
        </Alert>
      ) : null}
      {p.entryType === "REFUND" ? (
        <Alert tone="info" title="Refund recorded before Phase 10">
          Refunds are no longer recorded here; they need an approved credit-note process.
        </Alert>
      ) : null}
      {isReceipt && !reversed && p.reverseBlockedBy ? (
        <Alert tone="warning" title="Can't be reversed now">
          {p.reverseBlockedBy}
        </Alert>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-3">
        <ContentCard title="Payment" className="lg:col-span-2">
          <DescriptionList columns={2}>
            <DetailRow label="Received on">{formatDate(p.paymentDate)}</DetailRow>
            <DetailRow label="Amount (incl. GST)">
              <LedgerAmount amount={p.amount} reversed={reversed} />
            </DetailRow>
            <DetailRow label="Excl. GST">
              <LedgerAmount amount={p.netAmount} reversed={reversed} />
            </DetailRow>
            <DetailRow label="Method">{p.methodLabel}</DetailRow>
            <DetailRow label="Reference" mono>
              {p.reference ?? NOT_AVAILABLE}
            </DetailRow>
            <DetailRow label="Partner">
              {canRead("PARTNERS") ? (
                <Link to={`/partners/${p.partner.id}`} className={linkClass}>
                  {p.partner.name}
                </Link>
              ) : (
                p.partner.name
              )}
            </DetailRow>
            <DetailRow label="Notes" className="sm:col-span-2">
              {p.notes ? <span className="whitespace-pre-wrap">{p.notes}</span> : NOT_AVAILABLE}
            </DetailRow>
          </DescriptionList>
        </ContentCard>
        <ContentCard title="Invoice">
          <DescriptionList>
            <DetailRow label="Invoice">
              {canRead("INVOICES") ? (
                <Link to={`/invoices/${inv.id}`} className={`font-mono ${linkClass}`}>
                  {inv.invoiceNumber ?? NOT_AVAILABLE}
                </Link>
              ) : (
                <span className="font-mono">{inv.invoiceNumber ?? NOT_AVAILABLE}</span>
              )}
            </DetailRow>
            <DetailRow label="Billed to">{inv.billingName ?? NOT_AVAILABLE}</DetailRow>
            <DetailRow label="Issued · due">
              {inv.invoiceDate ? formatDate(inv.invoiceDate) : NOT_AVAILABLE} · {inv.dueDate ? formatDate(inv.dueDate) : NOT_AVAILABLE}
            </DetailRow>
            <DetailRow label="Total incl. GST">{formatCurrency(inv.grandTotal)}</DetailRow>
            <DetailRow label="Paid · balance">
              {formatCurrency(inv.paidAmount)} · {inv.status === "CANCELLED" ? "Cancelled" : formatCurrency(inv.balance)}
            </DetailRow>
            <DetailRow label="Payment status">{inv.paymentStatusLabel ?? NOT_AVAILABLE}</DetailRow>
          </DescriptionList>
        </ContentCard>
      </div>

      <ContentCard title="History" description="References are masked to their last four characters.">
        {history.isPending ? (
          <LoadingState label="Loading history…" />
        ) : history.isError ? (
          <ErrorState error={history.error} onRetry={() => history.refetch()} />
        ) : (
          <Timeline entries={history.data.map((h) => historyEntry(h, meta.data?.methods ?? []))} empty={p.entryType === "REVERSAL" ? "Recorded in the history of the reversed receipt." : undefined} />
        )}
      </ContentCard>

      <p className="text-xs text-muted-foreground">
        Recorded {formatDateTime(p.createdAt)}
        {p.createdByName ? ` by ${p.createdByName}` : ""}
      </p>

      {p.canReverse ? <ReversePaymentDialog payment={p} open={reversing} onClose={() => setReversing(false)} /> : null}
    </PageContainer>
  );
}

function historyEntry(h: PaymentHistoryEntry, methods: { value: string; label: string }[]): TimelineEntry {
  const changes = h.changes.filter((c) => !HIDDEN_FIELDS.has(c.field) && c.to !== null && c.to !== undefined);
  return {
    id: h.id,
    tone: h.action === "PAYMENT_REVERSED" ? "danger" : "success",
    title: HISTORY_TITLES[h.action] ?? h.action,
    time: `${formatDateTime(h.at)}${h.actorName ? ` · ${h.actorName}` : ""}`,
    description: changes.length > 0 ? changes.map((c) => `${FIELD_LABELS[c.field] ?? c.field}: ${c.field === "method" ? (methods.find((m) => m.value === c.to)?.label ?? show(c.to, c.field)) : show(c.to, c.field)}`).join("; ") : undefined,
  };
}

function show(value: unknown, field: string): string {
  if (value === null || value === undefined || value === "") return NOT_AVAILABLE;
  if (MONEY_FIELDS.has(field)) return formatCurrency(value as string);
  if (field === "paymentDate" || field === "reversalDate") return formatDate(String(value));
  if (typeof value === "boolean") return value ? "Yes" : "No";
  return String(value);
}
