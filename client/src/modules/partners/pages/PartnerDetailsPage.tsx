import { useState } from "react";
import { Link, useParams } from "react-router";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BadgeIndianRupee, ChevronDown, Handshake, Lock, Pencil, Plus, TrendingUp, Users, Wallet } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";
import { ErrorState, LoadingState } from "@/components/feedback/States";
import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";
import { ContentCard, PageActions, PageContainer, PageHeader } from "@/components/layout/Page";
import { DescriptionList, DetailRow, Timeline } from "@/components/data/DataDisplay";
import { StatusBadge } from "@/components/data/StatusBadge";
import { MetricCard } from "@/components/dashboard/Dashboard";
import { Alert } from "@/components/ui/Alert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuTrigger } from "@/components/ui/DropdownMenu";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { formatCurrency, formatDate, formatDateTime, formatPercent, NOT_AVAILABLE } from "@/utils/format";
import { partnerService } from "../services/partnerService";
import { usePartner, usePartnerMeta } from "../hooks/usePartners";
import { TierReviewDialog } from "../components/TierReviewDialog";
import { leadService } from "@/modules/leads/services/leadService";
import { useLeadMeta } from "@/modules/leads/hooks/useLeads";
import { useLeadActions } from "@/modules/leads/components/LeadDialogs";
import { LeadTable } from "@/modules/leads/components/LeadTable";
import type { Partner, PartnerPerformance, PartnerStatus, TierReviewHistoryEntry } from "../types/partner.types";

const HISTORY_TONE: Record<TierReviewHistoryEntry["action"], "success" | "danger" | "warning" | "info"> = {
  UPGRADE: "success",
  AUTO_UPGRADE: "success",
  DOWNGRADE: "danger",
  GRACE: "warning",
  RECOVERED: "info",
};

