import { useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Ban, Download, FileCheck2, Pencil, Printer, ShieldCheck, Trash2 } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { ErrorState, LoadingState } from "@/components/feedback/States";
import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";
import { ContentCard, PageActions, PageContainer, PageHeader } from "@/components/layout/Page";
import { DescriptionList, DetailRow, Timeline, type TimelineEntry } from "@/components/data/DataDisplay";
import { FormField } from "@/components/forms/FormField";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { Textarea } from "@/components/ui/Input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/Tabs";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { ApiRequestError } from "@/services/api";
import { saveTextFile } from "@/utils/download";
import { formatCurrency, formatDate, formatDateTime, formatPercent, NOT_AVAILABLE } from "@/utils/format";
import { invoiceService } from "../services/invoiceService";
import { useInvoice, useInvoiceMeta } from "../hooks/useInvoices";
import { EstimateComparisonNotice, InvoiceLinesTable, InvoiceStatusBadge, InvoiceTotalsCard, TaxTreatmentNotice } from "../components/InvoiceParts";
import type { Invoice, InvoiceHistoryEntry, InvoiceParty } from "../types/invoice.types";

type Pending = "issue" | "confirm" | "cancel" | "discard" | null;

const HISTORY_TITLES: Record<string, string> = {
  INVOICE_CREATED: "Draft created",
  INVOICE_CONVERTED: "Converted from estimate",
  INVOICE_UPDATED: "Draft edited",
  INVOICE_TAX_CONFIRMED: "Tax treatment confirmed",
  INVOICE_ISSUED: "Issued",
  INVOICE_CANCELLED: "Cancelled",
  INVOICE_DRAFT_DELETED: "Draft discarded",
};

const FIELD_LABELS: Record<string, string> = {
  status: "Status", invoiceNumber: "Number", invoiceDate: "Date", dueDate: "Due", dueDays: "Due days", discountPercent: "Discount %",
  feePercent: "Fee %", billTo: "Bill to", notes: "Notes", lines: "Items", grandTotal: "Total", gstAmount: "GST", taxableAmount: "Taxable",
  billingName: "Billing name", billingAddress: "Billing address", billingStateCode: "Billing state", billingGstin: "Customer GSTIN",
  placeOfSupplyCode: "Place of supply", placeOfSupplyBasis: "Basis", taxType: "Tax type", reason: "Reason", note: "Note",
  estimateNumber: "Estimate", leadId: "Lead", partnerId: "Partner", supplierStateCode: "Supplier state", supplierGstin: "Supplier GSTIN",
};

