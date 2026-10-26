import type { ReactNode } from "react";
import { Link } from "react-router";
import { BadgeCheck, Pencil, Trash2 } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { DataTable, type ColumnDef } from "@/components/tables/DataTable";
import type { SortState } from "@/components/tables/sorting";
import type { PageMeta } from "@/types/api.types";
import { Button } from "@/components/ui/Button";
import { formatCurrency } from "@/utils/format";
import { DuplicateBadge, RegistrationSummary, SlaPills } from "./LeadBadges";
import { StageSelect } from "./StageSelect";
import type { useLeadActions } from "./LeadDialogs";
import { isClosed, type LeadMeta, type LeadSummary } from "../types/lead.types";

const linkClass = "font-medium text-accent-ink underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-ring";

interface LeadTableProps {
  caption: string;
  rows: LeadSummary[] | undefined;
  meta: LeadMeta | undefined;
  actions: ReturnType<typeof useLeadActions>;
  loading?: boolean;
  fetching?: boolean;
  error?: unknown;
  onRetry?: () => void;
  /** Hidden on a partner's own page. */
  showPartner?: boolean;
  sort?: SortState | null;
  onSortChange?: (sort: SortState) => void;
  page?: PageMeta;
  onPageChange?: (page: number) => void;
  onPageSizeChange?: (size: number) => void;
  toolbar?: ReactNode;
  empty?: ReactNode;
}

/** Lead list columns from the source (BR-L9): client/project, partner, inline stage, budget, registration, SLA. */
export function LeadTable({ caption, rows, meta, actions, showPartner = true, ...table }: LeadTableProps) {
  const { canRead, canWrite, canPerform } = useAuth();
  const canEdit = canWrite("LEADS");
  const canApprove = canPerform("LEADS", "APPROVE");
  const canDelete = canPerform("LEADS", "DELETE");
  const canOpenPartner = canRead("PARTNERS");

  const columns: ColumnDef<LeadSummary>[] = [
    {
      id: "client",
      header: "Client / project",
      sortKey: "clientName",
      cell: (l) => (
        <div className="min-w-48 max-w-72">
          <Link to={`/leads/${l.id}`} className={`block truncate ${linkClass}`}>
            {l.clientName}
          </Link>
          <div className="truncate text-xs text-muted-foreground">
            {[l.leadNumber, l.projectTypeLabel, l.units ? `${l.units} units` : null, l.city].filter(Boolean).join(" · ")}
          </div>
          {l.conflict ? (
            <div className="mt-1">
              <DuplicateBadge />
            </div>
          ) : null}
        </div>
      ),
    },
    ...(showPartner
      ? [
          {
            id: "partner",
            header: "Partner",
            cell: (l: LeadSummary) =>
              canOpenPartner ? (
                <Link to={`/partners/${l.partner.id}`} className={`whitespace-nowrap ${linkClass}`}>
                  {l.partner.name}
                </Link>
              ) : (
                <span className="whitespace-nowrap">{l.partner.name}</span>
              ),
          },
        ]
      : []),
    {
      id: "stage",
      header: "Stage",
      cell: (l) => (
        <StageSelect
          stage={l.stage}
          stages={meta?.stages ?? [{ code: l.stage, label: l.stageLabel }]}
          label={`Stage for ${l.clientName}`}
          canEdit={canEdit}
          canApprove={canApprove}
          busy={actions.stagePendingId === l.id}
          onChange={(stage) => actions.requestStage(l, stage)}
        />
      ),
    },
    {
      id: "budget",
      header: "Budget",
      sortKey: "budget",
      numeric: true,
      cell: (l) => <span className="whitespace-nowrap">{l.budget === null ? "—" : formatCurrency(l.budget, { whole: true })}</span>,
    },
    {
      id: "registration",
      header: "Registration",
      cell: (l) =>
        l.acknowledgedAt ? (
          <RegistrationSummary lead={l} />
        ) : canApprove && !isClosed(l.stage) ? (
          <Button size="sm" variant="primary" onClick={() => actions.requestAcknowledge(l)} aria-label={`Acknowledge ${l.clientName}`}>
            <BadgeCheck aria-hidden="true" /> Acknowledge
          </Button>
        ) : (
          // P6-13: Won and Lost leads are reopened before they can be acknowledged.
          <span
            className="whitespace-nowrap text-sm text-muted-foreground"
            title={canApprove && isClosed(l.stage) ? "Reopen the lead to acknowledge it." : undefined}
          >
            Not acknowledged
          </span>
        ),
    },
    { id: "sla", header: "SLA", cell: (l) => <SlaPills sla={l.sla} /> },
  ];

  return (
    <DataTable
      caption={caption}
      itemLabel="leads"
      columns={columns}
      rows={rows}
      rowKey={(l) => l.id}
      pageSizeOptions={[20, 50, 100]}
      rowActions={
        canEdit || canDelete
          ? (l) => (
              <div className="flex justify-end gap-1.5">
                {canEdit ? (
                  <Button asChild size="sm" variant="outline">
                    <Link to={`/leads/${l.id}/edit`} aria-label={`Edit ${l.clientName}`}>
                      <Pencil aria-hidden="true" /> Edit
                    </Link>
                  </Button>
                ) : null}
                {canDelete ? (
                  <Button size="icon-sm" variant="destructive-outline" onClick={() => actions.requestDelete(l)} aria-label={`Delete ${l.clientName}`}>
                    <Trash2 aria-hidden="true" />
                  </Button>
                ) : null}
              </div>
            )
          : undefined
      }
      {...table}
    />
  );
}
