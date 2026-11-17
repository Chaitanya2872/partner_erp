import { useState } from "react";
import { Link } from "react-router";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { Download, Plus } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useDebounce } from "@/hooks/useDebounce";
import { toSortParam, type SortState } from "@/components/tables/sorting";
import { DataTable, type ColumnDef } from "@/components/tables/DataTable";
import { EmptyState } from "@/components/feedback/States";
import { PageActions, PageContainer, PageHeader, Toolbar } from "@/components/layout/Page";
import { MetricCard, SummaryPanel } from "@/components/dashboard/Dashboard";
import { SearchInput } from "@/components/forms/SearchInput";
import { FilterDate } from "@/components/forms/inputs";
import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Input";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { saveTextFile } from "@/utils/download";
import { formatCurrency, formatDate, formatNumber, NOT_AVAILABLE } from "@/utils/format";
import { paymentService } from "../services/paymentService";
import { usePaymentMeta } from "../hooks/usePayments";
import { LedgerAmount, PaymentEntryBadge } from "../components/PaymentParts";
import { RecordPaymentDialog } from "../components/RecordPaymentDialog";
import type { PaymentEntryType, PaymentMethod, PaymentState, PaymentSummary } from "../types/payment.types";

const linkClass = "font-medium text-accent-ink underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-ring";

/**
 * Payments ledger: receipts against issued invoices and the reversals of mistaken ones. Totals follow the filters;
 * receivables are the current outstanding balances of issued invoices. The server enforces permissions and data scope.
 */
