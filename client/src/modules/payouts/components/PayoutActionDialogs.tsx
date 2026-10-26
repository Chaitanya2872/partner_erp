import { useId, useState, type ReactNode } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { FormField } from "@/components/forms/FormField";
import { CurrencyInput, DateInput } from "@/components/forms/inputs";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Checkbox } from "@/components/ui/Checkbox";
import { Dialog } from "@/components/ui/Dialog";
import { Input, Textarea } from "@/components/ui/Input";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { ApiRequestError } from "@/services/api";
import { businessToday, formatCurrency } from "@/utils/format";
import { payoutService } from "../services/payoutService";
import type { Payout } from "../types/payout.types";

export type PayoutAction = "recalculate" | "submit" | "approve" | "reject" | "cancel" | "processing" | "markPaid" | "fail" | "adjustTds";

interface ActionDialogProps {
  payout: Payout;
  action: PayoutAction | null;
  onClose: () => void;
}

/** Field errors the dialog shows inline; anything else (409 and the like) closes it with a toast and a refetch. */
type FieldErrors = Record<string, string>;

function useInvalidate(payout: Payout) {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.payouts.all }),
      queryClient.invalidateQueries({ queryKey: queryKeys.partners.detail(payout.partner.id) }),
    ]);
}

/**
 * Every payout transition. Each sends the version the user saw, so a payout changed by someone else meanwhile is
 * refused (409) rather than overwritten. The server decides what is allowed; buttons only follow `payout.actions`.
 */
export function PayoutActionDialog({ payout, action, onClose }: ActionDialogProps) {
  if (action === "markPaid") return <MarkPaidDialog payout={payout} open onClose={onClose} />;
  if (action === "adjustTds") return <TdsAdjustmentDialog payout={payout} open onClose={onClose} />;
  if (action === null) return null;
  return <TransitionDialog payout={payout} action={action} onClose={onClose} />;
}

const NUMBER = (p: Payout) => p.payoutNumber ?? "this payout";

const TRANSITIONS: Record<
  Exclude<PayoutAction, "markPaid" | "adjustTds">,
  { title: (p: Payout) => string; confirm: string; busy: string; done: string; field?: "reason" | "note"; label?: string; hint?: string; danger?: boolean; body: (p: Payout) => ReactNode }
