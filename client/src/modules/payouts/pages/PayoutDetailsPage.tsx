import { useState, type ReactNode } from "react";
import { Link, useParams } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/hooks/useAuth";
import { ErrorState, LoadingState } from "@/components/feedback/States";
import { ContentCard, PageActions, PageContainer, PageHeader } from "@/components/layout/Page";
import { DescriptionList, DetailRow, Timeline, type TimelineEntry } from "@/components/data/DataDisplay";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { queryKeys } from "@/lib/queryKeys";
import { formatCurrency, formatDate, formatDateTime, formatPercent, NOT_AVAILABLE } from "@/utils/format";
import { payoutService } from "../services/payoutService";
import { usePayout } from "../hooks/usePayouts";
import { linkClass, PayoutLinesTable, PayoutStatusBadge, SignedAmount, TdsBreakdown } from "../components/PayoutParts";
import { PayoutActionDialog, type PayoutAction } from "../components/PayoutActionDialogs";
import type { Payout, PayoutCalculation, PayoutHistoryEntry } from "../types/payout.types";

const HISTORY: Record<string, { title: string; tone: TimelineEntry["tone"] }> = {
  PAYOUT_CREATED: { title: "Draft created", tone: "info" },
  PAYOUT_RECALCULATED: { title: "Recalculated", tone: "info" },
  PAYOUT_TDS_ADJUSTED: { title: "TDS changed by hand", tone: "warning" },
  PAYOUT_SUBMITTED: { title: "Submitted for approval", tone: "info" },
  PAYOUT_APPROVED: { title: "Approved", tone: "success" },
  PAYOUT_REJECTED: { title: "Rejected", tone: "danger" },
  PAYOUT_CANCELLED: { title: "Cancelled", tone: "danger" },
  PAYOUT_PROCESSING: { title: "Transfer started", tone: "info" },
  PAYOUT_PAID: { title: "Paid", tone: "success" },
  PAYOUT_FAILED: { title: "Transfer failed", tone: "danger" },
};
const FIELD_LABELS: Record<string, string> = {
  payoutNumber: "Number", partner: "Partner", status: "Status", asOfDate: "Receipts up to", receipts: "Receipt lines", adjustments: "Adjustment lines", tdsSection: "TDS section",
  receiptsAdded: "Receipts added", adjustmentsAdded: "Adjustments added", linesReleased: "Lines released", commissionTotal: "Commission",
  computedTds: "TDS calculated", tdsAmount: "TDS", tdsStatus: "TDS status", netAmount: "Net", note: "Note", reason: "Reason", paidDate: "Paid on",
  paymentReference: "Bank reference", bankAccountLast4: "Account ending", reversedSources: "Reversed receipts", reversalAdjustments: "Deductions carried forward",
};
const MONEY_FIELDS = new Set(["commissionTotal", "computedTds", "tdsAmount", "netAmount"]);
const CALC_EVENTS: Record<PayoutCalculation["event"], string> = {
  GENERATED: "Generated",
  RECALCULATED: "Recalculated",
  ADJUSTED: "Adjusted by hand",
  APPROVED: "Checked at approval",
  LEGACY: "Carried over",
};

