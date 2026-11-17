import { useState, type ReactNode } from "react";
import { Link, useSearchParams } from "react-router";
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
import { StatusBadge } from "@/components/data/StatusBadge";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/Tabs";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { saveTextFile } from "@/utils/download";
import { formatCount, formatCurrency, formatDate, formatNumber, NOT_AVAILABLE } from "@/utils/format";
import { payoutService } from "../services/payoutService";
import { usePayoutMeta } from "../hooks/usePayouts";
import { linkClass, PayoutStatusBadge, SignedAmount } from "../components/PayoutParts";
import { CreatePayoutDialog } from "../components/CreatePayoutDialog";
import { DecideAdjustmentDialog, NewAdjustmentDialog } from "../components/AdjustmentDialogs";
import type { AdjustmentKind, AdjustmentStatus, PartnerAdjustment, PartnerPayoutBalance, PayoutStatus, PayoutSummary } from "../types/payout.types";

type Tab = "payouts" | "partners" | "adjustments";

/**
 * Partner payouts: commission on realised collections (excl. GST) at the rate locked on each invoice, less TDS from the
 * confirmed settings. Payouts go draft → approval → processing → paid; nothing here moves money. The server enforces
 * permissions, data scope and separation of duties; buttons only follow what it reports.
 */
export function PayoutsPage() {
  const { canRead } = useAuth();
  const [params, setParams] = useSearchParams();
  const tab = (["payouts", "partners", "adjustments"].includes(params.get("tab") ?? "") ? params.get("tab") : "payouts") as Tab;
  const meta = usePayoutMeta();
  const dashboard = useQuery({ queryKey: queryKeys.payouts.dashboard, queryFn: ({ signal }) => payoutService.dashboard(signal) });
  const [creating, setCreating] = useState<{ partnerId?: string } | null>(null);

  const m = meta.data;
  const d = dashboard.data;
  const status = (s: PayoutStatus) => d?.byStatus.find((b) => b.status === s);
  const open = ["DRAFT", "PENDING_APPROVAL", "APPROVED", "PROCESSING"] as const;
  const openCount = open.reduce((n, s) => n + (status(s)?.count ?? 0), 0);
  const createButton = m?.canWrite ? (
    <Button variant="primary" onClick={() => setCreating({})}>
      <Plus aria-hidden="true" /> Create payout
    </Button>
  ) : undefined;

  return (
    <PageContainer>
      <PageHeader
        title="Payouts"
        description="Partner commission on money actually received, less TDS. Approved payouts are paid by Finance outside this system and recorded here."
        actions={createButton ? <PageActions>{createButton}</PageActions> : undefined}
      />

      {m && !m.tdsConfigured ? (
        <Alert tone="warning" title="TDS settings not confirmed">
          {m.tdsMessage ?? "Payouts can be drafted and previewed, but none can be approved or paid until Finance confirms the TDS settings."}{" "}
          {canRead("SETTINGS") ? (
            <Link to="/settings#tds" className={linkClass}>
              Open TDS settings
            </Link>
          ) : null}
        </Alert>
      ) : null}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          label="Ready to pay"
          loading={dashboard.isPending}
          value={d ? formatCurrency(d.eligibleCommission) : undefined}
          hint={d ? `${formatCount(d.eligibleReceipts, "receipt")} · ${formatCount(d.partnersWithBalance, "partner")}` : undefined}
        />
        <MetricCard
          label="In progress"
          loading={dashboard.isPending}
          value={d ? formatNumber(openCount) : undefined}
          hint={d ? `${formatNumber(status("PENDING_APPROVAL")?.count ?? 0)} awaiting approval · ${formatNumber(status("PROCESSING")?.count ?? 0)} processing` : undefined}
        />
        <MetricCard label={d ? `Paid in ${d.fyLabel}` : "Paid this year"} loading={dashboard.isPending} value={d ? formatCurrency(d.paidFy) : undefined} hint={d ? `TDS deducted ${formatCurrency(d.tdsFy)}` : undefined} />
        <MetricCard
          label="Adjustments"
          loading={dashboard.isPending}
          value={d ? <SignedAmount amount={d.carriedAdjustments} /> : undefined}
          hint={d ? `Carried to next payouts · ${formatNumber(d.pendingAdjustments)} awaiting approval` : undefined}
        />
      </div>
      {d && d.byStatus.some((b) => b.count > 0) ? (
        <SummaryPanel
          title="Payouts by status (commission · net)"
          rows={d.byStatus
            .filter((b) => b.count > 0)
            .map((b) => ({ label: `${b.label} (${formatNumber(b.count)})`, value: `${formatCurrency(b.commission)} · ${formatCurrency(b.net)}` }))}
        />
      ) : null}

      <Tabs value={tab} onValueChange={(v) => setParams(v === "payouts" ? {} : { tab: v }, { replace: true })}>
        <TabsList aria-label="Payout views">
          <TabsTrigger value="payouts">Payouts</TabsTrigger>
          <TabsTrigger value="partners">Partner balances</TabsTrigger>
          <TabsTrigger value="adjustments">Adjustments</TabsTrigger>
        </TabsList>
        <TabsContent value="payouts" className="mt-4">
          <PayoutList statuses={m?.statuses ?? []} canExport={!!m?.canExport} createButton={createButton} />
        </TabsContent>
        <TabsContent value="partners" className="mt-4">
          <BalanceList canCreate={!!m?.canWrite} onCreate={(partnerId) => setCreating({ partnerId })} />
        </TabsContent>
        <TabsContent value="adjustments" className="mt-4">
          <AdjustmentList canCreate={!!m?.canWrite} statuses={m?.adjustmentStatuses ?? []} />
        </TabsContent>
      </Tabs>
      <CreatePayoutDialog open={creating !== null} partnerId={creating?.partnerId} onClose={() => setCreating(null)} />
    </PageContainer>
  );
}