> = {
  recalculate: {
    title: (p) => `Recalculate ${NUMBER(p)}?`,
    confirm: "Recalculate",
    busy: "Recalculating…",
    done: "recalculated",
    body: () => (
      <ul>
        <li>Reversed receipts are released and newly eligible receipts and approved adjustments are added.</li>
        <li>TDS is worked out again from the confirmed settings; a manual TDS change is discarded.</li>
        <li>A pending payout stays pending; the approver checks the new amounts.</li>
      </ul>
    ),
  },
  submit: {
    title: (p) => `Submit ${NUMBER(p)} for approval?`,
    confirm: "Submit for approval",
    busy: "Submitting…",
    done: "submitted for approval",
    body: (p) => (
      <p>
        An approver other than you checks the commission of <strong className="font-mono">{formatCurrency(p.commissionTotal)}</strong> and its TDS. You can still
        recalculate or cancel it until it is approved.
      </p>
    ),
  },
  approve: {
    title: (p) => `Approve ${NUMBER(p)}?`,
    confirm: "Approve payout",
    busy: "Approving…",
    done: "approved",
    field: "note",
    label: "Approval note",
    hint: "Optional. Recorded with the approval.",
    body: (p) => (
      <ul>
        <li>
          Net payable <strong className="font-mono">{p.netAmount !== null ? formatCurrency(p.netAmount) : "—"}</strong> after TDS of{" "}
          {p.tds.tdsAmount !== null ? formatCurrency(p.tds.tdsAmount) : "—"}.
        </li>
        <li>The server checks the TDS again; if anything changed, the payout must be recalculated first.</li>
        <li>Approving doesn&apos;t move money. Finance records the transfer separately.</li>
      </ul>
    ),
  },
  reject: {
    title: (p) => `Reject ${NUMBER(p)}?`,
    confirm: "Reject payout",
    busy: "Rejecting…",
    done: "rejected",
    field: "reason",
    label: "Reason for rejecting",
    danger: true,
    body: () => <p>The payout is closed and its receipts and adjustments become available for the next payout.</p>,
  },
  cancel: {
    title: (p) => `Cancel ${NUMBER(p)}?`,
    confirm: "Cancel payout",
    busy: "Cancelling…",
    done: "cancelled",
    field: "reason",
    label: "Reason for cancelling",
    danger: true,
    body: () => <p>The payout stays on record as cancelled; its receipts and adjustments become available for the next payout.</p>,
  },
  processing: {
    title: (p) => `Start the transfer for ${NUMBER(p)}?`,
    confirm: "Mark as processing",
    busy: "Saving…",
    done: "marked as processing",
    field: "note",
    label: "Note",
    hint: "Optional, for example the batch the transfer is in. Don't enter bank details.",
    body: (p) => (
      <p>
        Records that Finance started a transfer of <strong className="font-mono">{p.netAmount !== null ? formatCurrency(p.netAmount) : "—"}</strong> to the account ending{" "}
        <span className="font-mono">{p.partner.bankAccount ? p.partner.bankAccount.slice(-4) : "—"}</span>. Nothing is sent to a bank from here.
      </p>
    ),
  },
  fail: {
    title: (p) => `Record that the transfer for ${NUMBER(p)} failed?`,
    confirm: "Mark as failed",
    busy: "Saving…",
    done: "marked as failed",
    field: "reason",
    label: "Reason",
    hint: "For example: account closed, name mismatch.",
    danger: true,
    body: () => <p>The payout is closed as failed; its receipts and adjustments become available for a new payout.</p>,
  },
};

function TransitionDialog({ payout, action, onClose }: { payout: Payout; action: Exclude<PayoutAction, "markPaid" | "adjustTds">; onClose: () => void }) {
  const t = TRANSITIONS[action];
  const invalidate = useInvalidate(payout);
  const [text, setText] = useState("");
  const [errors, setErrors] = useState<FieldErrors>({});
  const run = useMutation({
    mutationFn: () =>
      payoutService.action(payout.id, action, payout.version, t.field ? { [t.field]: text.trim() || undefined } : {}),
    onSuccess: async (saved) => {
      toast.success(`${saved.payoutNumber ?? "Payout"} ${t.done}.`);
      await invalidate();
      onClose();
    },
    onError: (e) => handleError(e, setErrors, onClose, invalidate),
  });
  const confirm = () => {
    if (t.field === "reason" && !text.trim()) return setErrors({ reason: "Give the reason." });
    if (text.trim().length > 500) return setErrors({ [t.field ?? "reason"]: "Use at most 500 characters." });
    run.mutate();
  };
  const error = t.field ? errors[t.field] : undefined;
  return (
    <Dialog
      open
      onOpenChange={(next) => !next && !run.isPending && onClose()}
      title={t.title(payout)}
      footer={
        <>
          <Button onClick={onClose} disabled={run.isPending}>
            Back
          </Button>
          <Button variant={t.danger ? "destructive" : "primary"} onClick={confirm} loading={run.isPending} loadingText={t.busy}>
            {t.confirm}
          </Button>
        </>
      }
    >
      <div className="space-y-3 text-sm [&_ul]:list-disc [&_ul]:space-y-0.5 [&_ul]:pl-5">
        {t.body(payout)}
        {errors.form ? <Alert tone="danger">{errors.form}</Alert> : null}
        {t.field ? (
          <FormField label={t.label ?? "Note"} error={error} required={t.field === "reason"} hint={t.hint ?? "At most 500 characters."}>
            <Textarea
              rows={3}
              value={text}
              onChange={(e) => {
                setText(e.target.value);
                setErrors({});
              }}
            />
          </FormField>
        ) : null}
      </div>
    </Dialog>
  );
}