export function PartnerDetailsPage() {
  const { id = "" } = useParams();
  const { canRead, canWrite, canPerform } = useAuth();
  const queryClient = useQueryClient();
  const partner = usePartner(id);
  const meta = usePartnerMeta();
  const performance = useQuery({
    queryKey: queryKeys.partners.performance(id),
    queryFn: ({ signal }) => partnerService.performance(id, signal),
  });
  const history = useQuery({
    queryKey: queryKeys.partners.tierHistory(id),
    queryFn: ({ signal }) => partnerService.tierHistory(id, signal),
  });
  const [statusTarget, setStatusTarget] = useState<PartnerStatus | null>(null);
  const [reviewing, setReviewing] = useState(false);

  const statusMutation = useMutation({
    mutationFn: ({ p, status }: { p: Partner; status: PartnerStatus }) => partnerService.setStatus(p.id, status, p.version),
    onSuccess: (updated) => {
      toast.success(`Status changed to ${updated.statusLabel}.`);
      setStatusTarget(null);
      void queryClient.invalidateQueries({ queryKey: queryKeys.partners.all });
    },
    onError: (error) => {
      toast.error(errorMessage(error));
      setStatusTarget(null);
    },
  });

  if (partner.isPending) return <LoadingState label="Loading partner…" />;
  if (partner.isError) {
    return (
      <PageContainer>
        <PageHeader title="Partner" back={{ to: "/partners", label: "Partners" }} />
        <ErrorState error={partner.error} onRetry={() => partner.refetch()} />
      </PageContainer>
    );
  }

  const p = partner.data;
  const canEdit = canWrite("PARTNERS");
  const canApprove = canPerform("PARTNERS", "APPROVE");
  const statusLabel = (code: PartnerStatus) => meta.data?.statuses.find((s) => s.code === code)?.label ?? code;
  const perf = performance.data;

  return (
    <PageContainer>
      <PageHeader
        title={p.name}
        back={{ to: "/partners", label: "Partners" }}
        badge={
          <>
            <StatusBadge domain="partner" status={p.status} />
            <Badge tone="accent">{p.tier.name}</Badge>
            {p.strategicProgramme ? <Badge tone="outline">Programme</Badge> : null}
          </>
        }
        description={[p.categoryLabel, p.city].filter(Boolean).join(" · ")}
        actions={
          canEdit || canApprove ? (
            <PageActions>
              {canApprove ? (
                <Button onClick={() => setReviewing(true)}>
                  <TrendingUp aria-hidden="true" /> Tier review
                </Button>
              ) : null}
              {canEdit ? (
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button>
                      Change status <ChevronDown aria-hidden="true" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuLabel>Set status</DropdownMenuLabel>
                    {(meta.data?.statuses ?? [])
                      .filter((s) => s.code !== p.status)
                      .map((s) => (
                        <DropdownMenuItem key={s.code} onSelect={() => setStatusTarget(s.code)}>
                          {s.label}
                        </DropdownMenuItem>
                      ))}
                  </DropdownMenuContent>
                </DropdownMenu>
              ) : null}
              {canEdit ? (
                <Button asChild variant="primary">
                  <Link to={`/partners/${p.id}/edit`}>
                    <Pencil aria-hidden="true" /> Edit
                  </Link>
                </Button>
              ) : null}
            </PageActions>
          ) : undefined
        }
      />

      <PartnerAlerts partner={p} performance={perf} />

      <section aria-label="Performance" className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          label="Tier"
          icon={Handshake}
          loading={performance.isPending}
          unavailable={performance.isError ? "Couldn't load" : undefined}
          value={<span className="font-sans">{p.tier.name}</span>}
          hint={
            perf ? (
              <>
                {formatPercent(perf.tier.commissionPercent)} fee
                {perf.calculatedTier.level !== perf.tier.level ? <> · revenue qualifies for {perf.calculatedTier.name}</> : null}
              </>
            ) : undefined
          }
        />
        <MetricCard
          label="Revenue · 12 months"
          icon={BadgeIndianRupee}
          loading={performance.isPending}
          unavailable={performance.isError ? "Couldn't load" : undefined}
          value={perf ? formatCurrency(perf.revenue12m) : undefined}
          hint={perf ? <>Paid since {formatDate(perf.revenueSince)}</> : undefined}
        />
        <MetricCard
          label="Referral fees"
          icon={Wallet}
          loading={performance.isPending}
          unavailable={performance.isError ? "Couldn't load" : undefined}
          value={perf ? formatCurrency(perf.feesUnpaid) : undefined}
          hint={perf ? <>unpaid · {formatCurrency(perf.feesPaid)} paid</> : undefined}
        />
        <MetricCard
          label="Leads"
          icon={Users}
          loading={performance.isPending}
          unavailable={performance.isError ? "Couldn't load" : undefined}
          value={perf ? String(perf.leadCount) : undefined}
        />
      </section>

      {perf ? <TierProgress performance={perf} /> : null}

      <div className="grid gap-4 lg:grid-cols-2">
        <ContentCard title="Profile">
          <DescriptionList>
            <DetailRow label="Category">{p.categoryLabel}</DetailRow>
            <DetailRow label="Status">{p.statusLabel}</DetailRow>
            <DetailRow label="Contact person">{p.contactName ?? NOT_AVAILABLE}</DetailRow>
            <DetailRow label="Phone">{p.phone ? <a href={`tel:${p.phone}`} className="text-accent-ink hover:underline">{p.phone}</a> : NOT_AVAILABLE}</DetailRow>
            <DetailRow label="Email">{p.email ? <a href={`mailto:${p.email}`} className="text-accent-ink hover:underline">{p.email}</a> : NOT_AVAILABLE}</DetailRow>
            <DetailRow label="City">{p.city ?? NOT_AVAILABLE}</DetailRow>
          </DescriptionList>
        </ContentCard>

        <ContentCard title="Agreement & tier">
          <DescriptionList>
            <DetailRow label="Agreement">
              <StatusBadge domain="agreement" status={p.agreementStatus} />
            </DetailRow>
            <DetailRow label="Version">{p.agreementVersion ?? NOT_AVAILABLE}</DetailRow>
            <DetailRow label="Signed / sent on">{formatDate(p.agreementDate)}</DetailRow>
            <DetailRow label="Tier">
              {p.tier.level} · {p.tier.name} ({formatPercent(p.tier.commissionPercent)})
            </DetailRow>
            <DetailRow label="Programme">{p.strategicProgramme ? "Signed builder / developer / contractor programme" : "No"}</DetailRow>
            <DetailRow label="Last tier review">{formatDate(p.lastTierReviewOn)}</DetailRow>
          </DescriptionList>
        </ContentCard>

        <ContentCard
          title="Compliance & bank"
          description={
            p.sensitiveVisible ? undefined : (
              <span className="inline-flex items-center gap-1">
                <Lock className="size-3.5" aria-hidden="true" /> PAN, GSTIN and bank account are masked for your account.
              </span>
            )
          }
        >
          <DescriptionList>
            <DetailRow label="PAN" mono>{p.pan ?? NOT_AVAILABLE}</DetailRow>
            <DetailRow label="GSTIN" mono>{p.gstin ?? NOT_AVAILABLE}</DetailRow>
            <DetailRow label="Licence / registration no.">{p.licenceNo ?? NOT_AVAILABLE}</DetailRow>
            <DetailRow label="Bank">{p.bankName ?? NOT_AVAILABLE}</DetailRow>
            <DetailRow label="Bank account" mono>{p.bankAccount ?? NOT_AVAILABLE}</DetailRow>
            <DetailRow label="IFSC" mono>{p.ifsc ?? NOT_AVAILABLE}</DetailRow>
          </DescriptionList>
        </ContentCard>

        <ContentCard title="Tier history">
          {history.isPending ? (
            <LoadingState label="Loading tier history…" />
          ) : history.isError ? (
            <ErrorState error={history.error} onRetry={() => history.refetch()} />
          ) : (
            <Timeline
              empty="No tier reviews yet."
              entries={history.data.map((h) => ({
                id: h.id,
                tone: HISTORY_TONE[h.action],
                title: (
                  <>
                    {h.actionLabel}: tier {h.fromLevel} → {h.toLevel}
                  </>
                ),
                time: `${formatDateTime(h.reviewedAt)}${h.actorName ? ` · ${h.actorName}` : ""}`,
                description: h.programme ? "Programme partner" : `${formatCurrency(h.revenue12m)} revenue in the prior 12 months`,
              }))}
            />
          )}
        </ContentCard>
      </div>

      {p.notes ? (
        <ContentCard title="Notes">
          <p className="whitespace-pre-wrap text-sm">{p.notes}</p>
        </ContentCard>
      ) : null}

      {canRead("LEADS") ? <PartnerLeads partnerId={p.id} partnerName={p.name} /> : null}

      <ContentCard title="Other linked records" description="Estimates, invoices, payments and payouts for this partner.">
        <p className="text-sm text-muted-foreground">
          These lists appear here as their modules are delivered: estimates in Phase 8, invoices and payments in Phases 9–10, and referral fee
          payouts in Phase 11.
        </p>
      </ContentCard>

      <p className="text-xs text-muted-foreground">
        Created {formatDateTime(p.createdAt)} · updated {formatDateTime(p.updatedAt)}
      </p>

      <ConfirmDialog
        open={!!statusTarget}
        title={statusTarget === "INACTIVE" ? "Deactivate partner?" : `Change status to ${statusTarget ? statusLabel(statusTarget) : ""}?`}
        confirmLabel={statusTarget === "INACTIVE" ? "Deactivate" : "Change status"}
        danger={statusTarget === "INACTIVE"}
        busy={statusMutation.isPending}
        onCancel={() => setStatusTarget(null)}
        onConfirm={() => statusTarget && statusMutation.mutate({ p, status: statusTarget })}
      >
        {statusTarget === "INACTIVE" ? (
          <p>
            <strong>{p.name}</strong> will be left out of tier reviews. Their records are kept; partners are never deleted.
          </p>
        ) : (
          <p>
            <strong>{p.name}</strong> moves from {p.statusLabel} to {statusTarget ? statusLabel(statusTarget) : ""}.
          </p>
        )}
      </ConfirmDialog>

      {canApprove ? <TierReviewDialog open={reviewing} onOpenChange={setReviewing} partnerIds={[p.id]} /> : null}
    </PageContainer>
  );
}

