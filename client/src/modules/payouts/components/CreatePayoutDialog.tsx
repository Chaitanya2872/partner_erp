import { useState, type ReactNode } from "react";
import { Link, useNavigate } from "react-router";
import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useDebounce } from "@/hooks/useDebounce";
import { FormField, FormGrid } from "@/components/forms/FormField";
import { DateInput } from "@/components/forms/inputs";
import { SearchableSelect } from "@/components/forms/SearchableSelect";
import { LoadingState, ErrorState } from "@/components/feedback/States";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { Textarea } from "@/components/ui/Input";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { businessToday, formatCount, formatCurrency } from "@/utils/format";
import { payoutService } from "../services/payoutService";
import { linkClass, PayoutLinesTable, SignedAmount, TdsBreakdown } from "./PayoutParts";

/**
 * Generates a draft payout for one partner: every eligible receipt up to the as-of date plus approved adjustments.
 * The preview is read-only; nothing is allocated until "Create draft". The server recalculates everything on create.
 */
export function CreatePayoutDialog({ open, onClose, partnerId }: { open: boolean; onClose: () => void; partnerId?: string }) {
  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()} title="Create payout" description="A draft for one partner. Commission is from realised collections excl. GST." wide>
      {open ? <CreatePayoutForm onClose={onClose} initialPartnerId={partnerId} /> : null}
    </Dialog>
  );
}

function CreatePayoutForm({ onClose, initialPartnerId }: { onClose: () => void; initialPartnerId?: string }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const today = businessToday();
  // One key per opened form: pressing Create twice returns the draft already generated.
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const [search, setSearch] = useState("");
  const [partnerId, setPartnerId] = useState(initialPartnerId ?? "");
  const [asOfDate, setAsOfDate] = useState(today);
  const [notes, setNotes] = useState("");
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const q = useDebounce(search.trim());

  const balances = useQuery({
    queryKey: queryKeys.payouts.balances(q),
    queryFn: ({ signal }) => payoutService.balances(q, signal),
    placeholderData: keepPreviousData,
  });
  const preview = useQuery({
    queryKey: queryKeys.payouts.preview(partnerId, asOfDate),
    queryFn: () => payoutService.preview(partnerId, asOfDate),
    enabled: !!partnerId && !!asOfDate && asOfDate <= today,
    staleTime: 0,
  });

  const options = (balances.data ?? []).map((b) => ({
    value: b.partnerId,
    label: b.partnerName,
    description: `${formatCurrency(b.eligibleCommission)} eligible · ${formatCount(b.eligibleReceipts, "receipt")}${b.inProgressPayouts > 0 ? " · payout in progress" : ""}`,
  }));

  const create = async () => {
    if (!partnerId) return setFormError("Choose the partner.");
    setSaving(true);
    setFormError(null);
    try {
      const saved = await payoutService.create({ partnerId, asOfDate, notes, idempotencyKey });
      toast.success(`Draft ${saved.payoutNumber ?? "payout"} created for ${saved.partner.name}.`);
      await queryClient.invalidateQueries({ queryKey: queryKeys.payouts.all });
      onClose();
      void navigate(`/payouts/${saved.id}`);
    } catch (error) {
      setFormError(errorMessage(error));
      void preview.refetch();
    } finally {
      setSaving(false);
    }
  };

  const p = preview.data;
  return (
    <div className="space-y-4">
      {formError ? <Alert tone="danger">{formError}</Alert> : null}
      <FormGrid>
        <FormField label="Partner" required hint="Partners with eligible commission or carried adjustments.">
          <SearchableSelect
            aria-label="Partner"
            options={options}
            value={partnerId}
            onChange={(v) => {
              setPartnerId(v);
              setFormError(null);
            }}
            onQueryChange={setSearch}
            loading={balances.isFetching}
            placeholder="Choose a partner"
            searchPlaceholder="Search partners"
            emptyText="No partner has anything to pay."
          />
        </FormField>
        <FormField label="Receipts up to" required hint="Receipts dated on or before this day. Sets the financial year for TDS.">
          <DateInput value={asOfDate} max={today} onChange={(e) => setAsOfDate(e.target.value)} />
        </FormField>
      </FormGrid>

      {!partnerId ? (
        <p className="text-sm text-muted-foreground">Choose a partner to see what would be paid.</p>
      ) : preview.isPending ? (
        <LoadingState label="Calculating…" />
      ) : preview.isError ? (
        <ErrorState error={preview.error} onRetry={() => preview.refetch()} />
      ) : p ? (
        <div className="space-y-4">
          {p.blockers.map((b) => (
            <Alert key={b} tone="warning">
              {b}
            </Alert>
          ))}
          {p.openPayout ? (
            <p className="text-sm">
              Open payout:{" "}
              <Link to={`/payouts/${p.openPayout.id}`} className={`font-mono ${linkClass}`} onClick={onClose}>
                {p.openPayout.payoutNumber}
              </Link>{" "}
              ({p.openPayout.statusLabel.toLowerCase()})
            </p>
          ) : null}
          <div className="grid gap-3 rounded-md border border-border p-3 text-sm sm:grid-cols-4">
            <Figure label="Receipts">{formatCurrency(p.receiptsTotal)}</Figure>
            <Figure label="Adjustments">
              <SignedAmount amount={p.adjustmentsTotal} />
            </Figure>
            <Figure label="TDS">{p.tds.tdsAmount !== null ? formatCurrency(p.tds.tdsAmount) : "Not calculated"}</Figure>
            <Figure label="Net payable">{p.netAmount !== null ? <SignedAmount amount={p.netAmount} /> : "—"}</Figure>
          </div>
          {p.tds.message && !p.blockers.includes(p.tds.message) ? <Alert tone="info">{p.tds.message}</Alert> : null}
          <details className="rounded-md border border-border p-3 text-sm">
            <summary className="cursor-pointer font-medium">
              {p.lines.length} line{p.lines.length === 1 ? "" : "s"} · commission <SignedAmount amount={p.commissionTotal} />
            </summary>
            <div className="mt-3">
              <PayoutLinesTable lines={p.lines} caption="Lines in this payout" canOpenPayments={false} canOpenInvoices={false} />
            </div>
          </details>
          <details className="rounded-md border border-border p-3 text-sm">
            <summary className="cursor-pointer font-medium">TDS breakdown ({p.fyLabel})</summary>
            <div className="mt-3">
              <TdsBreakdown tds={p.tds} commission={p.commissionTotal} fyLabel={p.fyLabel} />
            </div>
          </details>
        </div>
      ) : null}

      <FormField label="Notes" hint="Optional. At most 500 characters.">
        <Textarea rows={2} value={notes} maxLength={500} onChange={(e) => setNotes(e.target.value)} />
      </FormField>

      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button onClick={onClose} disabled={saving}>
          Cancel
        </Button>
        <Button variant="primary" onClick={create} loading={saving} loadingText="Creating…" disabled={!p?.canCreate}>
          Create draft
        </Button>
      </div>
    </div>
  );
}

function Figure({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="font-mono font-medium tabular">{children}</div>
    </div>
  );
}