/** Confirms a transfer under processing was paid: the bank's reference and the date it went through. */
export function MarkPaidDialog({ payout, open, onClose }: { payout: Payout; open: boolean; onClose: () => void }) {
  const invalidate = useInvalidate(payout);
  const ackId = useId();
  const today = businessToday();
  const [paidDate, setPaidDate] = useState(today);
  const [reference, setReference] = useState("");
  const [acknowledged, setAcknowledged] = useState(false);
  const [errors, setErrors] = useState<FieldErrors>({});
  const reversed = payout.reversedSources;
  const run = useMutation({
    mutationFn: () =>
      payoutService.markPaid(payout.id, payout.version, {
        paidDate,
        reference: reference.trim(),
        acknowledgeReversedSources: reversed.length > 0 ? acknowledged : undefined,
      }),
    onSuccess: async (saved) => {
      toast.success(`${saved.payoutNumber ?? "Payout"} marked as paid.`);
      await invalidate();
      onClose();
    },
    onError: (e) => handleError(e, setErrors, onClose, invalidate),
  });
  const confirm = () => {
    const next: FieldErrors = {};
    if (!paidDate) next.paidDate = "Enter the date the transfer went through.";
    else if (paidDate > today) next.paidDate = "The date can't be in the future.";
    if (!reference.trim()) next.reference = "Enter the bank's transaction reference.";
    if (reversed.length > 0 && !acknowledged) next.acknowledge = "Confirm you've seen the reversed receipts.";
    if (Object.keys(next).length > 0) return setErrors(next);
    run.mutate();
  };
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => !next && !run.isPending && onClose()}
      title={`Mark ${NUMBER(payout)} as paid?`}
      footer={
        <>
          <Button onClick={onClose} disabled={run.isPending}>
            Back
          </Button>
          <Button variant="primary" onClick={confirm} loading={run.isPending} loadingText="Saving…">
            Mark as paid
          </Button>
        </>
      }
    >
      <div className="space-y-4 text-sm">
        <p>
          Records that <strong className="font-mono">{payout.netAmount !== null ? formatCurrency(payout.netAmount) : "—"}</strong> reached {payout.partner.name}. Paid payouts
          never change; a receipt reversed later is deducted from the partner&apos;s next payout.
        </p>
        {errors.form ? <Alert tone="danger">{errors.form}</Alert> : null}
        {reversed.length > 0 ? (
          <div className="rounded-md border border-warning/40 bg-warning-bg p-3">
            <p className="font-medium text-warning">Receipts reversed during processing: {reversed.join(", ")}</p>
            <p className="mt-1 text-xs text-muted-foreground">
              If the transfer was already made, mark it paid; a deduction for each reversed receipt is carried to the partner&apos;s next payout.
            </p>
            <div className="mt-2 flex items-start gap-2">
              <Checkbox
                id={ackId}
                className="mt-0.5"
                checked={acknowledged}
                aria-invalid={errors.acknowledge ? true : undefined}
                onCheckedChange={(v) => {
                  setAcknowledged(v === true);
                  setErrors({});
                }}
              />
              <label htmlFor={ackId}>The transfer was made in full; carry the deductions forward.</label>
            </div>
            {errors.acknowledge ? <p className="mt-1 text-xs text-danger">{errors.acknowledge}</p> : null}
          </div>
        ) : null}
        <FormField label="Paid on" required error={errors.paidDate} hint={`Between the approval date and today, within ${payout.fyLabel}.`}>
          <DateInput
            value={paidDate}
            max={today}
            onChange={(e) => {
              setPaidDate(e.target.value);
              setErrors({});
            }}
          />
        </FormField>
        <FormField label="Bank reference" required error={errors.reference} hint="UTR or transaction number from the bank statement.">
          <Input
            value={reference}
            maxLength={100}
            autoComplete="off"
            onChange={(e) => {
              setReference(e.target.value);
              setErrors({});
            }}
          />
        </FormField>
      </div>
    </Dialog>
  );
}