/** The partner's leads, newest first, with the same inline actions as the Leads page. */
function PartnerLeads({ partnerId, partnerName }: { partnerId: string; partnerName: string }) {
  const { canWrite } = useAuth();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const meta = useLeadMeta();
  const actions = useLeadActions({ meta: meta.data });
  const filters = { partnerId, page, size };
  const leads = useQuery({
    queryKey: queryKeys.leads.list(filters),
    queryFn: ({ signal }) => leadService.list(filters, signal),
    placeholderData: keepPreviousData,
  });
  return (
    <section aria-labelledby="partner-leads-heading" className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="partner-leads-heading" className="text-base font-semibold">
          Leads
        </h2>
        {canWrite("LEADS") ? (
          <Button asChild size="sm" variant="primary">
            <Link to={`/leads/new?partnerId=${partnerId}`}>
              <Plus aria-hidden="true" /> New lead
            </Link>
          </Button>
        ) : null}
      </div>
      <LeadTable
        caption={`Leads from ${partnerName}`}
        rows={leads.data?.content}
        meta={meta.data}
        actions={actions}
        showPartner={false}
        loading={leads.isPending}
        fetching={leads.isFetching && !leads.isPending}
        error={leads.error}
        onRetry={() => leads.refetch()}
        page={leads.data?.meta}
        onPageChange={setPage}
        onPageSizeChange={(next) => {
          setSize(next);
          setPage(0);
        }}
        empty={<p className="py-6 text-center text-sm text-muted-foreground">No leads from this partner yet.</p>}
      />
      {actions.dialogs}
    </section>
  );
}

