import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { Download } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useDebounce } from "@/hooks/useDebounce";
import { toSortParam, type SortState } from "@/components/tables/sorting";
import { DataTable, type ColumnDef } from "@/components/tables/DataTable";
import { EmptyState, ErrorState, LoadingState } from "@/components/feedback/States";
import { PageActions, PageContainer, PageHeader, Toolbar } from "@/components/layout/Page";
import { SummaryPanel } from "@/components/dashboard/Dashboard";
import { SearchInput } from "@/components/forms/SearchInput";
import { SearchableSelect } from "@/components/forms/SearchableSelect";
import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Input";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { saveTextFile } from "@/utils/download";
import { formatCurrency, formatDate, formatNumber, formatPercent, humanizeCode, NOT_AVAILABLE } from "@/utils/format";
import { PeriodFilter } from "@/modules/dashboard/components/PeriodFilter";
import { Unavailable } from "@/modules/dashboard/components/DashboardStats";
import { usePeriod } from "@/modules/dashboard/hooks/usePeriod";
import { reportService } from "../services/reportService";
import type { ReportColumn, ReportInfo, ReportRow, ReportValue } from "../types/report.types";

const linkClass = "font-medium text-accent-ink underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-ring";

const REASONS: Record<string, string> = {
  NO_ACCESS: "This report needs read access to its module.",
  SCOPE_SELF: "Reports cover all records; your access to this module is limited to your own records.",
  SCOPE_TEAM: "Reports cover all records; your access to this module is limited to your team's records.",
};

function formatCell(column: ReportColumn, value: ReportValue, info: ReportInfo): string {
  if (value === null || value === undefined || value === "") return NOT_AVAILABLE;
  if (column.key === info.statusColumn) return info.statuses.find((s) => s.value === value)?.label ?? String(value);
  switch (column.type) {
    case "MONEY":
      return formatCurrency(value as string | number);
    case "NUMBER":
      return formatNumber(value as string | number);
    case "PERCENT":
      return formatPercent(value as string | number);
    case "DATE":
      return formatDate(String(value));
    case "BOOLEAN":
      return value ? "Yes" : "No";
    case "CODE":
      return codeLabel(String(value));
    default:
      return String(value);
  }
}

/** Labels for codes whose humanized form would read badly; other upper-case codes are humanized, anything else kept. */
const CODE_LABELS: Record<string, string> = {
  NOT_DUE: "Not due", D1_30: "1–30 days", D31_60: "31–60 days", D61_90: "61–90 days", D90_PLUS: "90+ days",
  UPI: "UPI", NEFT_RTGS: "NEFT / RTGS", TDS: "TDS",
};
const codeLabel = (code: string) => CODE_LABELS[code] ?? (/^[A-Z][A-Z_]*$/.test(code) ? humanizeCode(code) : code);

const numeric = (c: ReportColumn) => c.type === "MONEY" || c.type === "NUMBER" || c.type === "PERCENT";

/**
 * Reports (Phase 12): ten server-side reports with period, status, partner and search filters, sorting, pagination,
 * totals over every matching row and CSV export. The server lists only the columns the user may see and refuses a
 * report or export the user's permissions don't cover; the page only mirrors that.
 */
