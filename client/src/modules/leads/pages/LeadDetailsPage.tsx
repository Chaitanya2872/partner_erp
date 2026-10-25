import { Link, useNavigate, useParams } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { BadgeCheck, Pencil, Trash2 } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { ErrorState, LoadingState } from "@/components/feedback/States";
import { ContentCard, PageActions, PageContainer, PageHeader } from "@/components/layout/Page";
import { DescriptionList, DetailRow, Timeline, type TimelineEntry } from "@/components/data/DataDisplay";
import { StatusBadge } from "@/components/data/StatusBadge";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { queryKeys } from "@/lib/queryKeys";
import { formatCurrency, formatDate, formatDateTime, formatPercent, NOT_AVAILABLE } from "@/utils/format";
import { leadService } from "../services/leadService";
import { useLead, useLeadMeta } from "../hooks/useLeads";
import { useLeadActions } from "../components/LeadDialogs";
import { DuplicateBadge, SlaPills } from "../components/LeadBadges";
import { protectionText } from "../utils/leadDisplay";
import { StageSelect } from "../components/StageSelect";
import { isClosed, type Lead, type LeadHistoryEntry } from "../types/lead.types";

const HISTORY_TONE: Record<LeadHistoryEntry["event"], TimelineEntry["tone"]> = {
  CREATED: "neutral",
  STAGE_CHANGED: "info",
  ACKNOWLEDGED: "success",
  AUTO_STAGE: "info",
};

export function LeadDetailsPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const { canRead, canWrite, canPerform } = useAuth();
  const lead = useLead(id);
  const meta = useLeadMeta();
  const history = useQuery({
    queryKey: queryKeys.leads.history(id),
    queryFn: ({ signal }) => leadService.history(id, signal),
  });
  const actions = useLeadActions({ meta: meta.data, onDeleted: () => navigate("/leads", { replace: true }) });

  if (lead.isPending) return <LoadingState label="Loading lead…" />;
  if (lead.isError) {
    return (
      <PageContainer>
        <PageHeader title="Lead" back={{ to: "/leads", label: "Leads" }} />
        <ErrorState error={lead.error} onRetry={() => lead.refetch()} />
      </PageContainer>
    );
  }

  const l = lead.data;
  const canEdit = canWrite("LEADS");
  const canApprove = canPerform("LEADS", "APPROVE");
  const canDelete = canPerform("LEADS", "DELETE");

  return (
    <PageContainer>
      <PageHeader
        title={l.clientName}
        back={{ to: "/leads", label: "Leads" }}
        badge={
          <>
            <StatusBadge domain="lead" status={l.stage} />
            {l.conflict ? <DuplicateBadge /> : null}
          </>
        }
        description={[l.projectTypeLabel, l.units ? `${l.units} units` : null, l.city].filter(Boolean).join(" · ")}
        actions={
          canEdit || canApprove || canDelete ? (
            <PageActions>
              {/* P6-13: Won and Lost leads are reopened before they can be acknowledged. */}
              {canApprove && !l.acknowledgedAt && !isClosed(l.stage) ? (
                <Button onClick={() => actions.requestAcknowledge(l)}>
                  <BadgeCheck aria-hidden="true" /> Acknowledge
                </Button>
              ) : null}
              {canDelete ? (
                <Button variant="destructive-outline" onClick={() => actions.requestDelete(l)}>
                  <Trash2 aria-hidden="true" /> Delete
                </Button>
              ) : null}
              {canEdit ? (
                <Button asChild variant="primary">
                  <Link to={`/leads/${l.id}/edit`}>
                    <Pencil aria-hidden="true" /> Edit
                  </Link>
                </Button>
              ) : null}
            </PageActions>
          ) : undefined
        }
      />

      <LeadAlerts lead={l} />

      <div className="grid gap-4 lg:grid-cols-2">
        <ContentCard title="Registration">
          <DescriptionList>
            <DetailRow label="Referring partner">
              {canRead("PARTNERS") ? (
                <Link to={`/partners/${l.partner.id}`} className="text-accent-ink hover:underline">
                  {l.partner.name}
                </Link>
              ) : (
                l.partner.name
              )}
            </DetailRow>
            <DetailRow label="Stage">
              <div className="max-w-56">
                <StageSelect
                  stage={l.stage}
                  stages={meta.data?.stages ?? [{ code: l.stage, label: l.stageLabel }]}
                  label="Stage"
                  canEdit={canEdit}
                  canApprove={canApprove}
                  busy={actions.stagePendingId === l.id}
                  onChange={(stage) => actions.requestStage(l, stage)}
                />
              </div>
            </DetailRow>
            <DetailRow label="Acknowledged">
              {l.acknowledgedAt
                ? `${formatDateTime(l.acknowledgedAt)}${l.acknowledgedByName ? ` · ${l.acknowledgedByName}` : ""}`
                : isClosed(l.stage)
                  ? "Not yet. Reopen the lead to acknowledge it."
                  : "Not yet"}
            </DetailRow>
            <DetailRow label="Locked fee rate">{l.lockedFeePercent === null ? NOT_AVAILABLE : formatPercent(l.lockedFeePercent)}</DetailRow>
            <DetailRow label="Protection">{protectionText(l)}</DetailRow>
          </DescriptionList>
        </ContentCard>

        <ContentCard title="SLA" description={`Acknowledge within ${l.sla.ackTargetHours} h; first estimate within ${l.sla.estimateTargetDays} days of acknowledgement.`}>
          <div className="mb-3">
            <SlaPills sla={l.sla} />
          </div>
          <DescriptionList>
            <DetailRow label={l.acknowledgedAt ? "Time to acknowledge" : "Waiting for acknowledgement"}>
              {l.sla.ackHours === null ? NOT_AVAILABLE : `${l.sla.ackHours} h`}
            </DetailRow>
            <DetailRow label={l.sla.hasEstimate ? "Days to first estimate" : "Days since acknowledgement"}>
              {l.sla.estimateDays === null ? NOT_AVAILABLE : String(l.sla.estimateDays)}
            </DetailRow>
          </DescriptionList>
        </ContentCard>

        <ContentCard title="Client & project">
          <DescriptionList>
            <DetailRow label="Phone">
              {l.clientPhone ? (
                <a href={`tel:${l.clientPhone}`} className="text-accent-ink hover:underline">
                  {l.clientPhone}
                </a>
              ) : (
                NOT_AVAILABLE
              )}
            </DetailRow>
            <DetailRow label="City">{l.city ?? NOT_AVAILABLE}</DetailRow>
            <DetailRow label="Project type">{l.projectTypeLabel}</DetailRow>
            <DetailRow label="Units">{l.units ?? NOT_AVAILABLE}</DetailRow>
            <DetailRow label="Budget">{l.budget === null ? NOT_AVAILABLE : formatCurrency(l.budget)}</DetailRow>
            <DetailRow label="Expected close">{formatDate(l.expectedClose)}</DetailRow>
            <DetailRow label="Presales owner">{l.presalesOwner ?? NOT_AVAILABLE}</DetailRow>
            <DetailRow label="Source">{l.source ?? NOT_AVAILABLE}</DetailRow>
          </DescriptionList>
        </ContentCard>

        <ContentCard title="History">
          {history.isPending ? (
            <LoadingState label="Loading history…" />
          ) : history.isError ? (
            <ErrorState error={history.error} onRetry={() => history.refetch()} />
          ) : (
            <Timeline
              entries={[...history.data].reverse().map((h) => ({
                id: h.id,
                tone: h.toStage === "WON" ? "success" : h.toStage === "LOST" ? "danger" : HISTORY_TONE[h.event],
                title: historyTitle(h),
                time: `${formatDateTime(h.occurredAt)}${h.actorName ? ` · ${h.actorName}` : ""}`,
                description: h.reason ?? undefined,
              }))}
            />
          )}
        </ContentCard>
      </div>

      {l.notes ? (
        <ContentCard title="Notes">
          <p className="whitespace-pre-wrap text-sm">{l.notes}</p>
        </ContentCard>
      ) : null}

      <p className="text-xs text-muted-foreground">
        Created {formatDateTime(l.createdAt)} · updated {formatDateTime(l.updatedAt)}
      </p>

      {actions.dialogs}
    </PageContainer>
  );
}