function PayoutList({ statuses, canExport, createButton }: { statuses: { value: string; label: string }[]; canExport: boolean; createButton?: ReactNode }) {
  const { canRead } = useAuth();
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<PayoutStatus | "">("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState<SortState | null>(null);
  const [exporting, setExporting] = useState(false);
  const q = useDebounce(search.trim());
  const criteria = { q, status, from, to };
  const filters = { ...criteria, page, size, sort: toSortParam(sort) };
  const payouts = useQuery({
    queryKey: queryKeys.payouts.list(filters),
    queryFn: ({ signal }) => payoutService.list(filters, signal),
    placeholderData: keepPreviousData,
  });
  const set =
    <T,>(setter: (value: T) => void) =>
    (value: T) => {
      setter(value);
      setPage(0);
    };
  const filtered = !!q || !!status || !!from || !!to;
  const clearFilters = () => { setSearch(""); setStatus(""); setFrom(""); setTo(""); setPage(0); };
  const canOpenPartner = canRead("PARTNERS");

  const exportCsv = async () => {
    setExporting(true);
    try {
      const { text, filename } = await payoutService.exportCsv(criteria);
      saveTextFile(text, filename);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setExporting(false);
    }
  };

  const columns: ColumnDef<PayoutSummary>[] = [
    {
      id: "payout",
      header: "Payout",
      sortKey: "createdAt",
      cell: (p) => (
        <div className="whitespace-nowrap">
          <Link to={`/payouts/${p.id}`} className={`font-mono ${linkClass}`}>
            {p.payoutNumber ?? "Payout"}
          </Link>
          <div className="text-xs text-muted-foreground">
            Up to {formatDate(p.asOfDate)} · {p.fyLabel}
            {p.legacy ? " · Phase 2" : ""}
          </div>
        </div>
      ),
    },
    {
      id: "status",
      header: "Status",
      cell: (p) => (
        <div className="flex flex-wrap items-center gap-1">
          <PayoutStatusBadge status={p.status} />
          {p.blocked ? <span className="text-xs font-medium text-danger">Receipt reversed</span> : null}
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
    { id: "commission", header: "Commission", sortKey: "commissionTotal", numeric: true, cell: (p) => <SignedAmount amount={p.commissionTotal} /> },
    {
      id: "tds",
      header: "TDS",
      numeric: true,
      cell: (p) =>
        p.tdsAmount !== null ? (
          <span className="font-mono tabular">{formatCurrency(p.tdsAmount)}</span>
        ) : (
          <span className="whitespace-nowrap text-xs text-warning-ink">{p.tdsStatus === "PAN_NOT_VERIFIED" ? "PAN not verified" : "Not calculated"}</span>
        ),
    },
    { id: "net", header: "Net", numeric: true, cell: (p) => <span className="font-mono tabular">{p.netAmount !== null ? formatCurrency(p.netAmount) : NOT_AVAILABLE}</span> },
    { id: "paid", header: "Paid on", sortKey: "paidDate", cell: (p) => <span className="whitespace-nowrap">{p.paidDate ? formatDate(p.paidDate) : NOT_AVAILABLE}</span> },
  ];

  return (
    <DataTable
      caption="Payouts"
      itemLabel="payouts"
      columns={columns}
      rows={payouts.data?.content}
      rowKey={(p) => p.id}
      loading={payouts.isPending}
      fetching={payouts.isFetching && !payouts.isPending}
      error={payouts.error}
      onRetry={() => payouts.refetch()}
      sort={sort}
      onSortChange={set(setSort)}
      page={payouts.data?.meta}
      onPageChange={setPage}
      pageSizeOptions={[20, 50, 100]}
      onPageSizeChange={set(setSize)}
      empty={
        <EmptyState title={filtered ? "No payouts match" : "No payouts yet"} onClearFilters={filtered ? clearFilters : undefined} action={!filtered ? createButton : undefined}>
          {filtered ? undefined : "Create a draft payout for a partner with eligible commission."}
        </EmptyState>
      }
      toolbar={
        <Toolbar label="Payout filters">
          <SearchInput placeholder="Search payouts" aria-label="Search payouts" value={search} onValueChange={set(setSearch)} />
          <Select aria-label="Filter by status" value={status} wrapperClassName="sm:w-44" onChange={(e) => set(setStatus)(e.target.value as PayoutStatus | "")}>
            <option value="">All statuses</option>
            {statuses.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </Select>
          <FilterDate label="From" aria-label="Created from" value={from} max={to || undefined} onChange={(e) => set(setFrom)(e.target.value)} className="sm:w-40" />
          <FilterDate label="To" aria-label="Created to" value={to} min={from || undefined} onChange={(e) => set(setTo)(e.target.value)} className="sm:w-40" />
          {canExport ? (
            <Button onClick={exportCsv} loading={exporting} loadingText="Exporting…">
              <Download aria-hidden="true" /> Export CSV
            </Button>
          ) : null}
        </Toolbar>
      }
    />
  );
}

function BalanceList({ canCreate, onCreate }: { canCreate: boolean; onCreate: (partnerId: string) => void }) {
  const { canRead } = useAuth();
  const [search, setSearch] = useState("");
  const q = useDebounce(search.trim());
  const balances = useQuery({
    queryKey: queryKeys.payouts.balances(q),
    queryFn: ({ signal }) => payoutService.balances(q, signal),
    placeholderData: keepPreviousData,
  });
  const canOpenPartner = canRead("PARTNERS");
  const columns: ColumnDef<PartnerPayoutBalance>[] = [
    {
      id: "partner",
      header: "Partner",
      cell: (b) => (
        <div className="min-w-40">
          {canOpenPartner ? (
            <Link to={`/partners/${b.partnerId}`} className={linkClass}>
              {b.partnerName}
            </Link>
          ) : (
            b.partnerName
          )}
          <div className="text-xs text-muted-foreground">
            {[!b.panOnFile ? "No PAN" : !b.panVerified ? "PAN not verified" : null, !b.bankOnFile ? "No bank details" : null].filter(Boolean).join(" · ") || "PAN verified · bank on file"}
          </div>
        </div>
      ),
    },
    {
      id: "eligible",
      header: "Eligible",
      numeric: true,
      cell: (b) => (
        <div>
          <span className="font-mono tabular">{formatCurrency(b.eligibleCommission)}</span>
          <div className="text-xs text-muted-foreground">{formatCount(b.eligibleReceipts, "receipt")}</div>
        </div>
      ),
    },
    {
      id: "adjustments",
      header: "Adjustments",
      numeric: true,
      cell: (b) => (
        <div>
          <SignedAmount amount={b.carriedAdjustments} />
          {b.pendingAdjustments > 0 ? <div className="text-xs text-muted-foreground">{formatNumber(b.pendingAdjustments)} awaiting approval</div> : null}
        </div>
      ),
    },
    { id: "progress", header: "In progress", numeric: true, cell: (b) => <span className="font-mono tabular">{formatCurrency(b.inProgress)}</span> },
    {
      id: "paid",
      header: "Paid this FY",
      numeric: true,
      cell: (b) => (
        <div>
          <span className="font-mono tabular">{formatCurrency(b.paidFy)}</span>
          <div className="text-xs text-muted-foreground">TDS {formatCurrency(b.tdsFy)}</div>
        </div>
      ),
    },
    { id: "last", header: "Last paid", cell: (b) => <span className="whitespace-nowrap">{b.lastPaidDate ? formatDate(b.lastPaidDate) : NOT_AVAILABLE}</span> },
    ...(canCreate
      ? [
          {
            id: "action",
            header: <span className="sr-only">Actions</span>,
            label: "Actions",
            cell: (b: PartnerPayoutBalance) =>
              b.inProgressPayouts === 0 && Number(b.eligibleCommission) + Number(b.carriedAdjustments) > 0 ? (
                <Button size="sm" onClick={() => onCreate(b.partnerId)}>
                  Create payout
                </Button>
              ) : null,
          } satisfies ColumnDef<PartnerPayoutBalance>,
        ]
      : []),
  ];
  return (
    <DataTable
      caption="Partner payout balances"
      itemLabel="partners"
      columns={columns}
      rows={balances.data}
      rowKey={(b) => b.partnerId}
      loading={balances.isPending}
      fetching={balances.isFetching && !balances.isPending}
      error={balances.error}
      onRetry={() => balances.refetch()}
      empty={<EmptyState title={q ? "No partners match" : "Nothing to pay"}>{q ? "Try another search." : "No partner has eligible commission, adjustments or payouts in progress."}</EmptyState>}
      toolbar={
        <Toolbar label="Balance filters">
          <SearchInput placeholder="Search partners" aria-label="Search partners" value={search} onValueChange={setSearch} />
        </Toolbar>
      }
    />
  );
}

function AdjustmentList({ canCreate, statuses }: { canCreate: boolean; statuses: { value: string; label: string }[] }) {
  const { canRead } = useAuth();
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<AdjustmentStatus | "">("");
  const [kind, setKind] = useState<AdjustmentKind | "">("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [creating, setCreating] = useState(false);
  const [deciding, setDeciding] = useState<{ adjustment: PartnerAdjustment; decision: "approve" | "reject" } | null>(null);
  const q = useDebounce(search.trim());
  const filters = { q, status, kind, page, size };
  const adjustments = useQuery({
    queryKey: queryKeys.payouts.adjustments(filters),
    queryFn: ({ signal }) => payoutService.adjustments(filters, signal),
    placeholderData: keepPreviousData,
  });
  const set =
    <T,>(setter: (value: T) => void) =>
    (value: T) => {
      setter(value);
      setPage(0);
    };
  const filtered = !!q || !!status || !!kind;
  const clearFilters = () => { setSearch(""); setStatus(""); setKind(""); setPage(0); };
  const newButton = canCreate ? (
    <Button onClick={() => setCreating(true)}>
      <Plus aria-hidden="true" /> New adjustment
    </Button>
  ) : undefined;

  const columns: ColumnDef<PartnerAdjustment>[] = [
    {
      id: "adjustment",
      header: "Adjustment",
      cell: (a) => (
        <div className="whitespace-nowrap">
          <span className="font-mono">{a.adjustmentNumber}</span>
          <div className="text-xs text-muted-foreground">
            {a.kindLabel} · {formatDate(a.createdAt)}
            {a.createdByName ? ` · ${a.createdByName}` : ""}
          </div>
        </div>
      ),
    },
    {
      id: "partner",
      header: "Partner",
      cell: (a) =>
        canRead("PARTNERS") ? (
          <Link to={`/partners/${a.partner.id}`} className={`whitespace-nowrap ${linkClass}`}>
            {a.partner.name}
          </Link>
        ) : (
          <span className="whitespace-nowrap">{a.partner.name}</span>
        ),
    },
    {
      id: "reason",
      header: "Reason",
      cell: (a) => (
        <div className="min-w-48 max-w-80">
          <div className="line-clamp-2">{a.reason}</div>
          {a.sourcePaymentNumber ? (
            canRead("PAYMENTS") && a.sourcePaymentId ? (
              <Link to={`/payments/${a.sourcePaymentId}`} className={`font-mono text-xs ${linkClass}`}>
                {a.sourcePaymentNumber}
              </Link>
            ) : (
              <span className="font-mono text-xs">{a.sourcePaymentNumber}</span>
            )
          ) : null}
          {a.decisionNote ? <div className="text-xs text-muted-foreground">Decision: {a.decisionNote}</div> : null}
        </div>
      ),
    },
    {
      id: "status",
      header: "Status",
      cell: (a) => (
        <div className="space-y-0.5">
          <StatusBadge domain="adjustment" status={a.status} />
          {a.allocatedTo ? (
            <div className="text-xs">
              In{" "}
              <Link to={`/payouts/${a.allocatedTo.id}`} className={`font-mono ${linkClass}`}>
                {a.allocatedTo.payoutNumber}
              </Link>
            </div>
          ) : a.status === "APPROVED" ? (
            <div className="text-xs text-muted-foreground">Carried to next payout</div>
          ) : null}
        </div>
      ),
    },
    { id: "amount", header: "Amount", numeric: true, cell: (a) => <SignedAmount amount={a.amount} /> },
    {
      id: "actions",
      header: <span className="sr-only">Actions</span>,
            label: "Actions",
      cell: (a) =>
        a.canDecide ? (
          <div className="flex gap-1">
            <Button size="sm" onClick={() => setDeciding({ adjustment: a, decision: "approve" })}>
              Approve
            </Button>
            <Button size="sm" variant="destructive-outline" onClick={() => setDeciding({ adjustment: a, decision: "reject" })}>
              Reject
            </Button>
          </div>
        ) : null,
    },
  ];

  return (
    <>
      <DataTable
        caption="Adjustments"
        itemLabel="adjustments"
        columns={columns}
        rows={adjustments.data?.content}
        rowKey={(a) => a.id}
        loading={adjustments.isPending}
        fetching={adjustments.isFetching && !adjustments.isPending}
        error={adjustments.error}
        onRetry={() => adjustments.refetch()}
        page={adjustments.data?.meta}
        onPageChange={setPage}
        pageSizeOptions={[20, 50, 100]}
        onPageSizeChange={set(setSize)}
        empty={
          <EmptyState title={filtered ? "No adjustments match" : "No adjustments"} onClearFilters={filtered ? clearFilters : undefined} action={!filtered ? newButton : undefined}>
            {filtered ? undefined : "Reversals of paid commission and manual corrections appear here."}
          </EmptyState>
        }
        toolbar={
          <Toolbar label="Adjustment filters">
            <SearchInput placeholder="Search adjustments" aria-label="Search adjustments" value={search} onValueChange={set(setSearch)} />
            <Select aria-label="Filter by status" value={status} wrapperClassName="sm:w-44" onChange={(e) => set(setStatus)(e.target.value as AdjustmentStatus | "")}>
              <option value="">All statuses</option>
              {statuses.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </Select>
            <Select aria-label="Filter by type" value={kind} wrapperClassName="sm:w-36" onChange={(e) => set(setKind)(e.target.value as AdjustmentKind | "")}>
              <option value="">All types</option>
              <option value="REVERSAL">Reversal</option>
              <option value="MANUAL">Manual</option>
            </Select>
            {newButton}
          </Toolbar>
        }
      />
      <NewAdjustmentDialog open={creating} onClose={() => setCreating(false)} />
      {deciding ? <DecideAdjustmentDialog adjustment={deciding.adjustment} decision={deciding.decision} onClose={() => setDeciding(null)} /> : null}
    </>
  );
}
