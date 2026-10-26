import { useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useDebounce } from "@/hooks/useDebounce";
import { FormError, FormField, FormGrid } from "@/components/forms/FormField";
import { CurrencyInput, DateInput } from "@/components/forms/inputs";
import { SearchableSelect, type SelectOption } from "@/components/forms/SearchableSelect";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { Input, Select, Textarea } from "@/components/ui/Input";
import { applyServerErrors, compareDecimal, decimalString } from "@/lib/validation";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { ApiRequestError } from "@/services/api";
import { businessToday, formatCurrency, formatDate } from "@/utils/format";
import { paymentService } from "../services/paymentService";
import { usePaymentInvoiceOptions, usePaymentMeta } from "../hooks/usePayments";
import type { Payment, PaymentMethod } from "../types/payment.types";

/** The invoice a receipt is recorded against, as far as the form needs it. */
export interface PayableInvoice {
  id: string;
  invoiceNumber: string;
  invoiceDate: string;
  billingName: string | null;
  partnerName: string;
  /** Outstanding balance incl. GST, as a decimal string. */
  balance: string;
}

const FIELDS = ["invoiceId", "paymentDate", "amount", "method", "reference", "notes"] as const;

interface RecordPaymentDialogProps {
  open: boolean;
  onClose: () => void;
  /** Fixed invoice (from the invoice page); without it the form offers a picker of issued invoices with a balance. */
  invoice?: PayableInvoice;
  onRecorded?: (payment: Payment) => void;
}

/**
 * Records a receipt against an issued invoice (Payments write). Amounts include GST and can't exceed the balance;
 * the server computes the ex-GST part, checks the balance again under a lock and refuses duplicates. The form carries
 * an idempotency key, so a double submission records one entry.
 */
export function RecordPaymentDialog({ open, onClose, invoice, onRecorded }: RecordPaymentDialogProps) {
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => !next && onClose()}
      title="Record payment"
      description={invoice ? `${invoice.invoiceNumber} · ${invoice.billingName ?? invoice.partnerName}` : "Money received against an issued invoice."}
    >
      {open ? <RecordPaymentForm invoice={invoice} onClose={onClose} onRecorded={onRecorded} /> : null}
    </Dialog>
  );
}

