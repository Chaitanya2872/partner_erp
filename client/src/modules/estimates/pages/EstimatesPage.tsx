import { useState } from "react";
import { Link } from "react-router";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { Download, Pencil, Plus } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useDebounce } from "@/hooks/useDebounce";
import { toSortParam, type SortState } from "@/components/tables/sorting";
import { DataTable, type ColumnDef } from "@/components/tables/DataTable";
import { EmptyState } from "@/components/feedback/States";
import { PageActions, PageContainer, PageHeader, Toolbar } from "@/components/layout/Page";
import { SearchInput } from "@/components/forms/SearchInput";
import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Input";
import { SegmentedList, SegmentedTrigger, Tabs, TabsContent } from "@/components/ui/Tabs";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { saveTextFile } from "@/utils/download";
import { formatCurrency, formatDate } from "@/utils/format";
import { estimateService } from "../services/estimateService";
import { useEstimateMeta } from "../hooks/useEstimates";
import { EstimateStatusBadge } from "../components/EstimateStatusBadge";
import { EstimateArchive } from "../components/EstimateArchive";
import type { EstimateStatus, EstimateSummary } from "../types/estimate.types";

const linkClass = "font-medium text-accent-ink underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-ring";

/**
 * Estimate register (source vEstimates). Partner-safe columns only; the internal B2C copy is opened from an
 * estimate's page. Export follows ESTIMATES:EXPORT. The server enforces every permission.
 */