export function PaymentsPage() {
  const { canRead, canWrite, canPerform } = useAuth();
  const [search, setSearch] = useState("");
  const [method, setMethod] = useState<PaymentMethod | "">("");
  const [type, setType] = useState<PaymentEntryType | "">("");
  const [state, setState] = useState<PaymentState | "">("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState<SortState | null>(null);
  const [exporting, setExporting] = useState(false);
  const [recording, setRecording] = useState(false);
  const q = useDebounce(search.trim());

  const meta = usePaymentMeta();
  const criteria = { q, method, type, state, from, to };
  const filters = { ...criteria, page, size, sort: toSortParam(sort) };
  const payments = useQuery({
    queryKey: queryKeys.payments.list(filters),
    queryFn: ({ signal }) => paymentService.list(filters, signal),
    placeholderData: keepPreviousData,
  });
  const report = useQuery({
    queryKey: queryKeys.payments.report(criteria),
    queryFn: ({ signal }) => paymentService.report(criteria, signal),
    placeholderData: keepPreviousData,
  });

  const canRecord = canWrite("PAYMENTS");
  const canExport = canPerform("PAYMENTS", "EXPORT");
  const canOpenInvoice = canRead("INVOICES");
  const canOpenPartner = canRead("PARTNERS");
  const set =
    <T,>(setter: (value: T) => void) =>
    (value: T) => {
      setter(value);
      setPage(0);
    };
  const filtered = !!q || !!method || !!type || !!state || !!from || !!to;
  const clearFilters = () => { setSearch(""); setMethod(""); setType(""); setState(""); setFrom(""); setTo(""); setPage(0); };

  const exportCsv = async () => {
    setExporting(true);
    try {
      const { text, filename } = await paymentService.exportCsv(criteria);
      saveTextFile(text, filename);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setExporting(false);
    }
  };

  const recordButton = canRecord ? (
    <Button variant="primary" onClick={() => setRecording(true)}>
      <Plus aria-hidden="true" /> Record payment
    </Button>
  ) : undefined;

  const columns: ColumnDef<PaymentSummary>[] = [
    {
      id: "payment",
      header: "Payment",
      sortKey: "paymentDate",
      cell: (p) => (
        <div className="whitespace-nowrap">
          <Link to={`/payments/${p.id}`} className={`font-mono ${linkClass}`}>
            {p.paymentNumber ?? "Payment"}
          </Link>
          <div className="text-xs text-muted-foreground">
            {formatDate(p.paymentDate)}
            {p.reversalOf?.paymentNumber ? ` · reverses ${p.reversalOf.paymentNumber}` : ""}
          </div>
        </div>
      ),
    },
    { id: "type", header: "Type", cell: (p) => <PaymentEntryBadge entryType={p.entryType} reversed={p.reversed} /> },
    {
      id: "invoice",
      header: "Invoice",
      cell: (p) => (
        <div className="min-w-40 max-w-64">
          {canOpenInvoice ? (
            <Link to={`/invoices/${p.invoice.id}`} className={`font-mono ${linkClass}`}>
              {p.invoice.invoiceNumber ?? NOT_AVAILABLE}
            </Link>
          ) : (
            <span className="font-mono">{p.invoice.invoiceNumber ?? NOT_AVAILABLE}</span>
          )}
          <div className="truncate text-xs text-muted-foreground">{p.billingName ?? NOT_AVAILABLE}</div>
        </div>
      ),
    },
    {
      id: "partner",
      header: "Partner",
      cell: (p) =>
        canOpenPartner ? (
          <Link to={`/partners/${p.partner.id}`} className={`whitespace-nowrap ${linkClass}`}>
            {p.partner.name}
          </Link>
        ) : (
          <span className="whitespace-nowrap">{p.partner.name}</span>
        ),
    },
    {
      id: "method",
      header: "Method · reference",
      cell: (p) => (
        <div className="min-w-32 max-w-56">
          <div>{p.methodLabel}</div>
          <div className="truncate font-mono text-xs text-muted-foreground">{p.reference ?? NOT_AVAILABLE}</div>
        </div>
      ),
    },
    { id: "amount", header: "Amount", sortKey: "amount", numeric: true, cell: (p) => <LedgerAmount amount={p.amount} reversed={p.reversed} /> },
  ];

  const r = report.data;
  return (
    <PageContainer>
      <PageHeader
        title="Payments"
        description="Money received against issued invoices. Entries are never edited or deleted; a mistaken receipt is reversed with a reason."
        actions={
          canExport || recordButton ? (
            <PageActions>
              {canExport ? (
                <Button onClick={exportCsv} loading={exporting} loadingText="Exporting…">
                  <Download aria-hidden="true" /> Export CSV
                </Button>
              ) : null}
              {recordButton}
            </PageActions>
          ) : undefined
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          label="Received"
          loading={report.isPending}
          value={r ? formatCurrency(r.received) : undefined}
          hint={r ? `${formatNumber(r.receipts)} receipts${filtered ? " matching the filters" : ""}` : undefined}
        />
        <MetricCard label="Reversed" loading={report.isPending} value={r ? formatCurrency(r.reversed) : undefined} hint={r ? `${formatNumber(r.reversals)} reversals` : undefined} />
        <MetricCard
          label="Net received"
          loading={report.isPending}
          value={r ? formatCurrency(r.netReceived) : undefined}
          hint={r ? `${formatCurrency(r.netReceivedExGst)} excl. GST` : undefined}
        />
        <MetricCard
          label="Outstanding"
          loading={report.isPending}
          value={r ? formatCurrency(r.receivables.outstandingAmount) : undefined}
          hint={r ? `${formatNumber(r.receivables.outstandingInvoices)} invoices · ${formatCurrency(r.receivables.overdueAmount)} overdue` : undefined}
        />
      </div>
      {r && r.byMethod.length > 0 ? (
        <SummaryPanel
          title="Net received by method"
          rows={[
            ...r.byMethod.map((m) => ({ label: `${m.label} (${formatNumber(m.receipts)})`, value: formatCurrency(m.netReceived) })),
            { label: "Total", value: formatCurrency(r.netReceived), emphasis: true },
            // Legacy refunds are negative entries already netted into the method totals above.
            ...(Number(r.refunded) !== 0 ? [{ label: "Includes refunds recorded before Phase 10", value: `−${formatCurrency(r.refunded)}` }] : []),
          ]}
        />
      ) : null}

      <DataTable
        caption="Payments"
        itemLabel="entries"
        columns={columns}
        rows={payments.data?.content}
        rowKey={(p) => p.id}
        loading={payments.isPending}
        fetching={payments.isFetching && !payments.isPending}
        error={payments.error}
        onRetry={() => payments.refetch()}
        sort={sort}
        onSortChange={set(setSort)}
        page={payments.data?.meta}
        onPageChange={setPage}
        pageSizeOptions={[20, 50, 100]}
        onPageSizeChange={set(setSize)}
        empty={
          <EmptyState title={filtered ? "No payments match" : "No payments yet"} onClearFilters={filtered ? clearFilters : undefined} action={!filtered ? recordButton : undefined}>
            {filtered ? undefined : "Record money received against an issued invoice."}
          </EmptyState>
        }
        toolbar={
          <Toolbar label="Payment filters">
            <SearchInput placeholder="Search payments" aria-label="Search payments" value={search} onValueChange={set(setSearch)} />
            <Select aria-label="Filter by method" value={method} wrapperClassName="sm:w-36" onChange={(e) => set(setMethod)(e.target.value as PaymentMethod | "")}>
              <option value="">All methods</option>
              {meta.data?.methods.map((m) => (
                <option key={m.value} value={m.value}>
                  {m.label}
                </option>
              ))}
            </Select>
            <Select aria-label="Filter by type" value={type} wrapperClassName="sm:w-36" onChange={(e) => set(setType)(e.target.value as PaymentEntryType | "")}>
              <option value="">All types</option>
              {meta.data?.entryTypes.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </Select>
            <Select aria-label="Filter by state" value={state} wrapperClassName="sm:w-36" onChange={(e) => set(setState)(e.target.value as PaymentState | "")}>
              <option value="">Any state</option>
              {meta.data?.states.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </Select>
            <FilterDate label="From" aria-label="Payment date from" value={from} max={to || undefined} onChange={(e) => set(setFrom)(e.target.value)} className="sm:w-40" />
            <FilterDate label="To" aria-label="Payment date to" value={to} min={from || undefined} onChange={(e) => set(setTo)(e.target.value)} className="sm:w-40" />
          </Toolbar>
        }
      />
      <RecordPaymentDialog open={recording} onClose={() => setRecording(false)} />
    </PageContainer>
  );
}