export function InvoiceDetailsPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { canRead, canWrite, canPerform } = useAuth();
  const invoice = useInvoice(id);
  const meta = useInvoiceMeta();
  const history = useQuery({
    queryKey: queryKeys.invoices.history(id),
    queryFn: ({ signal }) => invoiceService.history(id, signal),
  });
  const [pending, setPending] = useState<Pending>(null);
  const [text, setText] = useState("");
  const [textError, setTextError] = useState<string | null>(null);

  const close = () => {
    setPending(null);
    setText("");
    setTextError(null);
  };
  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.invoices.all }),
      queryClient.invalidateQueries({ queryKey: queryKeys.estimates.all }),
      queryClient.invalidateQueries({ queryKey: queryKeys.leads.all }),
    ]);
  const onError = (error: unknown) => {
    const field = error instanceof ApiRequestError ? error.fieldErrors.find((f) => f.field === "note" || f.field === "reason") : undefined;
    if (field) {
      setTextError(field.message);
      return;
    }
    toast.error(errorMessage(error));
    close();
    void refresh();
  };

  const issue = useMutation({
    mutationFn: (i: Invoice) => invoiceService.issue(i.id, { version: i.version, expectedGrandTotal: i.totals.grandTotal }),
    onSuccess: async (saved) => {
      toast.success(`Issued as ${saved.invoiceNumber}.`);
      close();
      await refresh();
    },
    onError,
  });
  const confirm = useMutation({
    mutationFn: (i: Invoice) => invoiceService.confirmTax(i.id, { version: i.version, note: text.trim() }),
    onSuccess: async () => {
      toast.success("Tax treatment confirmed.");
      close();
      await refresh();
    },
    onError,
  });
  const cancel = useMutation({
    mutationFn: (i: Invoice) => invoiceService.cancel(i.id, { version: i.version, reason: text.trim() }),
    onSuccess: async (saved) => {
      toast.success(`${saved.invoiceNumber} cancelled.`);
      close();
      await refresh();
    },
    onError,
  });
  const discard = useMutation({
    mutationFn: (i: Invoice) => invoiceService.discard(i.id),
    onSuccess: async (_, i) => {
      toast.success("Draft invoice discarded.");
      close();
      queryClient.removeQueries({ queryKey: queryKeys.invoices.detail(i.id) });
      await refresh();
      navigate("/invoices", { replace: true });
    },
    onError,
  });

  if (invoice.isPending) return <LoadingState label="Loading invoice…" />;
  if (invoice.isError) {
    return (
      <PageContainer>
        <PageHeader title="Invoice" back={{ to: "/invoices", label: "Invoices" }} />
        <ErrorState error={invoice.error} onRetry={() => invoice.refetch()} />
      </PageContainer>
    );
  }

  const i = invoice.data;
  const draft = i.status === "DRAFT";
  const canEdit = draft && canWrite("INVOICES");
  const canDiscard = draft && canPerform("INVOICES", "DELETE");
  const canConfirm = draft && canPerform("INVOICES", "APPROVE") && i.tax.confirmationRequired && !i.tax.confirmed && i.tax.missing.length === 0;
  const canCancel = i.status === "ISSUED" && canPerform("INVOICES", "DELETE");
  const hasPayments = i.paidAmount !== null && Number(i.paidAmount) !== 0;
  const supplierIncomplete = draft && meta.data && !meta.data.supplierComplete;
  const busy = issue.isPending || confirm.isPending || cancel.isPending || discard.isPending;
  const withText = (action: "confirm" | "cancel", max = 500) => () => {
    const value = text.trim();
    if (!value) return setTextError(action === "confirm" ? "Explain why this tax treatment is right." : "Give the reason for cancelling.");
    if (value.length > max) return setTextError(`Use at most ${max} characters.`);
    if (action === "confirm") confirm.mutate(i);
    else cancel.mutate(i);
  };

  return (
    <PageContainer>
      <PageHeader
        title={i.invoiceNumber ?? "Draft invoice"}
        documentTitle={i.invoiceNumber ?? "Draft invoice"}
        back={{ to: "/invoices", label: "Invoices" }}
        badge={<InvoiceStatusBadge status={i.status} paymentStatus={i.paymentStatus} />}
        description={`${i.billing.name ?? i.lead?.clientName ?? NOT_AVAILABLE} · ${i.partner.name} · ${formatCurrency(i.totals.grandTotal)}`}
        actions={
          canEdit || canDiscard || canConfirm || canCancel ? (
            <PageActions>
              {canDiscard ? (
                <Button variant="destructive-outline" onClick={() => setPending("discard")} disabled={busy}>
                  <Trash2 aria-hidden="true" /> Discard draft
                </Button>
              ) : null}
              {canCancel ? (
                <Button variant="destructive-outline" onClick={() => setPending("cancel")} disabled={busy}>
                  <Ban aria-hidden="true" /> Cancel invoice
                </Button>
              ) : null}
              {canConfirm ? (
                <Button onClick={() => setPending("confirm")} disabled={busy}>
                  <ShieldCheck aria-hidden="true" /> Confirm tax
                </Button>
              ) : null}
              {canEdit ? (
                <Button asChild>
                  <Link to={`/invoices/${i.id}/edit`}>
                    <Pencil aria-hidden="true" /> Edit
                  </Link>
                </Button>
              ) : null}
              {canEdit ? (
                <Button variant="primary" onClick={() => setPending("issue")} disabled={busy}>
                  <FileCheck2 aria-hidden="true" /> Issue invoice
                </Button>
              ) : null}
            </PageActions>
          ) : undefined
        }
      />

      {draft ? (
        <Alert tone="info" title="Draft: not a tax invoice">
          Drafts have no invoice number. Issuing assigns the next INV number, dates the invoice today and freezes its lines, prices, tax and customer details.
        </Alert>
      ) : null}
      {supplierIncomplete ? (
        <Alert tone="warning" title="GST registration incomplete">
          Set the supplier&apos;s registered state, GSTIN and address in Settings before issuing.
        </Alert>
      ) : null}
      {i.status === "CANCELLED" ? (
        <Alert tone="warning" title="Cancelled">
          Cancelled {formatDateTime(i.cancelledAt)}
          {i.cancelledByName ? ` by ${i.cancelledByName}` : ""}: “{i.cancelReason}”. The number {i.invoiceNumber} is never reused.
        </Alert>
      ) : null}
      <TaxTreatmentNotice tax={i.tax} />
      {draft && i.comparison ? <EstimateComparisonNotice comparison={i.comparison} taxDecided={!!i.tax.taxType} /> : null}

      <div className="grid gap-4 lg:grid-cols-3">
        <ContentCard title="Invoice" className="lg:col-span-2">
          <DescriptionList columns={2}>
            <DetailRow label="Lead">
              {i.lead ? (
                canRead("LEADS") && !i.lead.deleted ? (
                  <Link to={`/leads/${i.lead.id}`} className="text-accent-ink hover:underline">
                    {i.lead.clientName} · {i.lead.leadNumber}
                  </Link>
                ) : (
                  `${i.lead.clientName} · ${i.lead.leadNumber}`
                )
              ) : (
                NOT_AVAILABLE
              )}
            </DetailRow>
            <DetailRow label="Partner">
              {canRead("PARTNERS") ? (
                <Link to={`/partners/${i.partner.id}`} className="text-accent-ink hover:underline">
                  {i.partner.name}
                </Link>
              ) : (
                i.partner.name
              )}
            </DetailRow>
            <DetailRow label="Estimate">
              {i.estimate ? (
                canRead("ESTIMATES") ? (
                  <Link to={`/estimates/${i.estimate.id}`} className="font-mono text-accent-ink hover:underline">
                    {i.estimate.estimateNumber}
                  </Link>
                ) : (
                  <span className="font-mono">{i.estimate.estimateNumber}</span>
                )
              ) : (
                "Direct invoice"
              )}
            </DetailRow>
            <DetailRow label="Bill to">{i.billToLabel}</DetailRow>
            <DetailRow label="Invoice date">{i.invoiceDate ? formatDate(i.invoiceDate) : "On issue"}</DetailRow>
            <DetailRow label="Due">{i.dueDate ? formatDate(i.dueDate) : `${i.dueDays} days after issue`}</DetailRow>
            <DetailRow label="Place of supply">
              {i.placeOfSupplyCode ? `${i.placeOfSupplyCode} · ${i.placeOfSupplyName}` : NOT_AVAILABLE}
              {i.placeOfSupplyBasisLabel ? <div className="text-xs text-muted-foreground">{i.placeOfSupplyBasisLabel}</div> : null}
            </DetailRow>
            <DetailRow label="Tax">{i.tax.taxType ? (i.tax.taxType === "INTER" ? "IGST" : `CGST + ${i.tax.stateTaxLabel ?? "SGST"}`) : "Not decided"}</DetailRow>
            <DetailRow label="Fee rate">{formatPercent(i.feePercent)}</DetailRow>
            {i.status !== "DRAFT" ? (
              <DetailRow label="Paid · balance">
                {formatCurrency(i.paidAmount)} · {formatCurrency(i.balance)}
              </DetailRow>
            ) : null}
          </DescriptionList>
        </ContentCard>
        <InvoiceTotalsCard totals={i.totals} tax={i.tax} discountPercent={i.discountPercent} />
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <PartyCard title={draft ? "Supplier (copied on issue)" : "Supplier"} party={i.supplier} empty={draft ? "Copied from the GST registration when issued." : undefined} />
        <PartyCard title="Billed to" party={i.billing} />
      </div>

      <ContentCard title="Tax breakdown" description="Each line keeps its taxable value, rate and tax, rounded to the paisa.">
        <InvoiceLinesTable lines={i.lines} tax={i.tax} />
      </ContentCard>

      <DocumentViewer invoice={i} canInternal={canRead("INTERNAL_B2C") && !draft} />

      <ContentCard title="History" description="Partner-safe values only; GSTINs are masked.">
        {history.isPending ? (
          <LoadingState label="Loading history…" />
        ) : history.isError ? (
          <ErrorState error={history.error} onRetry={() => history.refetch()} />
        ) : (
          <Timeline entries={history.data.map(historyEntry)} />
        )}
      </ContentCard>

      <p className="text-xs text-muted-foreground">
        Created {formatDateTime(i.createdAt)}
        {i.createdByName ? ` by ${i.createdByName}` : ""}
        {i.issuedAt ? ` · issued ${formatDateTime(i.issuedAt)}${i.issuedByName ? ` by ${i.issuedByName}` : ""}` : ""} · updated {formatDateTime(i.updatedAt)}
      </p>

      <ConfirmDialog open={pending === "issue"} title="Issue this invoice?" confirmLabel="Issue invoice" busy={issue.isPending} onConfirm={() => issue.mutate(i)} onCancel={close}>
        <p>
          It gets the next invoice number, today&apos;s date and a due date {i.dueDays} days later. After issue its lines, prices, tax, customer details and number
          can&apos;t change; it can only be cancelled.
        </p>
        <p>
          Total incl. GST: <strong className="font-mono">{formatCurrency(i.totals.grandTotal)}</strong>
          {i.tax.taxType ? ` (${i.tax.taxType === "INTER" ? "IGST" : `CGST + ${i.tax.stateTaxLabel ?? "SGST"}`})` : ""}.
        </p>
        {i.comparison ? <EstimateComparisonNotice comparison={i.comparison} taxDecided={!!i.tax.taxType} /> : null}
        {i.lead && i.lead.stage !== "WON" ? <p>The lead is marked Won.</p> : null}
      </ConfirmDialog>

      <ConfirmDialog open={pending === "discard"} title="Discard this draft?" confirmLabel="Discard draft" danger busy={discard.isPending} onConfirm={() => discard.mutate(i)} onCancel={close}>
        <p>The draft leaves the register; its history is kept.{i.estimate ? ` Estimate ${i.estimate.estimateNumber} goes back to its earlier status and can be converted again.` : ""}</p>
      </ConfirmDialog>

      <TextDialog
        open={pending === "confirm"}
        title="Confirm the tax treatment"
        label="Why is this treatment right?"
        confirmLabel="Confirm tax"
        value={text}
        error={textError}
        busy={confirm.isPending}
        onChange={(v) => {
          setText(v);
          setTextError(null);
        }}
        onConfirm={withText("confirm")}
        onCancel={close}
      >
        <p>
          You confirm <strong>{i.tax.taxTypeLabel}</strong> for place of supply {i.placeOfSupplyCode} · {i.placeOfSupplyName} ({i.placeOfSupplyBasisLabel}). Changing
          the billing state, GSTIN or place of supply later clears this confirmation.
        </p>
        <ul>
          {i.tax.reasons.map((r) => (
            <li key={r.code}>{r.message}</li>
          ))}
        </ul>
      </TextDialog>

      <TextDialog
        open={pending === "cancel"}
        title={`Cancel ${i.invoiceNumber}?`}
        label="Reason for cancelling"
        confirmLabel="Cancel invoice"
        danger
        value={text}
        error={textError}
        busy={cancel.isPending}
        onChange={(v) => {
          setText(v);
          setTextError(null);
        }}
        onConfirm={withText("cancel")}
        onCancel={close}
      >
        {hasPayments ? (
          <Alert tone="warning" title="Payments recorded">
            Invoices with payments or partner payouts can&apos;t be cancelled until credit notes are available (Phase 10).
          </Alert>
        ) : null}
        <p>The invoice stays on record as cancelled and its number is never reused. The reason is kept in its history.</p>
      </TextDialog>
    </PageContainer>
  );
}