/** Replaces the calculated TDS of an open payout with a reason (for example a lower-deduction certificate). */
export function TdsAdjustmentDialog({ payout, open, onClose }: { payout: Payout; open: boolean; onClose: () => void }) {
  const invalidate = useInvalidate(payout);
  const [amount, setAmount] = useState(payout.tds.tdsAmount !== null ? String(payout.tds.tdsAmount) : "");
  const [reason, setReason] = useState("");
  const [errors, setErrors] = useState<FieldErrors>({});
  const run = useMutation({
    mutationFn: () => payoutService.adjustTds(payout.id, payout.version, amount.trim(), reason.trim()),
    onSuccess: async (saved) => {
      toast.success(`TDS of ${saved.payoutNumber ?? "the payout"} changed to ${saved.tds.tdsAmount !== null ? formatCurrency(saved.tds.tdsAmount) : "—"}.`);
      await invalidate();
      onClose();
    },
    onError: (e) => handleError(e, setErrors, onClose, invalidate),
  });
  const confirm = () => {
    const next: FieldErrors = {};
    if (!amount.trim()) next.tdsAmount = "Enter the TDS amount.";
    else if (Number(amount) >= Number(payout.commissionTotal)) next.tdsAmount = `TDS must be less than the commission (${formatCurrency(payout.commissionTotal)}).`;
    if (!reason.trim()) next.reason = "Give the reason for changing the calculated TDS.";
    else if (reason.trim().length > 500) next.reason = "Use at most 500 characters.";
    if (Object.keys(next).length > 0) return setErrors(next);
    run.mutate();
  };
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => !next && !run.isPending && onClose()}
      title={`Change the TDS of ${NUMBER(payout)}`}
      footer={
        <>
          <Button onClick={onClose} disabled={run.isPending}>
            Back
          </Button>
          <Button variant="primary" onClick={confirm} loading={run.isPending} loadingText="Saving…">
            Save TDS
          </Button>
        </>
      }
    >
      <div className="space-y-4 text-sm">
        <p>
          Calculated from the confirmed settings: <strong className="font-mono">{payout.tds.computedTds !== null ? formatCurrency(payout.tds.computedTds) : "—"}</strong>. The
          change and its reason are kept in the calculation history and checked by the approver. Recalculating discards it.
        </p>
        {errors.form ? <Alert tone="danger">{errors.form}</Alert> : null}
        <FormField label="TDS to deduct" required error={errors.tdsAmount}>
          <CurrencyInput
            value={amount}
            onValueChange={(v) => {
              setAmount(v);
              setErrors({});
            }}
          />
        </FormField>
        <FormField label="Reason" required error={errors.reason} hint="For example: lower-deduction certificate number and validity. At most 500 characters.">
          <Textarea
            rows={3}
            value={reason}
            onChange={(e) => {
              setReason(e.target.value);
              setErrors({});
            }}
          />
        </FormField>
      </div>
    </Dialog>
  );
}

/** Field errors stay in the dialog; a refusal (stale version, wrong state, blocked) closes it and refreshes the payout. */
function handleError(e: unknown, setErrors: (errors: FieldErrors) => void, onClose: () => void, invalidate: () => Promise<unknown>) {
  if (e instanceof ApiRequestError && e.fieldErrors.length > 0) {
    setErrors(Object.fromEntries(e.fieldErrors.map((f) => [f.field === "acknowledgeReversedSources" ? "acknowledge" : f.field, f.message])));
    return;
  }
  if (e instanceof ApiRequestError && (e.code === "PAYOUT_FY_MISMATCH" || e.code === "PAYOUT_BLOCKED")) {
    setErrors({ form: e.message });
    return;
  }
  toast.error(errorMessage(e));
  onClose();
  void invalidate();
}