/** BR-P6, BR-P7, BR-P8 and the downgrade grace period. */
function PartnerAlerts({ partner: p, performance }: { partner: Partner; performance?: PartnerPerformance }) {
  const missing = [!p.panOnFile && "PAN", !p.bankAccountOnFile && "bank account"].filter(Boolean) as string[];
  const alerts = [];
  if (p.status === "ACTIVE" && p.agreementStatus !== "SIGNED") {
    alerts.push(
      <Alert key="agreement" tone="warning" title="Agreement not signed">
        This partner is active but their agreement is {p.agreementStatusLabel.toLowerCase()}.
      </Alert>,
    );
  }
  if (missing.length > 0) {
    alerts.push(
      <Alert key="payout" tone={performance && Number(performance.feesUnpaid) > 0 ? "danger" : "info"} title="Not ready for payouts">
        Add the {missing.join(" and ")} before any referral fee can be paid.
      </Alert>,
    );
  }
  if (p.category === "ARCHITECT") {
    alerts.push(
      <Alert key="architect" tone="warning" title="Architect">
        Take legal advice before paying a cash referral fee (CoA conduct rules).
      </Alert>,
    );
  }
  if (p.downgradeGrace) {
    alerts.push(
      <Alert key="grace" tone="warning" title="Downgrade grace period">
        Revenue is below the {p.tier.name} threshold. If it is still below at the next tier review, the partner drops one tier.
      </Alert>,
    );
  }
  return alerts.length > 0 ? <div className="space-y-3">{alerts}</div> : null;
}

/** BR-P13: progress to the next tier above the assigned one. */
function TierProgress({ performance: perf }: { performance: PartnerPerformance }) {
  if (!perf.nextTier || perf.progressPercent === null) {
    return (
      <ContentCard>
        <p className="text-sm text-muted-foreground">{perf.tier.name} is the highest tier.</p>
      </ContentCard>
    );
  }
  return (
    <ContentCard>
      <div className="flex flex-wrap items-baseline justify-between gap-2 text-sm">
        <span id="tier-progress-label" className="font-medium">
          Progress to {perf.nextTier.name}
        </span>
        <span className="text-muted-foreground">
          {formatCurrency(perf.remainingToNextTier)} to go · threshold {formatCurrency(perf.nextTierMinimum, { whole: true })}
        </span>
      </div>
      <div
        role="progressbar"
        aria-labelledby="tier-progress-label"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={perf.progressPercent}
        aria-valuetext={`${perf.progressPercent}%`}
        className="mt-2 h-2.5 overflow-hidden rounded-full bg-secondary"
      >
        <div className="h-full rounded-full bg-primary transition-[width]" style={{ width: `${perf.progressPercent}%` }} />
      </div>
      <div className="mt-1 text-right text-xs text-muted-foreground">{perf.progressPercent}%</div>
    </ContentCard>
  );
}