function PartyCard({ title, party, empty }: { title: string; party: InvoiceParty; empty?: string }) {
  const blank = !party.name && !party.address && !party.gstin;
  return (
    <ContentCard title={title}>
      {blank ? (
        <p className="text-sm text-muted-foreground">{empty ?? "Not given yet."}</p>
      ) : (
        <DescriptionList>
          <DetailRow label="Name">{party.name ?? NOT_AVAILABLE}</DetailRow>
          <DetailRow label="Address">
            <span className="whitespace-pre-line">{party.address ?? NOT_AVAILABLE}</span>
          </DetailRow>
          <DetailRow label="State">{party.stateCode ? `${party.stateCode} · ${party.stateName}` : NOT_AVAILABLE}</DetailRow>
          <DetailRow label="GSTIN" mono>
            {party.gstin ?? "Unregistered"}
          </DetailRow>
        </DescriptionList>
      )}
    </ContentCard>
  );
}

interface TextDialogProps {
  open: boolean;
  title: string;
  label: string;
  confirmLabel: string;
  danger?: boolean;
  value: string;
  error: string | null;
  busy: boolean;
  children: React.ReactNode;
  onChange: (value: string) => void;
  onConfirm: () => void;
  onCancel: () => void;
}

/** A confirmation that needs a written reason or note (kept in the audit history). */
function TextDialog({ open, title, label, confirmLabel, danger, value, error, busy, children, onChange, onConfirm, onCancel }: TextDialogProps) {
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => !next && !busy && onCancel()}
      title={title}
      footer={
        <>
          <Button onClick={onCancel} disabled={busy}>
            Back
          </Button>
          <Button variant={danger ? "destructive" : "primary"} onClick={onConfirm} loading={busy} loadingText="Working…">
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className="space-y-3 text-sm [&_ul]:list-disc [&_ul]:space-y-0.5 [&_ul]:pl-5">
        {children}
        <FormField label={label} error={error ?? undefined} required hint="At most 500 characters.">
          <Textarea rows={3} value={value} onChange={(e) => onChange(e.target.value)} />
        </FormField>
      </div>
    </Dialog>
  );
}

