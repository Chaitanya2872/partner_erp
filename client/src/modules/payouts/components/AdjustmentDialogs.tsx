import { useState } from "react";
import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useDebounce } from "@/hooks/useDebounce";
import { FormField } from "@/components/forms/FormField";
import { CurrencyInput } from "@/components/forms/inputs";
import { SearchableSelect } from "@/components/forms/SearchableSelect";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { Select, Textarea } from "@/components/ui/Input";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { ApiRequestError } from "@/services/api";
import { formatCurrency } from "@/utils/format";
import { partnerService } from "@/modules/partners/services/partnerService";
import { payoutService } from "../services/payoutService";
import type { PartnerAdjustment } from "../types/payout.types";
import { SignedAmount } from "./PayoutParts";

type Errors = Record<string, string>;

/**
 * A manual correction to a partner's commission (Payouts write). It takes effect only after someone else approves it,
 * and is then carried into the partner's next payout. Nothing is debited from a partner's account.
 */
export function NewAdjustmentDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()} title="New adjustment" description="Added to or deducted from the partner's next payout once approved.">
      {open ? <NewAdjustmentForm onClose={onClose} /> : null}
    </Dialog>
  );
}

function NewAdjustmentForm({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const [search, setSearch] = useState("");
  const [partnerId, setPartnerId] = useState("");
  const [direction, setDirection] = useState<"ADD" | "DEDUCT">("ADD");
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const q = useDebounce(search.trim());
  // Any partner can receive an adjustment, not only those with something to pay now.
  const partners = useQuery({
    queryKey: queryKeys.partners.list({ q, page: 0, size: 20, purpose: "adjustment" }),
    queryFn: ({ signal }) => partnerService.list({ q, page: 0, size: 20, sort: "name,asc" }, signal),
    placeholderData: keepPreviousData,
  });

  const submit = async () => {
    const next: Errors = {};
    if (!partnerId) next.partnerId = "Choose the partner.";
    if (!amount.trim() || Number(amount) === 0) next.amount = "Enter an amount greater than zero.";
    if (!reason.trim()) next.reason = "Give the reason for this adjustment.";
    else if (reason.trim().length > 500) next.reason = "Use at most 500 characters.";
    if (Object.keys(next).length > 0) return setErrors(next);
    setSaving(true);
    try {
      const signed = direction === "DEDUCT" ? `-${amount.trim()}` : amount.trim();
      const saved = await payoutService.createAdjustment({ partnerId, amount: signed, reason: reason.trim(), idempotencyKey });
      toast.success(`${saved.adjustmentNumber} is waiting for approval.`);
      await queryClient.invalidateQueries({ queryKey: queryKeys.payouts.all });
      onClose();
    } catch (error) {
      if (error instanceof ApiRequestError && error.fieldErrors.length > 0) setErrors(Object.fromEntries(error.fieldErrors.map((f) => [f.field, f.message])));
      else setErrors({ form: errorMessage(error) });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-4">
      {errors.form ? <Alert tone="danger">{errors.form}</Alert> : null}
      <FormField label="Partner" required error={errors.partnerId}>
        <SearchableSelect
          aria-label="Partner"
          options={(partners.data?.content ?? []).map((p) => ({ value: p.id, label: p.name, description: p.city ?? undefined }))}
          value={partnerId}
          onChange={(v) => {
            setPartnerId(v);
            setErrors({});
          }}
          onQueryChange={setSearch}
          loading={partners.isFetching}
          placeholder="Choose a partner"
          searchPlaceholder="Search partners"
        />
      </FormField>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField label="Type" required>
          <Select value={direction} onChange={(e) => setDirection(e.target.value as "ADD" | "DEDUCT")}>
            <option value="ADD">Add to the next payout</option>
            <option value="DEDUCT">Deduct from the next payout</option>
          </Select>
        </FormField>
        <FormField label="Amount" required error={errors.amount}>
          <CurrencyInput
            value={amount}
            onValueChange={(v) => {
              setAmount(v);
              setErrors({});
            }}
          />
        </FormField>
      </div>
      <FormField label="Reason" required error={errors.reason} hint="Shown to the approver and kept with the payout. At most 500 characters.">
        <Textarea
          rows={3}
          value={reason}
          onChange={(e) => {
            setReason(e.target.value);
            setErrors({});
          }}
        />
      </FormField>
      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button onClick={onClose} disabled={saving}>
          Cancel
        </Button>
        <Button variant="primary" onClick={submit} loading={saving} loadingText="Saving…">
          Submit for approval
        </Button>
      </div>
    </div>
  );
}

/** Approves or rejects a pending adjustment (Payouts approve; never the person who raised it). */
export function DecideAdjustmentDialog({ adjustment, decision, onClose }: { adjustment: PartnerAdjustment; decision: "approve" | "reject"; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const reject = decision === "reject";

  const confirm = async () => {
    if (reject && !note.trim()) return setError("Give the reason for rejecting this adjustment.");
    if (note.trim().length > 500) return setError("Use at most 500 characters.");
    setSaving(true);
    try {
      const saved = await payoutService.decideAdjustment(adjustment.id, decision, note);
      toast.success(`${saved.adjustmentNumber} ${reject ? "rejected" : "approved"}.`);
      await queryClient.invalidateQueries({ queryKey: queryKeys.payouts.all });
      onClose();
    } catch (e) {
      if (e instanceof ApiRequestError && e.fieldErrors.length > 0) return setError(e.fieldErrors[0].message);
      toast.error(errorMessage(e));
      onClose();
      void queryClient.invalidateQueries({ queryKey: queryKeys.payouts.all });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog
      open
      onOpenChange={(next) => !next && !saving && onClose()}
      title={`${reject ? "Reject" : "Approve"} ${adjustment.adjustmentNumber}?`}
      footer={
        <>
          <Button onClick={onClose} disabled={saving}>
            Back
          </Button>
          <Button variant={reject ? "destructive" : "primary"} onClick={confirm} loading={saving} loadingText="Saving…">
            {reject ? "Reject adjustment" : "Approve adjustment"}
          </Button>
        </>
      }
    >
      <div className="space-y-3 text-sm">
        <p>
          {adjustment.partner.name}: <SignedAmount amount={adjustment.amount} /> — “{adjustment.reason}”
          {adjustment.createdByName ? ` (raised by ${adjustment.createdByName})` : ""}.
        </p>
        <p className="text-muted-foreground">
          {reject
            ? "A rejected adjustment is kept on record and never paid."
            : Number(adjustment.amount) < 0
              ? `${formatCurrency(String(adjustment.amount).replace("-", ""))} will be deducted from the partner's next payout.`
              : "It will be added to the partner's next payout."}
        </p>
        <FormField label={reject ? "Reason for rejecting" : "Note"} required={reject} error={error ?? undefined} hint={reject ? undefined : "Optional."}>
          <Textarea
            rows={3}
            value={note}
            onChange={(e) => {
              setNote(e.target.value);
              setError(null);
            }}
          />
        </FormField>
      </div>
    </Dialog>
  );
}