/** One payout: what it pays and why, its TDS, who did what, and the transitions the caller may make. */
export function PayoutDetailsPage() {
  const { id = "" } = useParams();
  const { canRead } = useAuth();
  const payout = usePayout(id);
  const history = useQuery({ queryKey: queryKeys.payouts.history(id), queryFn: ({ signal }) => payoutService.history(id, signal) });
  const [action, setAction] = useState<PayoutAction | null>(null);

  if (payout.isPending) return <LoadingState label="Loading payout…" />;
  if (payout.isError) {
    return (
      <PageContainer>
        <PageHeader title="Payout" back={{ to: "/payouts", label: "Payouts" }} />
        <ErrorState error={payout.error} onRetry={() => payout.refetch()} />
      </PageContainer>
    );
  }

  const p = payout.data;
  const w = p.workflow;
  const canOpenPayments = canRead("PAYMENTS");
  const canOpenInvoices = canRead("INVOICES");

  return (
    <PageContainer>
      <PageHeader
        title={p.payoutNumber ?? "Payout"}
        back={{ to: "/payouts", label: "Payouts" }}
        badge={<PayoutStatusBadge status={p.status} />}
        description={`${p.partner.name} · receipts up to ${formatDate(p.asOfDate)} · ${p.fyLabel}`}
        actions={<Actions payout={p} onAction={setAction} />}
      />

      {p.legacy ? (
        <Alert tone="info" title="Paid before Phase 11">
          Carried over from the earlier per-receipt accruals. It is kept for the record and the financial-year TDS position.
        </Alert>
      ) : null}
      {p.blockers.map((b) => (
        <Alert key={b} tone="warning">
          {b}
        </Alert>
      ))}
      {w.statusReason && (p.status === "REJECTED" || p.status === "CANCELLED" || p.status === "FAILED") ? (
        <Alert tone="danger" title={p.statusLabel}>
          {w.closedByName ? `${w.closedByName}: ` : ""}“{w.statusReason}”
          {w.closedAt ? ` (${formatDateTime(w.closedAt)})` : ""}. Its receipts and adjustments are available for the next payout.
        </Alert>
      ) : null}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Figure label="Receipts" value={formatCurrency(p.receiptsTotal)} />
        <Figure label="Adjustments" value={<SignedAmount amount={p.adjustmentsTotal} />} />
        <Figure label="TDS" value={p.tds.tdsAmount !== null ? formatCurrency(p.tds.tdsAmount) : "Not calculated"} hint={p.tds.message && !p.blockers.includes(p.tds.message) ? p.tds.message : undefined} />
        <Figure label="Net payable" value={p.netAmount !== null ? formatCurrency(p.netAmount) : NOT_AVAILABLE} emphasis />
      </div>

      <ContentCard
        title="Commission"
        description="Each receipt's amount excl. GST at the commission rate locked on its invoice. Negative lines are deductions carried from earlier payouts."
      >
        <PayoutLinesTable lines={p.lines} caption="Lines in this payout" canOpenPayments={canOpenPayments} canOpenInvoices={canOpenInvoices} />
        <div className="mt-3 flex justify-end gap-2 border-t border-border pt-3 text-sm">
          <span className="text-muted-foreground">Commission</span>
          <SignedAmount amount={p.commissionTotal} className="font-semibold" />
        </div>
      </ContentCard>

      {p.releasedLines.length > 0 ? (
        <ContentCard title="Released lines" description="Taken out of this payout (reversed receipts, or released when it closed). They stay on record.">
          <PayoutLinesTable lines={p.releasedLines} caption="Released lines" canOpenPayments={canOpenPayments} canOpenInvoices={canOpenInvoices} />
        </ContentCard>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-3">
        <ContentCard title="TDS" description={`Financial year ${p.fyLabel}`} className="lg:col-span-2">
          {p.tds.message ? (
            <Alert tone="warning" className="mb-4">
              {p.tds.message}
            </Alert>
          ) : null}
          <TdsBreakdown tds={p.tds} commission={p.commissionTotal} fyLabel={p.fyLabel} />
        </ContentCard>
        <ContentCard title="Partner">
          <DescriptionList>
            <DetailRow label="Partner">
              {canRead("PARTNERS") ? (
                <Link to={`/partners/${p.partner.id}`} className={linkClass}>
                  {p.partner.name}
                </Link>
              ) : (
                p.partner.name
              )}
            </DetailRow>
            <DetailRow label="PAN" mono>
              {p.partner.pan ?? (p.partner.panOnFile ? "On file" : "Not on file")}
              <span className="ml-1 font-sans text-xs text-muted-foreground">{p.partner.panVerified ? "verified" : "not verified"}</span>
            </DetailRow>
            <DetailRow label="Bank" mono>
              {p.partner.bankOnFile ? `${p.partner.bankName ?? ""} ${p.partner.bankAccount ?? ""}`.trim() || "On file" : "Not on file"}
            </DetailRow>
            {p.partner.ifsc ? (
              <DetailRow label="IFSC" mono>
                {p.partner.ifsc}
              </DetailRow>
            ) : null}
          </DescriptionList>
          {!p.partner.sensitiveVisible ? <p className="mt-3 text-xs text-muted-foreground">PAN and bank details are masked for your role.</p> : null}
        </ContentCard>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <ContentCard title="Workflow">
          <DescriptionList>
            <DetailRow label="Created">{who(w.createdAt, w.createdByName)}</DetailRow>
            <DetailRow label="Submitted">{who(w.submittedAt, w.submittedByName)}</DetailRow>
            <DetailRow label="Approved">
              {who(w.approvedAt, w.approvedByName)}
              {w.approvalNote ? <div className="text-xs text-muted-foreground">“{w.approvalNote}”</div> : null}
            </DetailRow>
            <DetailRow label="Transfer started">
              {who(w.processingAt, w.processingByName)}
              {w.bankAccountLast4 ? <div className="text-xs text-muted-foreground">To the account ending {w.bankAccountLast4}</div> : null}
            </DetailRow>
            <DetailRow label="Paid">
              {w.paidDate ? `${formatDate(w.paidDate)}${w.paidByName ? ` · recorded by ${w.paidByName}` : ""}` : NOT_AVAILABLE}
              {w.paymentReference ? <div className="font-mono text-xs text-muted-foreground">Ref. {w.paymentReference}</div> : null}
            </DetailRow>
            {p.notes ? (
              <DetailRow label="Notes">
                <span className="whitespace-pre-wrap">{p.notes}</span>
              </DetailRow>
            ) : null}
          </DescriptionList>
        </ContentCard>
        <ContentCard title="Calculation history" description="Every TDS calculation is kept, including manual changes and the check at approval.">
          {p.calculations.length === 0 ? (
            <p className="text-sm text-muted-foreground">No calculations.</p>
          ) : (
            <ol className="space-y-3 text-sm">
              {p.calculations.map((c) => (
                <li key={c.id} className="border-b border-border/60 pb-2 last:border-0">
                  <div className="flex flex-wrap justify-between gap-2">
                    <span className="font-medium">{CALC_EVENTS[c.event] ?? c.event}</span>
                    <span className="text-xs text-muted-foreground">{who(c.createdAt, c.createdByName)}</span>
                  </div>
                  <div className="text-xs text-muted-foreground">
                    Commission {formatCurrency(c.commissionTotal)} · {c.sectionCode ?? "no section"} {c.ratePercent !== null ? `@ ${formatPercent(c.ratePercent)}` : ""} · base{" "}
                    {c.tdsBase !== null ? formatCurrency(c.tdsBase) : NOT_AVAILABLE} · TDS {c.tdsAmount !== null ? formatCurrency(c.tdsAmount) : "not calculated"}
                    {c.adjustmentReason ? ` · “${c.adjustmentReason}”` : ""}
                  </div>
                </li>
              ))}
            </ol>
          )}
        </ContentCard>
      </div>

      <ContentCard title="History" description="Bank details are never recorded; at most the last four digits of the account.">
        {history.isPending ? (
          <LoadingState label="Loading history…" />
        ) : history.isError ? (
          <ErrorState error={history.error} onRetry={() => history.refetch()} />
        ) : (
          <Timeline entries={history.data.map(historyEntry)} />
        )}
      </ContentCard>

      {action ? <PayoutActionDialog payout={p} action={action} onClose={() => setAction(null)} /> : null}
    </PageContainer>
  );
}

function Actions({ payout, onAction }: { payout: Payout; onAction: (action: PayoutAction) => void }) {
  const a = payout.actions;
  const buttons: { key: PayoutAction; label: string; show: boolean; variant?: "primary" | "destructive-outline" }[] = [
    { key: "recalculate", label: "Recalculate", show: a.recalculate },
    { key: "adjustTds", label: "Change TDS", show: a.adjustTds },
    { key: "submit", label: "Submit for approval", show: a.submit, variant: "primary" },
    { key: "reject", label: "Reject", show: a.reject, variant: "destructive-outline" },
    { key: "approve", label: "Approve", show: a.approve, variant: "primary" },
    { key: "fail", label: "Transfer failed", show: a.fail, variant: "destructive-outline" },
    { key: "processing", label: "Start transfer", show: a.startProcessing, variant: "primary" },
    { key: "markPaid", label: "Mark as paid", show: a.markPaid, variant: "primary" },
    { key: "cancel", label: "Cancel payout", show: a.cancel, variant: "destructive-outline" },
  ];
  const shown = buttons.filter((b) => b.show);
  if (shown.length === 0) return null;
  return (
    <PageActions>
      {shown.map((b) => (
        <Button key={b.key} variant={b.variant} onClick={() => onAction(b.key)}>
          {b.label}
        </Button>
      ))}
    </PageActions>
  );
}

function Figure({ label, value, hint, emphasis }: { label: string; value: ReactNode; hint?: string; emphasis?: boolean }) {
  return (
    <div className="rounded-lg border border-border bg-surface p-4">
      <div className="text-xs font-medium text-muted-foreground">{label}</div>
      <div className={`mt-1 font-mono tabular ${emphasis ? "text-xl font-semibold" : "text-lg"}`}>{value}</div>
      {hint ? <div className="mt-1 text-xs text-warning-ink">{hint}</div> : null}
    </div>
  );
}

function who(at: string | null, name: string | null) {
  if (!at) return NOT_AVAILABLE;
  return `${formatDateTime(at)}${name ? ` · ${name}` : ""}`;
}

function historyEntry(h: PayoutHistoryEntry): TimelineEntry {
  const known = HISTORY[h.action];
  const changes = h.changes.filter((c) => c.to !== null && c.to !== undefined && c.to !== "");
  return {
    id: h.id,
    tone: known?.tone ?? "info",
    title: known?.title ?? h.action,
    time: `${formatDateTime(h.at)}${h.actorName ? ` · ${h.actorName}` : ""}`,
    description: changes.length > 0 ? changes.map((c) => `${FIELD_LABELS[c.field] ?? c.field}: ${show(c.to, c.field)}`).join("; ") : undefined,
  };
}

function show(value: unknown, field: string): string {
  if (value === null || value === undefined || value === "") return NOT_AVAILABLE;
  if (MONEY_FIELDS.has(field) && (typeof value === "number" || (typeof value === "string" && /^-?\d+(\.\d+)?$/.test(value)))) return formatCurrency(value as string);
  if (field === "paidDate" || field === "asOfDate") return formatDate(String(value));
  if (Array.isArray(value)) return value.join(", ");
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}