function historyTitle(h: LeadHistoryEntry) {
  switch (h.event) {
    case "CREATED":
      return <>Created as {h.toStageLabel}</>;
    case "ACKNOWLEDGED":
      return h.fromStage !== h.toStage ? <>Acknowledged: {h.fromStageLabel} → {h.toStageLabel}</> : <>Acknowledged</>;
    default:
      return (
        <>
          {h.event === "AUTO_STAGE" ? "Stage changed automatically" : h.fromStage && isClosed(h.fromStage) ? "Reopened" : "Stage changed"}:{" "}
          {h.fromStageLabel} → {h.toStageLabel}
        </>
      );
  }
}

/** BR-L6 and the source's lead alerts (§4.2, open leads only). */
function LeadAlerts({ lead: l }: { lead: Lead }) {
  const alerts = [];
  if (l.conflictPartnerName) {
    alerts.push(
      <Alert key="conflict" tone="warning" title="Possible duplicate">
        The same phone number is protected for {l.conflictPartnerName}. Check with both partners before working this lead.
      </Alert>,
    );
  }
  if (!isClosed(l.stage)) {
    if (!l.acknowledgedAt && l.sla.ackBreach) {
      alerts.push(
        <Alert key="ack" tone="danger" title="Not acknowledged in time">
          Waiting {l.sla.ackHours} h; the target is {l.sla.ackTargetHours} h.
        </Alert>,
      );
    }
    if (l.acknowledgedAt && !l.sla.hasEstimate && l.sla.estimateBreach) {
      alerts.push(
        <Alert key="estimate" tone="danger" title="No estimate yet">
          {l.sla.estimateDays} days since acknowledgement (target {l.sla.estimateTargetDays}).
        </Alert>,
      );
    }
    if (l.sla.protectionDaysLeft !== null && l.sla.protectionDaysLeft <= 15) {
      alerts.push(
        <Alert key="protection" tone="warning" title="Protection ending">
          Protection ends in {l.sla.protectionDaysLeft} day{l.sla.protectionDaysLeft === 1 ? "" : "s"} ({formatDate(l.protectionUntil)}).
        </Alert>,
      );
    }
  }
  return alerts.length > 0 ? <div className="space-y-3">{alerts}</div> : null;
}
