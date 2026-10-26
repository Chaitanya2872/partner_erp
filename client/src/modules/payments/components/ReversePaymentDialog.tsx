import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { FormField } from "@/components/forms/FormField";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { Textarea } from "@/components/ui/Input";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { ApiRequestError } from "@/services/api";
import { formatCurrency } from "@/utils/format";
import { paymentService } from "../services/paymentService";
import type { Payment } from "../types/payment.types";

/**
 * Reverses a receipt (Payments reverse) with a written reason. The receipt stays on record; the server adds a reversal
 * entry dated today that restores the invoice balance. A receipt is reversed once and a reversal can't be reversed.
 */
export function ReversePaymentDialog({ payment, open, onClose }: { payment: Payment; open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const close = () => {
    setReason("");
    setError(null);
    onClose();
  };
  const reverse = useMutation({
    mutationFn: () => paymentService.reverse(payment.id, reason.trim()),
    onSuccess: async (saved) => {
      toast.success(`${payment.paymentNumber ?? "Payment"} reversed by ${saved.reversal?.paymentNumber ?? "a reversal entry"}.`);
      await Promise.all([queryClient.invalidateQueries({ queryKey: queryKeys.payments.all }), queryClient.invalidateQueries({ queryKey: queryKeys.invoices.all })]);
      close();
    },
    onError: (e) => {
      const field = e instanceof ApiRequestError ? e.fieldErrors.find((f) => f.field === "reason") : undefined;
      if (field) return setError(field.message);
      toast.error(errorMessage(e));
      close();
      void queryClient.invalidateQueries({ queryKey: queryKeys.payments.all });
    },
  });
  const confirm = () => {
    const value = reason.trim();
    if (!value) return setError("Give the reason for reversing this payment.");
    if (value.length > 500) return setError("Use at most 500 characters.");
    reverse.mutate();
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => !next && !reverse.isPending && close()}
      title={`Reverse ${payment.paymentNumber ?? "this payment"}?`}
      footer={
        <>
          <Button onClick={close} disabled={reverse.isPending}>
            Back
          </Button>
          <Button variant="destructive" onClick={confirm} loading={reverse.isPending} loadingText="Reversing…">
            Reverse payment
          </Button>
        </>
      }
    >
      <div className="space-y-3 text-sm [&_ul]:list-disc [&_ul]:space-y-0.5 [&_ul]:pl-5">
        <ul>
          <li>
            The receipt of <strong className="font-mono">{formatCurrency(payment.amount)}</strong> stays on record, marked reversed.
          </li>
          <li>A reversal entry dated today for −{formatCurrency(payment.amount)} is added with your reason.</li>
          <li>
            The balance of {payment.invoice.invoiceNumber} goes back up to{" "}
            <strong className="font-mono">{formatCurrency(Number(payment.invoice.balance) + Number(payment.amount))}</strong>. The invoice itself doesn&apos;t change.
          </li>
          <li>
            Partner commission: if it was already paid out, a deduction is carried into the partner&apos;s next payout; a payout not yet paid that includes this
            receipt must be recalculated or cancelled.
          </li>
          <li>This can&apos;t be undone. If the payment was right after all, record it again.</li>
        </ul>
        <FormField label="Reason for reversing" error={error ?? undefined} required hint="For example: cheque bounced, recorded against the wrong invoice. At most 500 characters.">
          <Textarea
            rows={3}
            value={reason}
            onChange={(e) => {
              setReason(e.target.value);
              setError(null);
            }}
          />
        </FormField>
      </div>
    </Dialog>
  );
}