/**
 * Printable copies rendered by the server, shown in a sandboxed frame without scripts. Drafts print as "Draft
 * invoice · not a tax invoice". The internal copy needs INTERNAL_B2C read, exists only once issued, and is never
 * sent to partners.
 */
function DocumentViewer({ invoice: i, canInternal }: { invoice: Invoice; canInternal: boolean }) {
  const [copy, setCopy] = useState<"partner" | "internal">("partner");
  const frame = useRef<HTMLIFrameElement>(null);
  const doc = useQuery({
    queryKey: [...queryKeys.invoices.detail(i.id), "document", copy, i.version],
    queryFn: () => (copy === "internal" ? invoiceService.internalDocument(i.id) : invoiceService.document(i.id)),
  });

  const body = doc.isPending ? (
    <LoadingState label="Loading document…" />
  ) : doc.isError ? (
    <ErrorState error={doc.error} onRetry={() => doc.refetch()} />
  ) : (
    <iframe
      ref={frame}
      title={copy === "internal" ? "Internal B2C copy" : "Invoice copy"}
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
            <TabsTrigger value="partner">Invoice copy</TabsTrigger>
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

function historyEntry(h: InvoiceHistoryEntry): TimelineEntry {
  const tone: TimelineEntry["tone"] =
    h.action === "INVOICE_CANCELLED" || h.action === "INVOICE_DRAFT_DELETED" ? "danger" : h.action === "INVOICE_ISSUED" ? "success" : h.action === "INVOICE_CREATED" || h.action === "INVOICE_CONVERTED" ? "neutral" : "info";
  // Creation, conversion and issue list headline values only; edits list each change.
  const HEADLINE = ["invoiceNumber", "estimateNumber", "grandTotal", "taxType", "placeOfSupplyCode", "reason", "note"];
  const summaryOnly = h.action !== "INVOICE_UPDATED";
  const changes = h.changes.filter((c) => !summaryOnly || HEADLINE.includes(c.field));
  return {
    id: h.id,
    tone,
    title: HISTORY_TITLES[h.action] ?? h.action,
    time: `${formatDateTime(h.at)}${h.actorName ? ` · ${h.actorName}` : ""}`,
    description:
      changes.length > 0
        ? changes.map((c) => `${FIELD_LABELS[c.field] ?? c.field}: ${c.field === "lines" ? "changed" : summaryOnly ? show(c.to, c.field) : `${show(c.from, c.field)} → ${show(c.to, c.field)}`}`).join("; ")
        : undefined,
  };
}

const MONEY_FIELDS = new Set(["grandTotal", "gstAmount", "taxableAmount"]);
const PERCENT_FIELDS = new Set(["discountPercent", "feePercent"]);
const DATE_FIELDS = new Set(["invoiceDate", "dueDate"]);

function show(value: unknown, field?: string): string {
  if (value === null || value === undefined || value === "") return NOT_AVAILABLE;
  if (field && MONEY_FIELDS.has(field)) return formatCurrency(value as string);
  if (field && PERCENT_FIELDS.has(field)) return formatPercent(value as string);
  if (field && DATE_FIELDS.has(field)) return formatDate(String(value));
  if (typeof value === "boolean") return value ? "yes" : "no";
  if (typeof value === "object") return "changed";
  const text = String(value);
  return /^[A-Z_]+$/.test(text) ? text.charAt(0) + text.slice(1).toLowerCase().replaceAll("_", " ") : text;
}
