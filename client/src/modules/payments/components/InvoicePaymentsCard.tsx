import { useState } from "react";
import { Link } from "react-router";
import { Plus } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { ContentCard } from "@/components/layout/Page";
import { DataTable, type ColumnDef } from "@/components/tables/DataTable";
import { EmptyState } from "@/components/feedback/States";
import { Button } from "@/components/ui/Button";
import { formatCurrency, formatDate, NOT_AVAILABLE } from "@/utils/format";
import type { Invoice } from "@/modules/invoices/types/invoice.types";
import { useInvoicePayments } from "../hooks/usePayments";
import { LedgerAmount, PaymentEntryBadge } from "./PaymentParts";
import { RecordPaymentDialog } from "./RecordPaymentDialog";
import type { PaymentSummary } from "../types/payment.types";

const linkClass = "font-mono font-medium text-accent-ink underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-ring";

/**
 * Payments ledger of an issued or cancelled invoice (Payments read): receipts, reversals and the balance. Recording
 * needs Payments write and an issued invoice with a balance; the server checks both again.
 */
export function InvoicePaymentsCard({ invoice }: { invoice: Invoice }) {
  const { canWrite } = useAuth();
  const payments = useInvoicePayments(invoice.id);
  const [recording, setRecording] = useState(false);
  const balance = invoice.balance === null ? null : String(invoice.balance);
  const canRecord = canWrite("PAYMENTS") && invoice.status === "ISSUED" && balance !== null && Number(balance) > 0.5;

  const columns: ColumnDef<PaymentSummary>[] = [
    {
      id: "payment",
      header: "Entry",
      cell: (p) => (
        <div className="whitespace-nowrap">
          <Link to={`/payments/${p.id}`} className={linkClass}>
            {p.paymentNumber ?? "Payment"}
          </Link>
          <div className="text-xs text-muted-foreground">{formatDate(p.paymentDate)}</div>
        </div>
      ),
    },
    { id: "type", header: "Type", cell: (p) => <PaymentEntryBadge entryType={p.entryType} reversed={p.reversed} /> },
    {
      id: "method",
      header: "Method · reference",
      cell: (p) => (
        <div className="min-w-32">
          <div>{p.methodLabel}</div>
          <div className="truncate font-mono text-xs text-muted-foreground">{p.reference ?? NOT_AVAILABLE}</div>
        </div>
      ),
    },
    { id: "amount", header: "Amount", numeric: true, cell: (p) => <LedgerAmount amount={p.amount} reversed={p.reversed} /> },
  ];

  const record = canRecord ? (
    <Button size="sm" onClick={() => setRecording(true)}>
      <Plus aria-hidden="true" /> Record payment
    </Button>
  ) : undefined;

  return (
    <ContentCard
      title="Payments"
      description={`Paid ${formatCurrency(invoice.paidAmount ?? 0)} · balance ${balance === null ? NOT_AVAILABLE : formatCurrency(balance)}. Receipts can't be edited; mistakes are reversed.`}
      action={record}
    >
      <DataTable
        caption={`Payments for ${invoice.invoiceNumber ?? "this invoice"}`}
        itemLabel="entries"
        columns={columns}
        rows={payments.data?.content}
        rowKey={(p) => p.id}
        loading={payments.isPending}
        error={payments.error}
        onRetry={() => payments.refetch()}
        columnMenu={false}
        skeletonRows={2}
        empty={<EmptyState title="No payments recorded">{canRecord ? "Record money received against this invoice." : undefined}</EmptyState>}
      />
      {invoice.invoiceNumber && invoice.invoiceDate && balance !== null ? (
        <RecordPaymentDialog
          open={recording}
          onClose={() => setRecording(false)}
          invoice={{
            id: invoice.id,
            invoiceNumber: invoice.invoiceNumber,
            invoiceDate: invoice.invoiceDate,
            billingName: invoice.billing.name,
            partnerName: invoice.partner.name,
            balance,
          }}
        />
      ) : null}
    </ContentCard>
  );
}
