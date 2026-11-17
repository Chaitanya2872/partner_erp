import { useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Archive, Check, Download, FileText, Pencil, Printer, Send, X } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { ErrorState, LoadingState } from "@/components/feedback/States";
import { ArchiveDialog } from "@/components/feedback/ArchiveDialog";
import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";
import { ContentCard, PageActions, PageContainer, PageHeader } from "@/components/layout/Page";
import { DescriptionList, DetailRow, Timeline, type TimelineEntry } from "@/components/data/DataDisplay";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/Tabs";
import { errorMessage } from "@/lib/errors";
import { ApiRequestError } from "@/services/api";
import { queryKeys } from "@/lib/queryKeys";
import { saveTextFile } from "@/utils/download";
import { formatCurrency, formatDate, formatDateTime, formatPercent, NOT_AVAILABLE } from "@/utils/format";
import { estimateService } from "../services/estimateService";
import { invoiceService } from "@/modules/invoices/services/invoiceService";
import { useEstimate } from "../hooks/useEstimates";
import { EstimateStatusBadge } from "../components/EstimateStatusBadge";
import { EstimateTotals } from "../components/EstimateTotals";
import type { Estimate, EstimateHistoryEntry } from "../types/estimate.types";

type StatusAction = "send" | "accept" | "reject";

const ACTIONS: Record<StatusAction, { label: string; done: string; confirm: string; body: string }> = {
  send: {
    label: "Mark sent",
    done: "marked sent",
    confirm: "Mark this estimate as sent?",
    body: "The lead moves to Estimate sent if it is at an earlier stage.",
  },
  accept: {
    label: "Mark accepted",
    done: "marked accepted",
    confirm: "Mark this estimate as accepted?",
    body: "The lead is marked Won. Accepting for a Lost lead reopens it, which needs Leads approve.",
  },
  reject: {
    label: "Mark rejected",
    done: "marked rejected",
    confirm: "Mark this estimate as rejected?",
    body: "The lead is marked Lost unless it is already Won.",
  },
};

const HISTORY_TITLES: Record<string, string> = {
  ESTIMATE_CREATED: "Created",
  ESTIMATE_UPDATED: "Edited",
  ESTIMATE_STATUS_CHANGED: "Status changed",
  ESTIMATE_DELETED: "Archived",
  ESTIMATE_RESTORED: "Restored from the archive",
};

const FIELD_LABELS: Record<string, string> = {
  status: "Status", estimateDate: "Date", validUntil: "Valid until", discountPercent: "Discount %", igst: "IGST", billTo: "Bill to",
  feePercent: "Fee %", defaultFeePercent: "Default fee %", feeOverrideReason: "Fee reason", notes: "Notes", lines: "Items", grandTotal: "Total", leadId: "Lead", partnerId: "Partner",
  estimateNumber: "Number", revisionReason: "Reason for revision",
};

