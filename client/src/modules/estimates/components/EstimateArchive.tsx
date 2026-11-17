import { useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArchiveRestore, Eye } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useDebounce } from "@/hooks/useDebounce";
import { toSortParam, type SortState } from "@/components/tables/sorting";
import { DataTable, type ColumnDef } from "@/components/tables/DataTable";
import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";
import { ArchiveField, ArchiveReasonCell, ArchiveStamp } from "@/components/feedback/ArchiveDialog";
import { EmptyState, ErrorState, LoadingState } from "@/components/feedback/States";
import { Toolbar } from "@/components/layout/Page";
import { SearchInput } from "@/components/forms/SearchInput";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { formatCurrency, formatDate, formatDateTime, formatPercent } from "@/utils/format";
import { estimateService } from "../services/estimateService";
import { EstimateStatusBadge } from "./EstimateStatusBadge";
import type { EstimateArchiveEntry } from "../types/estimate.types";

/**
 * The estimate archive: estimates that were archived with a reason. Records here are read-only; View shows the
 * partner copy's items with the reason, who and when, and Restore (Estimates approve or delete) brings one back.
 */
export function EstimateArchive() {
  const { canPerform } = useAuth();
  const canRestore = canPerform("ESTIMATES", "APPROVE") || canPerform("ESTIMATES", "DELETE");
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState<SortState>({ key: "deletedAt", direction: "desc" });
  const [viewId, setViewId] = useState<string | null>(null);
  const [restoreTarget, setRestoreTarget] = useState<{ id: string; estimateNumber: string } | null>(null);
  const q = useDebounce(search.trim());

  const filters = { q, page, size, sort: toSortParam(sort) };
  const archive = useQuery({
    queryKey: queryKeys.estimates.archive(filters),
    queryFn: ({ signal }) => estimateService.archive(filters, signal),
    placeholderData: keepPreviousData,
  });
  const detail = useQuery({
    queryKey: queryKeys.estimates.archived(viewId ?? ""),
    queryFn: ({ signal }) => estimateService.archived(viewId!, signal),
    enabled: !!viewId,
  });

  const restore = useMutation({
    mutationFn: (estimate: { id: string }) => estimateService.restore(estimate.id),
    onSuccess: async (restored) => {
      toast.success(`${restored.estimateNumber} restored to Estimates.`);
      setRestoreTarget(null);
      setViewId(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.estimates.all }),
        queryClient.invalidateQueries({ queryKey: queryKeys.leads.all }),
      ]);
    },
    onError: (error) => {
      toast.error(errorMessage(error));
      setRestoreTarget(null);
    },
  });

  const set =
    <T,>(setter: (value: T) => void) =>
    (value: T) => {
      setter(value);
      setPage(0);
    };

  const columns: ColumnDef<EstimateArchiveEntry>[] = [
    {
      id: "estimate",
      header: "Estimate",
      sortKey: "estimateNumber",
      cell: ({ estimate: e }) => (
        <div className="whitespace-nowrap">
          <div className="font-mono font-medium">{e.estimateNumber}</div>
          <div className="text-xs text-muted-foreground">{formatDate(e.estimateDate)}</div>
        </div>
      ),
    },
    {
      id: "client",
      header: "Client",
      cell: ({ estimate: e }) => (
        <div className="min-w-40 max-w-64">
          <div className="truncate">{e.clientName}</div>
          <div className="truncate text-xs text-muted-foreground">
            {e.leadNumber}
            {e.billTo === "PARTNER" ? " · billed to partner" : ""}
          </div>
        </div>
      ),
    },
    { id: "partner", header: "Partner", cell: ({ estimate: e }) => <span className="whitespace-nowrap">{e.partner.name}</span> },
    { id: "status", header: "Status", cell: ({ estimate: e }) => <EstimateStatusBadge status={e.status} expired={false} /> },
    { id: "total", header: "Total incl. GST", numeric: true, cell: ({ estimate: e }) => <span className="whitespace-nowrap">{formatCurrency(e.grandTotal)}</span> },
    { id: "deletedAt", header: "Deleted on", sortKey: "deletedAt", cell: (a) => <span className="whitespace-nowrap">{formatDateTime(a.deletedAt)}</span> },
    { id: "deletedBy", header: "Deleted by", cell: (a) => <span className="whitespace-nowrap">{a.deletedByName ?? "—"}</span> },
    { id: "reason", header: "Reason", cell: (a) => <ArchiveReasonCell reason={a.deleteReason} /> },
  ];

  const view = detail.data;
  return (
    <>
      <DataTable
        caption="Archived estimates"
        itemLabel="archived estimates"
        columns={columns}
        rows={archive.data?.content}
        rowKey={(a) => a.estimate.id}
        loading={archive.isPending}
        fetching={archive.isFetching && !archive.isPending}
        error={archive.error}
        onRetry={() => archive.refetch()}
        sort={sort}
        onSortChange={set(setSort)}
        page={archive.data?.meta}
        onPageChange={setPage}
        pageSizeOptions={[20, 50, 100]}
        onPageSizeChange={set(setSize)}
        rowActions={({ estimate: e }) => (
          <div className="flex justify-end gap-1.5">
            <Button size="sm" variant="outline" onClick={() => setViewId(e.id)} aria-label={`View archived ${e.estimateNumber}`}>
              <Eye aria-hidden="true" /> View
            </Button>
            {canRestore ? (
              <Button size="sm" onClick={() => setRestoreTarget(e)} aria-label={`Restore ${e.estimateNumber}`}>
                <ArchiveRestore aria-hidden="true" /> Restore
              </Button>
            ) : null}
          </div>
        )}
        empty={
          <EmptyState title={q ? "No archived estimates match" : "The archive is empty"} onClearFilters={q ? () => set(setSearch)("") : undefined}>
            {q ? undefined : "Archived estimates appear here with the reason, who archived them and when."}
          </EmptyState>
        }
        toolbar={
          <Toolbar label="Archive filters">
            <SearchInput placeholder="Search archived estimates" aria-label="Search archived estimates" value={search} onValueChange={set(setSearch)} />
          </Toolbar>
        }
      />

      <Dialog
        open={!!viewId}
        onOpenChange={(next) => !next && setViewId(null)}
        title={view ? `Archived estimate: ${view.estimate.estimateNumber}` : "Archived estimate"}
        wide
        footer={
          <>
            <Button onClick={() => setViewId(null)}>Close</Button>
            {view && canRestore ? (
              <Button variant="primary" onClick={() => setRestoreTarget(view.estimate)}>
                <ArchiveRestore aria-hidden="true" /> Restore
              </Button>
            ) : null}
          </>
        }
      >
        {detail.isPending ? (
          <LoadingState label="Loading archived estimate…" />
        ) : detail.isError ? (
          <ErrorState error={detail.error} onRetry={() => detail.refetch()} />
        ) : view ? (
          <div className="space-y-3 text-sm">
            <ArchiveStamp deletedAt={view.deletedAt} deletedByName={view.deletedByName} deleteReason={view.deleteReason} />
            <p className="text-muted-foreground">Read-only. Restore the estimate to work on it again.</p>
            <dl className="grid gap-x-4 gap-y-2 sm:grid-cols-3">
              <ArchiveField label="Status" value={view.estimate.statusLabel} />
              <ArchiveField label="Client" value={view.estimate.lead.clientName} />
              <ArchiveField label="Lead" value={view.estimate.lead.leadNumber} />
              <ArchiveField label="Partner" value={view.estimate.partner.name} />
              <ArchiveField label="Estimate date" value={formatDate(view.estimate.estimateDate)} />
              <ArchiveField label="Valid until" value={formatDate(view.estimate.validUntil)} />
              <ArchiveField label="Discount" value={formatPercent(view.estimate.discountPercent)} />
              <ArchiveField label="Total incl. GST" value={formatCurrency(view.estimate.totals.grandTotal)} />
            </dl>
            <div className="overflow-x-auto rounded-lg border border-border">
              <table className="w-full min-w-[560px] text-sm">
                <caption className="sr-only">Items of the archived estimate</caption>
                <thead className="bg-surface-sunken text-left text-xs text-muted-foreground">
                  <tr>
                    <th scope="col" className="px-3 py-2 font-medium">Item</th>
                    <th scope="col" className="px-3 py-2 text-right font-medium">Qty</th>
                    <th scope="col" className="px-3 py-2 text-right font-medium">Rate</th>
                    <th scope="col" className="px-3 py-2 text-right font-medium">GST</th>
                    <th scope="col" className="px-3 py-2 text-right font-medium">Amount</th>
                  </tr>
                </thead>
                <tbody>
                  {view.estimate.lines.map((l) => (
                    <tr key={l.id} className="border-t border-border">
                      <td className="px-3 py-2">
                        {l.description}
                        {l.kind === "INSTALLATION" ? <span className="text-xs text-muted-foreground"> · installation</span> : null}
                      </td>
                      <td className="px-3 py-2 text-right font-mono tabular">{l.quantity}</td>
                      <td className="px-3 py-2 text-right font-mono tabular">{formatCurrency(l.unitPrice)}</td>
                      <td className="px-3 py-2 text-right font-mono tabular">{formatPercent(l.gstPercent)}</td>
                      <td className="px-3 py-2 text-right font-mono tabular">{formatCurrency(l.amount)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        ) : null}
      </Dialog>

      <ConfirmDialog
        open={!!restoreTarget}
        title="Restore estimate?"
        confirmLabel="Restore estimate"
        busy={restore.isPending}
        onCancel={() => setRestoreTarget(null)}
        onConfirm={() => restoreTarget && restore.mutate(restoreTarget)}
      >
        {restoreTarget ? (
          <p>
            {restoreTarget.estimateNumber} returns to the estimate register with its status unchanged. It can&apos;t be restored while its lead is
            archived.
          </p>
        ) : null}
      </ConfirmDialog>
    </>
  );
}