export function ReportsPage() {
  const { canRead } = useAuth();
  const [params, setParams] = useSearchParams();
  const { period, setPeriod, complete } = usePeriod();
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [partnerId, setPartnerId] = useState("");
  const [partnerLabel, setPartnerLabel] = useState<{ value: string; label: string; description?: string } | null>(null);
  const [partnerQuery, setPartnerQuery] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [sort, setSort] = useState<SortState | null>(null);
  const [exporting, setExporting] = useState(false);
  const q = useDebounce(search.trim());
  const pq = useDebounce(partnerQuery.trim());

  const catalog = useQuery({ queryKey: queryKeys.reports.catalog, queryFn: ({ signal }) => reportService.catalog(signal) });
  const reports = catalog.data ?? [];
  const requested = params.get("report");
  const info = reports.find((r) => r.code === requested) ?? reports.find((r) => r.available) ?? reports[0];

  const reset = () => {
    setPage(0);
  };
  const choose = (code: string) => {
    setParams(
      (current) => {
        const out = new URLSearchParams(current);
        out.set("report", code);
        return out;
      },
      { replace: true },
    );
    setStatus("");
    setPartnerId("");
    setSearch("");
    setSort(null);
    setPage(0);
  };

  const criteria = { preset: period.preset, from: period.from, to: period.to, status, partnerId, q, sort: toSortParam(sort) };
  const filters = { ...criteria, page, size };
  const result = useQuery({
    queryKey: queryKeys.reports.run(info?.code ?? "", filters),
    queryFn: ({ signal }) => reportService.run(info!.code, filters, signal),
    enabled: !!info?.available && complete,
    placeholderData: keepPreviousData,
  });
  const showPartnerFilter = !!info?.partnerFilter && canRead("PARTNERS");
  const partners = useQuery({
    queryKey: queryKeys.reports.partnerOptions(pq),
    queryFn: ({ signal }) => reportService.partnerOptions(pq, signal),
    enabled: showPartnerFilter,
    placeholderData: keepPreviousData,
  });

  const exportCsv = async () => {
    if (!info) return;
    setExporting(true);
    try {
      const { text, filename } = await reportService.exportCsv(info.code, criteria);
      saveTextFile(text, filename);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setExporting(false);
    }
  };

  if (catalog.isPending) return <LoadingState label="Loading reports…" />;
  if (catalog.isError) {
    return (
      <PageContainer>
        <PageHeader title="Reports" />
        <ErrorState error={catalog.error} onRetry={() => catalog.refetch()} />
      </PageContainer>
    );
  }

  const data = result.data;
  const visibleColumns = data?.columns ?? info?.columns ?? [];
  const columns: ColumnDef<ReportRow>[] = visibleColumns.map((c, i) => ({
    id: c.key,
    header: c.label,
    numeric: numeric(c),
    sortKey: c.sortable ? c.key : undefined,
    hideable: i > 0,
    cell: (row) => {
      const text = formatCell(c, row[c.key], info!);
      // The detail link goes on the first column that has a value (a partner without a code links on its name).
      const linkKey = visibleColumns.find((v) => row[v.key] !== null && row[v.key] !== "")?.key;
      if (c.key === linkKey && row.link) {
        return (
          <Link to={row.link} className={`whitespace-nowrap ${linkClass}`}>
            {text}
          </Link>
        );
      }
      return <span className={c.type === "CODE" || c.type === "MASKED" ? "whitespace-nowrap font-mono text-xs" : numeric(c) || c.type === "DATE" ? "whitespace-nowrap" : undefined}>{text}</span>;
    },
  }));
  const totals = data ? data.columns.filter((c) => c.total && data.totals[c.key] !== undefined) : [];
  const filtered = !!q || !!status || !!partnerId;
  const fetched = (partners.data ?? []).map((p) => ({ value: p.id, label: p.name, description: p.code ?? undefined }));
  // Keep the chosen partner selectable while the search shows other names.
  const partnerOptions = partnerLabel && partnerId === partnerLabel.value && !fetched.some((o) => o.value === partnerId) ? [partnerLabel, ...fetched] : fetched;

  return (
    <PageContainer>
      <PageHeader
        title="Reports"
        description="Organisation-wide reports for a period in IST. Totals cover every matching row; exports follow the same filters and permissions."
        actions={
          info?.available && info.canExport ? (
            <PageActions>
              <Button onClick={exportCsv} loading={exporting} loadingText="Exporting…" disabled={!complete}>
                <Download aria-hidden="true" /> Export CSV
              </Button>
            </PageActions>
          ) : undefined
        }
      />

      <div className="flex flex-wrap items-start gap-3">
        <Select aria-label="Report" value={info?.code ?? ""} wrapperClassName="w-full sm:w-72" onChange={(e) => choose(e.target.value)}>
          {reports.map((r) => (
            <option key={r.code} value={r.code} disabled={!r.available}>
              {r.title}
              {r.available ? "" : " (not available)"}
            </option>
          ))}
        </Select>
        <PeriodFilter
          value={period}
          onChange={(p) => {
            setPeriod(p);
            reset();
          }}
          resolved={complete ? data?.period : undefined}
          idPrefix="report-period"
        />
      </div>
      {info ? (
        <p className="text-sm text-muted-foreground">
          {info.description} <span>Period applies to: {info.dateBasis.charAt(0).toLowerCase() + info.dateBasis.slice(1)}.</span>
        </p>
      ) : null}

      {!info ? (
        <EmptyState title="No reports">No reports are available.</EmptyState>
      ) : !info.available ? (
        <Unavailable title={`${info.title} is not available`}>{REASONS[info.reason ?? ""] ?? "You don't have access to this report."}</Unavailable>
      ) : (
        <>
          {totals.length > 0 && data ? (
            <SummaryPanel
              title={`Totals · ${formatNumber(data.totalElements)} rows`}
              rows={totals.map((c) => ({ label: c.label, value: formatCell(c, data.totals[c.key], info) }))}
            />
          ) : null}
          <DataTable
            caption={info.title}
            itemLabel="rows"
            columns={columns}
            rows={data?.rows}
            rowKey={(r) => r.id}
            loading={result.isPending && complete}
            fetching={result.isFetching && !result.isPending}
            error={result.error}
            onRetry={() => result.refetch()}
            sort={sort}
            onSortChange={(s) => {
              setSort(s);
              reset();
            }}
            page={data ? { page: data.page, size: data.size, totalElements: data.totalElements, totalPages: data.totalPages } : undefined}
            onPageChange={setPage}
            pageSizeOptions={[25, 50, 100, 200]}
            onPageSizeChange={(s) => {
              setSize(s);
              reset();
            }}
            empty={
              <EmptyState title={filtered ? "No rows match" : "Nothing in this period"}>
                {filtered ? "Try other filters." : "Choose another period."}
              </EmptyState>
            }
            toolbar={
              info.searchColumns.length > 0 || info.statuses.length > 0 || showPartnerFilter ? (
                <Toolbar label="Report filters">
                  {info.searchColumns.length > 0 ? (
                    <SearchInput
                      placeholder={`Search ${info.title.toLowerCase()}`}
                      aria-label={`Search ${info.title}`}
                      value={search}
                      onValueChange={(v) => {
                        setSearch(v);
                        reset();
                      }}
                    />
                  ) : null}
                  {info.statuses.length > 0 ? (
                    <Select
                      aria-label="Filter by status"
                      value={status}
                      wrapperClassName="sm:w-44"
                      onChange={(e) => {
                        setStatus(e.target.value);
                        reset();
                      }}
                    >
                      <option value="">Any status</option>
                      {info.statuses.map((s) => (
                        <option key={s.value} value={s.value}>
                          {s.label}
                        </option>
                      ))}
                    </Select>
                  ) : null}
                  {showPartnerFilter ? (
                    <SearchableSelect
                      aria-label="Filter by partner"
                      className="sm:w-56"
                      placeholder="Any partner"
                      searchPlaceholder="Search partners"
                      clearLabel="Any partner"
                      options={partnerOptions}
                      value={partnerId}
                      loading={partners.isFetching}
                      onQueryChange={setPartnerQuery}
                      onChange={(v) => {
                        setPartnerId(v);
                        setPartnerLabel(partnerOptions.find((o) => o.value === v) ?? null);
                        reset();
                      }}
                    />
                  ) : null}
                </Toolbar>
              ) : undefined
            }
          />
        </>
      )}
    </PageContainer>
  );
}