export function EstimateDetailsPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { canRead, canWrite, canPerform } = useAuth();
  const estimate = useEstimate(id);
  const history = useQuery({
    queryKey: queryKeys.estimates.history(id),
    queryFn: ({ signal }) => estimateService.history(id, signal),
  });
  const [pending, setPending] = useState<StatusAction | "delete" | "convert" | null>(null);
  const [reasonError, setReasonError] = useState<string | null>(null);

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.estimates.all }),
      queryClient.invalidateQueries({ queryKey: queryKeys.leads.all }),
    ]);

  const transition = useMutation({
    mutationFn: ({ action, e }: { action: StatusAction; e: Estimate }) => estimateService[action](e.id, e.version),
    onSuccess: async (saved, { action }) => {
      toast.success(`${saved.estimateNumber} ${ACTIONS[action].done}.`);
      setPending(null);
      await refresh();
    },
    onError: (error) => {
      toast.error(errorMessage(error));
      setPending(null);
    },
  });

  const remove = useMutation({
    mutationFn: ({ e, why }: { e: Estimate; why: string }) => estimateService.remove(e.id, why),
    onSuccess: async (_, { e }) => {
      toast.success(`${e.estimateNumber} archived. You can restore it from the Archive.`);
      setPending(null);
      queryClient.removeQueries({ queryKey: queryKeys.estimates.detail(e.id) });
      await refresh();
      navigate("/estimates", { replace: true });
    },
    onError: (error) => {
      const field = error instanceof ApiRequestError ? error.fieldErrors.find((f) => f.field === "reason") : undefined;
      if (field) {
        setReasonError(field.message);
        return;
      }
      toast.error(errorMessage(error));
      setPending(null);
    },
  });

  // Phase 9: a sent or accepted estimate becomes a draft invoice; the estimate is then locked as Invoiced.
  const convert = useMutation({
    mutationFn: (e: Estimate) => invoiceService.convert(e.id),
    onSuccess: async (invoice, e) => {
      toast.success(`Draft invoice created from ${e.estimateNumber}.`);
      setPending(null);
      await Promise.all([refresh(), queryClient.invalidateQueries({ queryKey: queryKeys.invoices.all })]);
      navigate(`/invoices/${invoice.id}`);
    },
    onError: (error) => {
      toast.error(errorMessage(error));
      setPending(null);
      void refresh();
    },
  });

  if (estimate.isPending) return <LoadingState label="Loading estimate…" />;
  if (estimate.isError) {
    return (
      <PageContainer>
        <PageHeader title="Estimate" back={{ to: "/estimates", label: "Estimates" }} />
        <ErrorState error={estimate.error} onRetry={() => estimate.refetch()} />
      </PageContainer>
    );
  }

  const e = estimate.data;
  const canEdit = canWrite("ESTIMATES") && e.status !== "INVOICED";
  const canDelete = canPerform("ESTIMATES", "DELETE") && e.status !== "INVOICED";
  const canInternal = canRead("INTERNAL_B2C");
  const actions: StatusAction[] = !canWrite("ESTIMATES") ? [] : e.status === "DRAFT" ? ["send"] : e.status === "SENT" ? ["accept", "reject"] : [];
  // Invoiced estimates may be converted again once their invoice is cancelled; the server refuses while one is live.
  const canConvert = canWrite("INVOICES") && (e.status === "SENT" || e.status === "ACCEPTED" || e.status === "INVOICED");
  const busy = transition.isPending || remove.isPending || convert.isPending;

  return (
    <PageContainer>
      <PageHeader
        title={e.estimateNumber}
        back={{ to: "/estimates", label: "Estimates" }}
        badge={<EstimateStatusBadge status={e.status} expired={e.expired} />}
        description={`${e.lead.clientName} · ${e.partner.name} · ${formatCurrency(e.totals.grandTotal)}`}
        actions={
          canEdit || canDelete || canConvert || actions.length > 0 ? (
            <PageActions>
              {canDelete ? (
                <Button variant="destructive-outline" onClick={() => { setReasonError(null); setPending("delete"); }} disabled={busy}>
                  <Archive aria-hidden="true" /> Archive
                </Button>
              ) : null}
              {actions.map((a) => (
                <Button key={a} onClick={() => setPending(a)} disabled={busy}>
                  {a === "send" ? <Send aria-hidden="true" /> : a === "accept" ? <Check aria-hidden="true" /> : <X aria-hidden="true" />} {ACTIONS[a].label}
                </Button>
              ))}
              {canConvert ? (
                <Button onClick={() => setPending("convert")} disabled={busy}>
                  <FileText aria-hidden="true" /> Convert to invoice
                </Button>
              ) : null}
              {canEdit ? (
                <Button asChild variant="primary">
                  <Link to={`/estimates/${e.id}/edit`}>
                    <Pencil aria-hidden="true" /> Edit
                  </Link>
                </Button>
              ) : null}
            </PageActions>
          ) : undefined
        }
      />

      {e.expired ? (
        <Alert tone="warning" title="Validity has passed">
          This estimate was valid until {formatDate(e.validUntil)}. Edit it to extend the validity, or create a new one.
        </Alert>
      ) : null}
      {e.lead.deleted ? (
        <Alert tone="info" title="Lead archived">
          The lead this estimate was made for has been archived.
        </Alert>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-3">
        <ContentCard title="Estimate" className="lg:col-span-2">
          <DescriptionList columns={2}>
            <DetailRow label="Lead">
              {canRead("LEADS") && !e.lead.deleted ? (
                <Link to={`/leads/${e.lead.id}`} className="text-accent-ink hover:underline">
                  {e.lead.clientName} · {e.lead.leadNumber}
                </Link>
              ) : (
                `${e.lead.clientName} · ${e.lead.leadNumber}`
              )}
            </DetailRow>
            <DetailRow label="Partner">
              {canRead("PARTNERS") ? (
                <Link to={`/partners/${e.partner.id}`} className="text-accent-ink hover:underline">
                  {e.partner.name}
                </Link>
              ) : (
                e.partner.name
              )}
            </DetailRow>
            <DetailRow label="Date">{formatDate(e.estimateDate)}</DetailRow>
            <DetailRow label="Valid until">{formatDate(e.validUntil)}</DetailRow>
            <DetailRow label="Bill to">{e.billToLabel}</DetailRow>
            <DetailRow label="Tax">{e.igst ? "IGST" : "CGST + SGST"}</DetailRow>
            <DetailRow label="Fee rate">{formatPercent(e.feePercent)}</DetailRow>
            <DetailRow label="Sent on">{e.sentOn ? formatDate(e.sentOn) : NOT_AVAILABLE}</DetailRow>
          </DescriptionList>
        </ContentCard>
        <EstimateTotals totals={e.totals} discountPercent={e.discountPercent} igst={e.igst} />
      </div>

      {e.lines.some((l) => !l.productActive) ? (
        <Alert tone="info" title="Deactivated products">
          Some items use products that have since been deactivated. Saved items keep their copied details and prices.
        </Alert>
      ) : null}

      <DocumentViewer estimate={e} canInternal={canInternal} />

      <ContentCard title="History" description="Partner-safe values only.">
        {history.isPending ? (
          <LoadingState label="Loading history…" />
        ) : history.isError ? (
          <ErrorState error={history.error} onRetry={() => history.refetch()} />
        ) : (
          <Timeline entries={history.data.map(historyEntry)} />
        )}
      </ContentCard>

      <p className="text-xs text-muted-foreground">
        Created {formatDateTime(e.createdAt)}
        {e.createdByName ? ` by ${e.createdByName}` : ""} · updated {formatDateTime(e.updatedAt)}
      </p>

      <ConfirmDialog
        open={pending === "convert"}
        title={`Convert ${e.estimateNumber} to an invoice?`}
        confirmLabel="Create draft invoice"
        busy={convert.isPending}
        onConfirm={() => convert.mutate(e)}
        onCancel={() => setPending(null)}
      >
        <p>
          A draft invoice is created with this estimate&apos;s lead, buyer, items, prices, discount and fee. The estimate becomes Invoiced and can no longer be
          edited. Add the billing details and place of supply on the draft; GST is then worked out per line and any difference from this estimate is shown
          before issue.
        </p>
        {e.status === "INVOICED" ? <p>This estimate is already invoiced. A new draft is possible only if its earlier invoice was cancelled.</p> : null}
      </ConfirmDialog>
      {pending && pending !== "delete" && pending !== "convert" ? (
        <ConfirmDialog
          open
          title={ACTIONS[pending].confirm}
          confirmLabel={ACTIONS[pending].label}
          busy={transition.isPending}
          onConfirm={() => transition.mutate({ action: pending, e })}
          onCancel={() => setPending(null)}
        >
          <p>{ACTIONS[pending].body}</p>
        </ConfirmDialog>
      ) : null}
      <ArchiveDialog
        open={pending === "delete"}
        title={`Archive ${e.estimateNumber}?`}
        confirmLabel="Archive estimate"
        busy={remove.isPending}
        serverError={reasonError}
        onConfirm={(why) => {
          setReasonError(null);
          remove.mutate({ e, why });
        }}
        onCancel={() => setPending(null)}
      >
        {e.status !== "DRAFT" ? (
          <Alert tone="warning" title={`This estimate is ${e.statusLabel.toLowerCase()}`}>
            The client or partner may already have it. Archiving doesn&apos;t change the lead&apos;s stage.
          </Alert>
        ) : null}
        <p>The estimate leaves the register. Its number is not reused, its history is kept, and its products stay protected from deletion.</p>
      </ArchiveDialog>
    </PageContainer>
  );
}

/**
 * Printable copies rendered by the server. The HTML is shown in a sandboxed frame without scripts; Print uses the
 * browser's print dialog (Save as PDF). The internal copy needs INTERNAL_B2C read and is never sent to partners.
 */
function DocumentViewer({ estimate: e, canInternal }: { estimate: Estimate; canInternal: boolean }) {
  const [copy, setCopy] = useState<"partner" | "internal">("partner");
  const frame = useRef<HTMLIFrameElement>(null);
  const doc = useQuery({
    queryKey: [...queryKeys.estimates.detail(e.id), "document", copy, e.version],
    queryFn: () => (copy === "internal" ? estimateService.internalDocument(e.id) : estimateService.document(e.id)),
  });

  const body = doc.isPending ? (
    <LoadingState label="Loading document…" />
  ) : doc.isError ? (
    <ErrorState error={doc.error} onRetry={() => doc.refetch()} />
  ) : (
    <iframe
      ref={frame}
      title={copy === "internal" ? "Internal B2C copy" : "Partner copy"}
      srcDoc={doc.data.text}
      sandbox="allow-same-origin allow-modals"
      className="h-[720px] w-full rounded-md border border-border bg-white"
    />
  );

  const tools = doc.data ? (
    <div className="flex gap-2">
      <Button size="sm" onClick={() => frame.current?.contentWindow?.print()}>
        <Printer aria-hidden="true" /> Print
      </Button>
      <Button size="sm" onClick={() => saveTextFile(doc.data.text, doc.data.filename, "text/html;charset=utf-8")}>
        <Download aria-hidden="true" /> Download HTML
      </Button>
    </div>
  ) : null;

  return (
    <ContentCard title="Document" description={copy === "internal" ? "Internal only. Never send this copy to the partner or client." : "The copy for the client or partner."} action={tools}>
      {canInternal ? (
        <Tabs value={copy} onValueChange={(v) => setCopy(v as "partner" | "internal")}>
          <TabsList aria-label="Document copy">
            <TabsTrigger value="partner">Partner copy</TabsTrigger>
            <TabsTrigger value="internal">Internal B2C copy</TabsTrigger>
          </TabsList>
          <TabsContent value="partner" className="mt-3">
            {copy === "partner" ? body : null}
          </TabsContent>
          <TabsContent value="internal" className="mt-3">
            {copy === "internal" ? body : null}
          </TabsContent>
        </Tabs>
      ) : (
        body
      )}
    </ContentCard>
  );
}

function historyEntry(h: EstimateHistoryEntry): TimelineEntry {
  const status = h.changes.find((c) => c.field === "status");
  const tone: TimelineEntry["tone"] =
    h.action === "ESTIMATE_DELETED" || status?.to === "REJECTED" ? "danger" : status?.to === "ACCEPTED" || h.action === "ESTIMATE_RESTORED" ? "success" : h.action === "ESTIMATE_CREATED" ? "neutral" : "info";
  // A creation lists the headline values, plus the fee override (D-13) when there was one.
  const overridden = h.changes.some((c) => c.field === "feeOverrideReason");
  const CREATED_FIELDS = ["status", "estimateNumber", "grandTotal", ...(overridden ? ["defaultFeePercent", "feePercent", "feeOverrideReason"] : [])];
  const archived = h.action === "ESTIMATE_DELETED" || h.action === "ESTIMATE_RESTORED";
  // A revision says why it was made; an archive says why it was archived, and a restore recalls that reason.
  const reasonChange = h.changes.find((c) => c.field === "revisionReason" || c.field === "reason" || c.field === "archiveReason");
  const reason = reasonChange ? { ...reasonChange, label: reasonLabel(h.action) } : undefined;
  const changes = archived
    ? []
    : h.changes.filter((c) => c.field !== "revisionReason" && (h.action !== "ESTIMATE_CREATED" || CREATED_FIELDS.includes(c.field)));
  const summary =
    changes.length > 0 && h.action !== "ESTIMATE_STATUS_CHANGED"
      ? changes.map((c) => `${c.field === "lines" ? linesLabel(c) : FIELD_LABELS[c.field] ?? c.field}: ${c.field === "lines" ? "changed" : h.action === "ESTIMATE_CREATED" ? show(c.to, c.field) : `${show(c.from, c.field)} → ${show(c.to, c.field)}`}`).join("; ")
      : undefined;
  return {
    id: h.id,
    tone,
    title: status && h.action === "ESTIMATE_STATUS_CHANGED" ? `Status: ${show(status.from)} → ${show(status.to)}` : (HISTORY_TITLES[h.action] ?? h.action),
    time: `${formatDateTime(h.at)}${h.actorName ? ` · ${h.actorName}` : ""}`,
    description:
      reason || summary ? (
        <>
          {reason ? (
            <p>
              <span className="font-medium text-foreground">{reason.label}:</span> {show(reasonChange?.field === "archiveReason" ? reason.from : reason.to)}
            </p>
          ) : null}
          {summary ? <p>{summary}</p> : null}
        </>
      ) : undefined,
  };
}

function reasonLabel(action: string): string {
  return action === "ESTIMATE_DELETED" ? "Reason for archiving" : action === "ESTIMATE_RESTORED" ? "It had been archived because" : FIELD_LABELS.revisionReason;
}

type AuditLine = { kind?: string };
const linesOf = (value: unknown, installation: boolean): string =>
  JSON.stringify((Array.isArray(value) ? (value as AuditLine[]) : []).filter((l) => (l.kind === "INSTALLATION") === installation).map((l) => ({ ...l, lineNo: undefined })));

/** Product and installation lines are listed together in the audit entry; say which section changed. */
function linesLabel(c: { from: unknown; to: unknown }): string {
  const installation = linesOf(c.from, true) !== linesOf(c.to, true);
  const products = linesOf(c.from, false) !== linesOf(c.to, false);
  if (installation && !products) return "Installation";
  if (installation) return "Items and installation";
  return FIELD_LABELS.lines;
}

const MONEY_FIELDS = new Set(["grandTotal"]);
const PERCENT_FIELDS = new Set(["discountPercent", "feePercent", "defaultFeePercent"]);
const DATE_FIELDS = new Set(["estimateDate", "validUntil", "sentOn"]);

function show(value: unknown, field?: string): string {
  if (value === null || value === undefined || value === "") return NOT_AVAILABLE;
  if (field && MONEY_FIELDS.has(field)) return formatCurrency(value as string);
  if (field && PERCENT_FIELDS.has(field)) return formatPercent(value as string);
  if (field && DATE_FIELDS.has(field)) return formatDate(String(value));
  if (typeof value === "boolean") return value ? "yes" : "no";
  if (typeof value === "object") return "changed";
  const text = String(value);
  // Enum codes (DRAFT, ACCEPTED) read better humanised; numbers and estimate numbers stay as they are.
  return /^[A-Z_]+$/.test(text) ? text.charAt(0) + text.slice(1).toLowerCase().replaceAll("_", " ") : text;
}