function RecordPaymentForm({ invoice, onClose, onRecorded }: Omit<RecordPaymentDialogProps, "open">) {
  const queryClient = useQueryClient();
  const meta = usePaymentMeta();
  // One key per opened form: resubmitting it returns the entry already recorded instead of a second one.
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const [formError, setFormError] = useState<string | null>(null);
  const [duplicate, setDuplicate] = useState<{ message: string; values: string } | null>(null);
  const [search, setSearch] = useState("");
  const [chosen, setChosen] = useState<PayableInvoice | undefined>(invoice);
  const options = usePaymentInvoiceOptions(useDebounce(search.trim()), !invoice);
  const today = businessToday();
  const referenceOptional = new Set<PaymentMethod>(meta.data?.referenceOptional ?? ["CASH"]);

  const schema = z
    .object({
      invoiceId: z.string().min(1, "Choose the invoice."),
      paymentDate: z
        .string()
        .min(1, "Enter the date the payment was received.")
        .refine((v) => v <= today, "The payment date can't be in the future.")
        .refine((v) => !chosen || v >= chosen.invoiceDate, `The payment date can't be before the invoice date${chosen ? ` (${formatDate(chosen.invoiceDate)})` : ""}.`),
      amount: decimalString({ label: "Amount", min: "0.01" }).refine(
        (v) => !chosen || v.length === 0 || compareDecimal(v, chosen.balance) <= 0,
        `The amount can't be more than the balance${chosen ? ` of ${formatCurrency(chosen.balance)}` : ""}.`,
      ),
      method: z.string().min(1, "Choose how the payment was received."),
      reference: z.string().trim().max(100, "Use at most 100 characters."),
      notes: z.string().trim().max(500, "Use at most 500 characters."),
    })
    .refine((v) => referenceOptional.has(v.method as PaymentMethod) || v.reference.length > 0, {
      path: ["reference"],
      message: "Enter the reference (UTR, cheque number or transaction id).",
    });
  type Values = z.input<typeof schema>;

  const {
    control,
    register,
    handleSubmit,
    setError,
    setValue,
    getValues,
    formState: { errors, isSubmitting },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { invoiceId: invoice?.id ?? "", paymentDate: today, amount: invoice?.balance ?? "", method: "", reference: "", notes: "" },
  });
  const values = useWatch({ control });
  const method = values.method as PaymentMethod | "";
  const snapshot = JSON.stringify({ ...values, notes: undefined });
  const duplicateWarning = duplicate && duplicate.values === snapshot ? duplicate.message : null;

  const choices: PayableInvoice[] = (options.data ?? []).map((o) => ({
    id: o.id,
    invoiceNumber: o.invoiceNumber,
    invoiceDate: o.invoiceDate,
    billingName: o.billingName,
    partnerName: o.partner.name,
    balance: String(o.balance),
  }));
  const listed = chosen && !choices.some((c) => c.id === chosen.id) ? [chosen, ...choices] : choices;
  const invoiceOptions: SelectOption[] = listed.map((c) => ({
    value: c.id,
    label: `${c.invoiceNumber} · ${c.billingName ?? c.partnerName}`,
    description: `${c.partnerName} · balance ${formatCurrency(c.balance)}`,
  }));

  const submit = handleSubmit(async (v) => {
    setFormError(null);
    try {
      const saved = await paymentService.record({
        invoiceId: v.invoiceId,
        paymentDate: v.paymentDate,
        amount: v.amount.trim(),
        method: v.method as PaymentMethod,
        reference: v.reference.trim() || undefined,
        notes: v.notes.trim() || undefined,
        idempotencyKey,
        confirmDuplicate: duplicateWarning ? true : undefined,
      });
      toast.success(`Recorded ${saved.paymentNumber ?? "the payment"}. Balance ${formatCurrency(saved.invoice.balance)}.`);
      await Promise.all([queryClient.invalidateQueries({ queryKey: queryKeys.payments.all }), queryClient.invalidateQueries({ queryKey: queryKeys.invoices.all })]);
      onRecorded?.(saved);
      onClose();
    } catch (error) {
      if (error instanceof ApiRequestError && error.code === "POSSIBLE_DUPLICATE") {
        setDuplicate({ message: error.message, values: JSON.stringify({ ...getValues(), notes: undefined }) });
        return;
      }
      const unmatched = applyServerErrors(error, setError, FIELDS);
      if (!(error instanceof ApiRequestError) || error.fieldErrors.length === 0) setFormError(errorMessage(error));
      else if (unmatched.length > 0) setFormError(unmatched.join(" "));
    }
  });

  return (
    <form onSubmit={submit} noValidate className="space-y-4" aria-label="Record payment">
      <FormError>{formError}</FormError>
      <FormGrid>
        {invoice ? null : (
          <Controller
            control={control}
            name="invoiceId"
            render={({ field }) => (
              <FormField
                label="Invoice"
                error={errors.invoiceId?.message}
                required
                wide
                hint={chosen ? `Balance ${formatCurrency(chosen.balance)} · issued ${formatDate(chosen.invoiceDate)}` : "Issued invoices with an outstanding balance."}
              >
                <SearchableSelect
                  ref={field.ref}
                  options={invoiceOptions}
                  value={field.value}
                  onChange={(id) => {
                    const next = listed.find((c) => c.id === id);
                    setChosen(next);
                    field.onChange(id);
                    if (next) setValue("amount", next.balance, { shouldValidate: false });
                  }}
                  onQueryChange={setSearch}
                  loading={options.isPending}
                  placeholder="Choose an invoice…"
                  searchPlaceholder="Search by invoice, customer or partner"
                  emptyText="No issued invoices with a balance match"
                />
              </FormField>
            )}
          />
        )}
        <FormField label="Received on" error={errors.paymentDate?.message} required>
          <DateInput max={today} min={chosen?.invoiceDate} {...register("paymentDate")} />
        </FormField>
        <Controller
          control={control}
          name="amount"
          render={({ field }) => (
            <FormField label="Amount received (incl. GST)" error={errors.amount?.message} required hint={chosen ? `At most ${formatCurrency(chosen.balance)}.` : undefined}>
              <CurrencyInput ref={field.ref} value={field.value} onValueChange={field.onChange} onBlur={field.onBlur} />
            </FormField>
          )}
        />
        <FormField label="Method" error={errors.method?.message} required>
          <Select {...register("method")}>
            <option value="">Choose…</option>
            {meta.data?.methods.map((m) => (
              <option key={m.value} value={m.value}>
                {m.label}
              </option>
            ))}
          </Select>
        </FormField>
        <FormField
          label="Reference"
          error={errors.reference?.message}
          required={method !== "" && !referenceOptional.has(method)}
          hint={method === "CARD" ? "The approval code or last four digits. Never the full card number." : "UTR, cheque number or transaction id."}
        >
          <Input autoComplete="off" maxLength={100} {...register("reference")} />
        </FormField>
        <FormField label="Notes" error={errors.notes?.message} wide hint="Optional. At most 500 characters.">
          <Textarea rows={2} {...register("notes")} />
        </FormField>
      </FormGrid>
      {duplicateWarning ? (
        <Alert tone="warning" title="Possible duplicate">
          {duplicateWarning} Record it only if it is a separate payment.
        </Alert>
      ) : null}
      <p className="text-xs text-muted-foreground">
        Payments can&apos;t be edited or deleted. A mistaken payment is reversed with a reason, and the original stays on record. Refunds and overpayments aren&apos;t
        supported.
      </p>
      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button onClick={onClose} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" variant="primary" loading={isSubmitting} loadingText="Recording…">
          {duplicateWarning ? "Record anyway" : "Record payment"}
        </Button>
      </div>
    </form>
  );
}