export function EstimatesPage() {
  const { canRead, canWrite, canPerform } = useAuth();
  const [view, setView] = useState<"active" | "archive">("active");
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<EstimateStatus | "">("");
  const [expired, setExpired] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState<SortState | null>(null);
  const [exporting, setExporting] = useState(false);
  const q = useDebounce(search.trim());

  const meta = useEstimateMeta();
  const filters = { q, status, expired, page, size, sort: toSortParam(sort) };
  const estimates = useQuery({
    queryKey: queryKeys.estimates.list(filters),
    queryFn: ({ signal }) => estimateService.list(filters, signal),
    placeholderData: keepPreviousData,
  });

  const canEdit = canWrite("ESTIMATES");
  const canExport = canPerform("ESTIMATES", "EXPORT");
  const canOpenLead = canRead("LEADS");
  const canOpenPartner = canRead("PARTNERS");
  const set =
    <T,>(setter: (value: T) => void) =>
    (value: T) => {
      setter(value);
      setPage(0);
    };
  const filtered = !!q || !!status || !!expired;
  const clearFilters = () => { setSearch(""); setStatus(""); setExpired(""); setPage(0); };

  const exportCsv = async () => {
    setExporting(true);
    try {
      const { text, filename } = await estimateService.exportCsv({ q, status, expired });
      saveTextFile(text, filename);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setExporting(false);
    }
  };

  const newEstimate = canEdit ? (
    <Button asChild variant="primary">
      <Link to="/estimates/new">
        <Plus aria-hidden="true" /> New estimate
      </Link>
    </Button>
  ) : undefined;

  const columns: ColumnDef<EstimateSummary>[] = [
    {
      id: "estimate",
      header: "Estimate",
      sortKey: "estimateNumber",
      cell: (e) => (
        <div className="whitespace-nowrap">
          <Link to={`/estimates/${e.id}`} className={`font-mono ${linkClass}`}>
            {e.estimateNumber}
          </Link>
          <div className="text-xs text-muted-foreground">{formatDate(e.estimateDate)}</div>
        </div>
      ),
    },
    {
      id: "client",
      header: "Client",
      cell: (e) => (
        <div className="min-w-40 max-w-64">
          <div className="truncate">{e.clientName}</div>
          <div className="truncate text-xs text-muted-foreground">
            {canOpenLead ? (
              <Link to={`/leads/${e.leadId}`} className="hover:underline">
                {e.leadNumber}
              </Link>
            ) : (
              e.leadNumber
            )}
            {e.billTo === "PARTNER" ? " · billed to partner" : ""}
          </div>
        </div>
      ),
    },
    {
      id: "partner",
      header: "Partner",
      cell: (e) =>
        canOpenPartner ? (
          <Link to={`/partners/${e.partner.id}`} className={`whitespace-nowrap ${linkClass}`}>
            {e.partner.name}
          </Link>
        ) : (
          <span className="whitespace-nowrap">{e.partner.name}</span>
        ),
    },
    { id: "status", header: "Status", cell: (e) => <EstimateStatusBadge status={e.status} expired={e.expired} /> },
    { id: "validUntil", header: "Valid until", sortKey: "validUntil", cell: (e) => <span className="whitespace-nowrap">{formatDate(e.validUntil)}</span> },
    {
      id: "total",
      header: "Total incl. GST",
      numeric: true,
      cell: (e) => <span className="whitespace-nowrap">{formatCurrency(e.grandTotal)}</span>,
    },
  ];

  return (
    <PageContainer>
      <PageHeader
        title="Estimates"
        description="Partner quotations with GST. Totals are calculated on the server."
        actions={
          canExport || newEstimate ? (
            <PageActions>
              {canExport ? (
                <Button onClick={exportCsv} loading={exporting} loadingText="Exporting…">
                  <Download aria-hidden="true" /> Export CSV
                </Button>
              ) : null}
              {newEstimate}
            </PageActions>
          ) : undefined
        }
      />

      <Tabs value={view} onValueChange={(next) => setView(next as "active" | "archive")} className="space-y-4">
        <SegmentedList aria-label="Estimates view">
          <SegmentedTrigger value="active">Active estimates</SegmentedTrigger>
          <SegmentedTrigger value="archive">Archive</SegmentedTrigger>
        </SegmentedList>
        <TabsContent value="active" className="pt-0">
      <DataTable
        caption="Estimates"
        itemLabel="estimates"
        columns={columns}
        rows={estimates.data?.content}
        rowKey={(e) => e.id}
        loading={estimates.isPending}
        fetching={estimates.isFetching && !estimates.isPending}
        error={estimates.error}
        onRetry={() => estimates.refetch()}
        sort={sort}
        onSortChange={set(setSort)}
        page={estimates.data?.meta}
        onPageChange={setPage}
        pageSizeOptions={[20, 50, 100]}
        onPageSizeChange={set(setSize)}
        rowActions={
          canEdit
            ? (e) =>
                // P8: invoiced estimates are locked (D-09).
                e.status === "INVOICED" ? null : (
                  <div className="flex justify-end">
                    <Button asChild size="sm" variant="outline">
                      <Link to={`/estimates/${e.id}/edit`} aria-label={`Edit ${e.estimateNumber}`}>
                        <Pencil aria-hidden="true" /> Edit
                      </Link>
                    </Button>
                  </div>
                )
            : undefined
        }
        empty={
          <EmptyState title={filtered ? "No estimates match" : "No estimates yet"} onClearFilters={filtered ? clearFilters : undefined} action={!filtered ? newEstimate : undefined}>
            {filtered ? undefined : "Create an estimate from a lead to quote the client or the partner."}
          </EmptyState>
        }
        toolbar={
          <Toolbar label="Estimate filters">
            <SearchInput placeholder="Search estimates" aria-label="Search estimates" value={search} onValueChange={set(setSearch)} />
            <Select aria-label="Filter by status" value={status} wrapperClassName="sm:w-40" onChange={(e) => set(setStatus)(e.target.value as EstimateStatus | "")}>
              <option value="">All statuses</option>
              {meta.data?.statuses.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </Select>
            <Select aria-label="Filter by validity" value={expired} wrapperClassName="sm:w-44" onChange={(e) => set(setExpired)(e.target.value)}>
              <option value="">Any validity</option>
              <option value="true">Expired</option>
              <option value="false">Not expired</option>
            </Select>
          </Toolbar>
        }
      />
        </TabsContent>
        <TabsContent value="archive" className="pt-0">
          <EstimateArchive />
        </TabsContent>
      </Tabs>
    </PageContainer>
  );
}
