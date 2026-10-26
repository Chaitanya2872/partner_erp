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
import { SearchInput } from "@/components/forms/SearchInput";
import { DateInput } from "@/components/forms/inputs";
import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Input";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { saveTextFile } from "@/utils/download";
import { formatCurrency, formatDate, NOT_AVAILABLE } from "@/utils/format";
import { invoiceService } from "../services/invoiceService";
import { useInvoiceMeta } from "../hooks/useInvoices";
import { InvoiceStatusBadge } from "../components/InvoiceParts";
import type { InvoiceStatus, InvoiceSummary, PaymentFilter } from "../types/invoice.types";

const linkClass = "font-medium text-accent-ink underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-ring";

/**
 * Invoice register. Partner-safe columns only; the internal B2C copy is opened from an invoice's page. Payment
 * status is derived from recorded payments (BR-V6). The server enforces every permission and the data scope.
 */
export function InvoicesPage() {
  const { canRead, canWrite, canPerform } = useAuth();
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<InvoiceStatus | "">("");
  const [payment, setPayment] = useState<PaymentFilter | "">("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState<SortState | null>(null);
  const [exporting, setExporting] = useState(false);
  const q = useDebounce(search.trim());

  const meta = useInvoiceMeta();
  const filters = { q, status, payment, from, to, page, size, sort: toSortParam(sort) };
  const invoices = useQuery({
    queryKey: queryKeys.invoices.list(filters),
    queryFn: ({ signal }) => invoiceService.list(filters, signal),
    placeholderData: keepPreviousData,
  });

  const canCreate = canWrite("INVOICES");
  const canExport = canPerform("INVOICES", "EXPORT");
  const canOpenLead = canRead("LEADS");
  const canOpenPartner = canRead("PARTNERS");
  const set =
    <T,>(setter: (value: T) => void) =>
    (value: T) => {
      setter(value);
      setPage(0);
    };
  const filtered = !!q || !!status || !!payment || !!from || !!to;

  const exportCsv = async () => {
    setExporting(true);
    try {
      const { text, filename } = await invoiceService.exportCsv({ q, status, payment, from, to });
      saveTextFile(text, filename);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setExporting(false);
    }
  };

  const newInvoice = canCreate ? (
    <Button asChild variant="primary">
      <Link to="/invoices/new">
        <Plus aria-hidden="true" /> New invoice
      </Link>
    </Button>
  ) : undefined;

  const columns: ColumnDef<InvoiceSummary>[] = [
    {
      id: "invoice",
      header: "Invoice",
      sortKey: "invoiceNumber",
      cell: (i) => (
        <div className="whitespace-nowrap">
          <Link to={`/invoices/${i.id}`} className={`font-mono ${linkClass}`}>
            {i.invoiceNumber ?? "Draft"}
          </Link>
          <div className="text-xs text-muted-foreground">
            {i.invoiceDate ? formatDate(i.invoiceDate) : `Created ${formatDate(i.createdAt.slice(0, 10))}`}
            {i.estimateNumber ? ` · ${i.estimateNumber}` : ""}
          </div>
        </div>
      ),
    },
    {
      id: "client",
      header: "Billed to",
      cell: (i) => (
        <div className="min-w-40 max-w-64">
          <div className="truncate">{i.billingName ?? i.clientName ?? NOT_AVAILABLE}</div>
          <div className="truncate text-xs text-muted-foreground">
            {i.leadNumber ? (
              canOpenLead && i.leadId ? (
                <Link to={`/leads/${i.leadId}`} className="hover:underline">
                  {i.leadNumber}
                </Link>
              ) : (
                i.leadNumber
              )
            ) : null}
          </div>
        </div>
      ),
    },
    {
      id: "partner",
      header: "Partner",
      cell: (i) =>
        canOpenPartner ? (
          <Link to={`/partners/${i.partner.id}`} className={`whitespace-nowrap ${linkClass}`}>
            {i.partner.name}
          </Link>
        ) : (
          <span className="whitespace-nowrap">{i.partner.name}</span>
        ),
    },
    { id: "status", header: "Status", cell: (i) => <InvoiceStatusBadge status={i.status} paymentStatus={i.paymentStatus} /> },
    { id: "dueDate", header: "Due", sortKey: "dueDate", cell: (i) => <span className="whitespace-nowrap">{i.dueDate ? formatDate(i.dueDate) : NOT_AVAILABLE}</span> },
    { id: "total", header: "Total incl. GST", numeric: true, cell: (i) => <span className="whitespace-nowrap">{formatCurrency(i.grandTotal)}</span> },
    {
      id: "balance",
      header: "Balance",
      numeric: true,
      cell: (i) => <span className="whitespace-nowrap">{i.balance === null || i.status === "CANCELLED" ? NOT_AVAILABLE : formatCurrency(i.balance)}</span>,
    },
  ];

  return (
    <PageContainer>
      <PageHeader
        title="Invoices"
        description="GST tax invoices. Numbers are assigned on issue; tax is calculated per line on the server."
        actions={
          canExport || newInvoice ? (
            <PageActions>
              {canExport ? (
                <Button onClick={exportCsv} loading={exporting} loadingText="Exporting…">
                  <Download aria-hidden="true" /> Export CSV
                </Button>
              ) : null}
              {newInvoice}
            </PageActions>
          ) : undefined
        }
      />

      <DataTable
        caption="Invoices"
        itemLabel="invoices"
        columns={columns}
        rows={invoices.data?.content}
        rowKey={(i) => i.id}
        loading={invoices.isPending}
        fetching={invoices.isFetching && !invoices.isPending}
        error={invoices.error}
        onRetry={() => invoices.refetch()}
        sort={sort}
        onSortChange={set(setSort)}
        page={invoices.data?.meta}
        onPageChange={setPage}
        pageSizeOptions={[20, 50, 100]}
        onPageSizeChange={set(setSize)}
        empty={
          <EmptyState title={filtered ? "No invoices match" : "No invoices yet"} action={!filtered ? newInvoice : undefined}>
            {filtered ? "Try other filters." : "Convert a sent or accepted estimate, or create an invoice directly."}
          </EmptyState>
        }
        toolbar={
          <Toolbar label="Invoice filters">
            <SearchInput placeholder="Search invoices" aria-label="Search invoices" value={search} onValueChange={set(setSearch)} />
            <Select aria-label="Filter by status" value={status} wrapperClassName="sm:w-36" onChange={(e) => set(setStatus)(e.target.value as InvoiceStatus | "")}>
              <option value="">All statuses</option>
              {meta.data?.statuses.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </Select>
            <Select aria-label="Filter by payment" value={payment} wrapperClassName="sm:w-40" onChange={(e) => set(setPayment)(e.target.value as PaymentFilter | "")}>
              <option value="">Any payment</option>
              {meta.data?.paymentStatuses.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </Select>
            <DateInput aria-label="Invoice date from" value={from} max={to || undefined} onChange={(e) => set(setFrom)(e.target.value)} className="sm:w-40" />
            <DateInput aria-label="Invoice date to" value={to} min={from || undefined} onChange={(e) => set(setTo)(e.target.value)} className="sm:w-40" />
          </Toolbar>
        }
      />
    </PageContainer>
  );
}
