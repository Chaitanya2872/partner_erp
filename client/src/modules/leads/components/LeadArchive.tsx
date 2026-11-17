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
import { formatCurrency, formatDateTime } from "@/utils/format";
import { leadService } from "../services/leadService";
import type { LeadArchiveEntry } from "../types/lead.types";

/**
 * The lead archive: leads that were archived with a reason. Records here are read-only; View shows the details with
 * the reason, who and when, and Restore (Leads approve or delete) brings a lead back.
 */
export function LeadArchive() {
  const { canPerform } = useAuth();
  const canRestore = canPerform("LEADS", "APPROVE") || canPerform("LEADS", "DELETE");
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState<SortState>({ key: "deletedAt", direction: "desc" });
  const [viewId, setViewId] = useState<string | null>(null);
  const [restoreTarget, setRestoreTarget] = useState<{ id: string; clientName: string } | null>(null);
  const q = useDebounce(search.trim());

  const filters = { q, page, size, sort: toSortParam(sort) };
  const archive = useQuery({
    queryKey: queryKeys.leads.archive(filters),
    queryFn: ({ signal }) => leadService.archive(filters, signal),
    placeholderData: keepPreviousData,
  });
  const detail = useQuery({
    queryKey: queryKeys.leads.archived(viewId ?? ""),
    queryFn: ({ signal }) => leadService.archived(viewId!, signal),
    enabled: !!viewId,
  });

  const restore = useMutation({
    mutationFn: (lead: { id: string }) => leadService.restore(lead.id),
    onSuccess: async (restored) => {
      toast.success(`“${restored.clientName}” restored to Leads.`);
      setRestoreTarget(null);
      setViewId(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.leads.all }),
        queryClient.invalidateQueries({ queryKey: queryKeys.partners.all }),
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

  const columns: ColumnDef<LeadArchiveEntry>[] = [
    {
      id: "client",
      header: "Client / project",
      sortKey: "clientName",
      cell: (l) => (
        <div className="min-w-48 max-w-72">
          <div className="truncate font-medium">{l.clientName}</div>
          <div className="truncate text-xs text-muted-foreground">{[l.leadNumber, l.projectTypeLabel, l.city].filter(Boolean).join(" · ")}</div>
        </div>
      ),
    },
    { id: "partner", header: "Partner", cell: (l) => <span className="whitespace-nowrap">{l.partner.name}</span> },
    { id: "stage", header: "Stage", cell: (l) => <span className="whitespace-nowrap">{l.stageLabel}</span> },
    { id: "deletedAt", header: "Deleted on", sortKey: "deletedAt", cell: (l) => <span className="whitespace-nowrap">{formatDateTime(l.deletedAt)}</span> },
    { id: "deletedBy", header: "Deleted by", cell: (l) => <span className="whitespace-nowrap">{l.deletedByName ?? "—"}</span> },
    { id: "reason", header: "Reason", cell: (l) => <ArchiveReasonCell reason={l.deleteReason} /> },
  ];

  const view = detail.data;
  return (
    <>
      <DataTable
        caption="Archived leads"
        itemLabel="archived leads"
        columns={columns}
        rows={archive.data?.content}
        rowKey={(l) => l.id}
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
        rowActions={(l) => (
          <div className="flex justify-end gap-1.5">
            <Button size="sm" variant="outline" onClick={() => setViewId(l.id)} aria-label={`View archived ${l.clientName}`}>
              <Eye aria-hidden="true" /> View
            </Button>
            {canRestore ? (
              <Button size="sm" onClick={() => setRestoreTarget(l)} aria-label={`Restore ${l.clientName}`}>
                <ArchiveRestore aria-hidden="true" /> Restore
              </Button>
            ) : null}
          </div>
        )}
        empty={
          <EmptyState title={q ? "No archived leads match" : "The archive is empty"} onClearFilters={q ? () => set(setSearch)("") : undefined}>
            {q ? undefined : "Archived leads appear here with the reason, who archived them and when."}
          </EmptyState>
        }
        toolbar={
          <Toolbar label="Archive filters">
            <SearchInput placeholder="Search archived leads" aria-label="Search archived leads" value={search} onValueChange={set(setSearch)} />
          </Toolbar>
        }
      />

      <Dialog
        open={!!viewId}
        onOpenChange={(next) => !next && setViewId(null)}
        title={view ? `Archived lead: ${view.lead.clientName}` : "Archived lead"}
        footer={
          <>
            <Button onClick={() => setViewId(null)}>Close</Button>
            {view && canRestore ? (
              <Button variant="primary" onClick={() => setRestoreTarget(view.lead)}>
                <ArchiveRestore aria-hidden="true" /> Restore
              </Button>
            ) : null}
          </>
        }
      >
        {detail.isPending ? (
          <LoadingState label="Loading archived lead…" />
        ) : detail.isError ? (
          <ErrorState error={detail.error} onRetry={() => detail.refetch()} />
        ) : view ? (
          <div className="space-y-3 text-sm">
            <ArchiveStamp deletedAt={view.deletedAt} deletedByName={view.deletedByName} deleteReason={view.deleteReason} />
            <p className="text-muted-foreground">Read-only. Restore the lead to work on it again.</p>
            <dl className="grid gap-x-4 gap-y-2 sm:grid-cols-2">
              <ArchiveField label="Lead number" value={view.lead.leadNumber} />
              <ArchiveField label="Stage" value={view.lead.stageLabel} />
              <ArchiveField label="Client" value={view.lead.clientName} />
              <ArchiveField label="Phone" value={view.lead.clientPhone} />
              <ArchiveField label="Email" value={view.lead.clientEmail} />
              <ArchiveField label="City" value={view.lead.city} />
              <ArchiveField label="Partner" value={view.lead.partner.name} />
              <ArchiveField label="Project" value={view.lead.projectTypeOther ?? view.lead.projectTypeLabel} />
              <ArchiveField label="Budget" value={view.lead.budget === null ? null : formatCurrency(view.lead.budget, { whole: true })} />
              <ArchiveField label="Created" value={formatDateTime(view.lead.createdAt)} />
            </dl>
            {view.lead.notes ? <p className="whitespace-pre-wrap">{view.lead.notes}</p> : null}
          </div>
        ) : null}
      </Dialog>

      <ConfirmDialog
        open={!!restoreTarget}
        title="Restore lead?"
        confirmLabel="Restore lead"
        busy={restore.isPending}
        onCancel={() => setRestoreTarget(null)}
        onConfirm={() => restoreTarget && restore.mutate(restoreTarget)}
      >
        {restoreTarget ? (
          <p>
            “{restoreTarget.clientName}” returns to the lead lists and protects its phone number again. If the same number is protected for another
            partner, it is flagged as a possible duplicate.
          </p>
        ) : null}
      </ConfirmDialog>
    </>
  );
}
