import { useEffect, useMemo, useRef, useState } from "react";
import { Controller, useFieldArray, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Alert } from "@/components/ui/Alert";
import { Input, Select, Textarea } from "@/components/ui/Input";
import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";
import { ErrorSummary, FormActions, FormError, FormField, FormGrid, FormSection, type SummaryItem } from "@/components/forms/FormField";
import { DateInput, PercentageInput } from "@/components/forms/inputs";
import { SearchableSelect, type SelectOption } from "@/components/forms/SearchableSelect";
import { SearchInput } from "@/components/forms/SearchInput";
import { UnsavedChangesGuard } from "@/components/forms/UnsavedChangesGuard";
import { useDebounce } from "@/hooks/useDebounce";
import { decimalString } from "@/lib/validation";
import { errorMessage } from "@/lib/errors";
import { ApiRequestError } from "@/services/api";
import { sanitizeDecimal } from "@/utils/decimal";
import { businessToday, formatCurrency, formatPercent } from "@/utils/format";
import { useEstimatePreview, useLeadOptions, useProductOptions } from "../hooks/useEstimates";
import { EstimateTotals } from "./EstimateTotals";
import type { BillTo, Estimate, EstimateInput, EstimateMeta, ProductOption } from "../types/estimate.types";

/** A lead the estimate can be for, from the restricted lead picker (or the estimate's own lead). */
export interface LeadChoice {
  id: string;
  leadNumber: string;
  clientName: string;
  partnerName: string;
}

/* Mirrors EstimateRules on the server for quick feedback; the server validates again and computes every total. */
const QUANTITY = /^\d{1,9}(\.\d{1,3})?$/;
const lineSchema = z.object({
  key: z.string(),
  id: z.string().optional(),
  productId: z.string().min(1),
  sku: z.string(),
  description: z.string(),
  unit: z.string(),
  b2cName: z.string().optional(),
  quantity: z
    .string()
    .trim()
    .refine((v) => QUANTITY.test(v), "Enter a quantity with up to 3 decimals.")
    .refine((v) => !QUANTITY.test(v) || Number(v) > 0, "Quantity must be more than 0."),
  unitPrice: decimalString({ label: "Rate", max: "9999999999.99" }),
  gstPercent: decimalString({ label: "GST", scale: 3, max: "100" }),
});

const makeSchema = (maxLines: number) =>
  z.object({
    leadId: z.string().min(1, "Choose the lead this estimate is for."),
    estimateDate: z.string().min(1, "Estimate date is required."),
    validUntil: z.string().min(1, "Valid until is required."),
    discountPercent: decimalString({ required: false, label: "Discount", scale: 3, max: "100" }),
    // D-21: the tax type is chosen, never assumed, for a new estimate.
    igst: z.string().refine((v) => v === "true" || v === "false", "Choose CGST + SGST (same state) or IGST (other state)."),
    billTo: z.enum(["CLIENT", "PARTNER"]),
    feePercent: decimalString({ required: false, label: "Fee", scale: 3, max: "100" }),
    feeOverrideReason: z.string().trim().max(500, "Use at most 500 characters."),
    notes: z.string().max(5000, "Use at most 5000 characters."),
    lines: z
      .array(lineSchema)
      .min(1, "Add at least one item.")
      .max(maxLines, `An estimate can have at most ${maxLines} items.`),
  })
  .refine((v) => v.validUntil >= v.estimateDate, { path: ["validUntil"], message: "Valid until can't be before the estimate date." });

type FormValues = z.input<ReturnType<typeof makeSchema>>;
type LineValues = FormValues["lines"][number];

const FIELDS = ["leadId", "estimateDate", "validUntil", "discountPercent", "igst", "billTo", "feePercent", "feeOverrideReason", "notes", "lines"] as const;
const LABELS: Record<string, string> = {
  leadId: "Lead", estimateDate: "Estimate date", validUntil: "Valid until", discountPercent: "Discount", igst: "Tax",
  billTo: "Bill to", feePercent: "Fee rate", feeOverrideReason: "Reason for the fee", notes: "Notes and terms", lines: "Items",
};

const addDays = (iso: string, days: number) => {
  const d = new Date(`${iso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
};

const same = (a: string | number | null | undefined, b: string | number | null | undefined) =>
  a !== null && a !== undefined && b !== null && b !== undefined && a !== "" && b !== "" && Number(a) === Number(b);

let keySeq = 0;
const nextKey = () => `new-${++keySeq}`;

interface EstimateFormProps {
  meta: EstimateMeta;
  estimate?: Estimate;
  /** The lead from a "New estimate" link, already checked as eligible by the server. */
  initialLead?: LeadChoice;
  /** PRODUCTS:VIEW_INTERNAL_B2C: the preview may carry the B2C comparison (the server decides). */
  internal: boolean;
  onSubmit: (input: EstimateInput) => Promise<void>;
  onCancel: () => void;
}

/**
 * Estimate editor (§6, BR-E1–E5). Lines copy the product when added; saved lines keep their snapshot, so later
 * product price changes never alter an existing estimate. Totals come from POST /estimates/preview.
 */
export function EstimateForm({ meta, estimate, initialLead, internal, onSubmit, onCancel }: EstimateFormProps) {
  const editing = !!estimate;
  /** Sent, accepted and rejected estimates are saved only after a warning (Phase 8 decision 3); invoiced ones never. */
  const warnOnSave = editing && estimate.status !== "DRAFT";
  const [pendingSave, setPendingSave] = useState<EstimateInput | null>(null);
  const [saving, setSaving] = useState(false);
  const today = businessToday();
  const schema = useMemo(() => makeSchema(meta.maxLines), [meta.maxLines]);
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    setFocus,
    setValue,
    getFieldState,
    control,
    formState: { errors, isSubmitting, isDirty, isSubmitSuccessful },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      leadId: estimate?.lead.id ?? initialLead?.id ?? "",
      estimateDate: estimate?.estimateDate ?? today,
      validUntil: estimate?.validUntil ?? addDays(today, meta.validityDays),
      discountPercent: estimate ? String(estimate.discountPercent) : "",
      igst: estimate ? String(estimate.igst) : "",
      billTo: estimate?.billTo ?? "CLIENT",
      feePercent: estimate ? String(estimate.feePercent) : "",
      feeOverrideReason: "",
      notes: estimate ? (estimate.notes ?? "") : meta.terms,
      lines:
        estimate?.lines.map((l) => ({
          key: l.id,
          id: l.id,
          productId: l.productId ?? "",
          sku: l.sku ?? "",
          description: l.description,
          unit: l.unit ?? "",
          quantity: String(l.quantity),
          unitPrice: String(l.unitPrice),
          gstPercent: String(l.gstPercent),
        })) ?? [],
    },
  });
  const lines = useFieldArray({ control, name: "lines", keyName: "fieldKey" });
  const values = useWatch({ control }) as FormValues;

  // A new estimate's validity follows its date until the user sets it (BR-E2: date + validity days).
  const estimateDate = values.estimateDate;
  useEffect(() => {
    if (!editing && estimateDate && !getFieldState("validUntil").isDirty) {
      setValue("validUntil", addDays(estimateDate, meta.validityDays));
    }
  }, [editing, estimateDate, getFieldState, meta.validityDays, setValue]);

  const partnerBuys = values.billTo === "PARTNER";
  const sameDeal = editing && values.leadId === estimate.lead.id && values.billTo === estimate.billTo;
  /** Null keeps the stored fee (same lead and buyer) or applies the default; partner purchases carry no fee. */
  const feeToSend = (fee: string) => {
    if (!meta.canOverrideFee || partnerBuys || fee.trim() === "") return null;
    if (sameDeal && same(fee, estimate.feePercent)) return null;
    return fee.trim();
  };

  const toInput = (v: FormValues): EstimateInput => ({
    leadId: v.leadId,
    estimateDate: v.estimateDate || null,
    validUntil: v.validUntil || null,
    discountPercent: v.discountPercent?.trim() ? v.discountPercent.trim() : null,
    igst: v.igst === "true",
    billTo: v.billTo as BillTo,
    feePercent: feeToSend(v.feePercent ?? ""),
    feeOverrideReason: v.feeOverrideReason?.trim() ? v.feeOverrideReason.trim() : null,
    notes: v.notes ?? "",
    lines: (v.lines ?? []).map((l) => ({
      id: l.id,
      productId: l.productId,
      quantity: l.quantity.trim(),
      unitPrice: l.unitPrice.trim(),
      gstPercent: l.gstPercent.trim(),
    })),
    version: estimate?.version,
  });

  // Live totals: debounced, and only for input the server would accept.
  const previewable =
    !!values.leadId &&
    (values.lines?.length ?? 0) > 0 &&
    values.lines.every((l) => QUANTITY.test(l.quantity.trim()) && Number(l.quantity) > 0 && /^\d+(\.\d{1,2})?$/.test(l.unitPrice.trim()) && /^\d+(\.\d{1,3})?$/.test(l.gstPercent.trim()) && Number(l.gstPercent) <= 100);
  const previewKey = useDebounce(previewable ? JSON.stringify({ ...toInput(values), feeOverrideReason: null }) : null, 400);
  const preview = useEstimatePreview(previewKey ? (JSON.parse(previewKey) as EstimateInput) : null, estimate?.id);
  const shown = previewable ? preview.data : undefined;

  const feeInput = values.feePercent ?? "";
  const defaultFee = shown?.defaultFeePercent;
  const overriding =
    meta.canOverrideFee && !partnerBuys && feeInput.trim() !== "" && defaultFee !== undefined && !same(feeInput, defaultFee) && !(sameDeal && same(feeInput, estimate.feePercent));

  const save = async (input: EstimateInput) => {
    setSaving(true);
    try {
      await onSubmit(input);
    } catch (error) {
      setSaving(false);
      const unmatched = applyEstimateErrors(error, setError);
      const hasFieldErrors = error instanceof ApiRequestError && error.fieldErrors.length > 0;
      if (!hasFieldErrors) setFormError(errorMessage(error));
      else if (unmatched.length > 0) setFormError(unmatched.join(" "));
    }
  };

  const submit = handleSubmit(async (v) => {
    setFormError(null);
    if (overriding && !v.feeOverrideReason?.trim()) {
      setError("feeOverrideReason", { type: "manual", message: "Give a reason for using a fee other than the default." }, { shouldFocus: true });
      return;
    }
    // The warning is a courtesy; the server decides what may be edited.
    if (warnOnSave) setPendingSave(toInput(v));
    else await save(toInput(v));
  });

  const addProduct = (p: ProductOption) => {
    lines.append({
      key: nextKey(),
      productId: p.id,
      sku: p.sku,
      description: p.name,
      unit: p.unit ?? "",
      b2cName: p.b2cName,
      quantity: "1",
      unitPrice: String(p.rate),
      gstPercent: String(p.gstPercent),
    });
  };

  const summary: SummaryItem[] = [
    ...Object.entries(errors)
      .filter(([name, e]) => name !== "lines" && e?.message)
      .map(([name, e]) => ({ name, label: LABELS[name] ?? name, message: String(e!.message) })),
    ...(errors.lines?.message ? [{ name: "lines", label: "Items", message: errors.lines.message }] : []),
    ...(errors.lines?.root?.message ? [{ name: "lines", label: "Items", message: errors.lines.root.message }] : []),
  ];
  const lineErrors = Array.isArray(errors.lines) ? errors.lines : [];
  const lineErrorCount = lineErrors.filter(Boolean).length;

  const [leadSearch, setLeadSearch] = useState("");
  const leadResults = useLeadOptions(useDebounce(leadSearch.trim()));
  const results: LeadChoice[] = (leadResults.data ?? []).map((l) => ({ id: l.id, leadNumber: l.leadNumber, clientName: l.clientName, partnerName: l.partner.name }));
  // The chosen lead keeps its label when a later search no longer returns it.
  const [chosenLead, setChosenLead] = useState<LeadChoice | undefined>(() =>
    estimate ? { id: estimate.lead.id, leadNumber: estimate.lead.leadNumber, clientName: estimate.lead.clientName, partnerName: estimate.partner.name } : initialLead,
  );
  const selectedLead = chosenLead?.id === values.leadId ? chosenLead : results.find((l) => l.id === values.leadId);
  const leadChoices = selectedLead && !results.some((l) => l.id === selectedLead.id) ? [selectedLead, ...results] : results;
  const leadOptions: SelectOption[] = leadChoices.map((l) => ({ value: l.id, label: `${l.clientName} · ${l.leadNumber}`, description: l.partnerName }));
  const linesError = errors.lines?.message ?? errors.lines?.root?.message;

  return (
    <form onSubmit={submit} noValidate className="space-y-6" aria-label={editing ? `Edit ${estimate.estimateNumber}` : "New estimate"}>
      <UnsavedChangesGuard when={isDirty && !isSubmitting && !saving && !isSubmitSuccessful} />
      {warnOnSave ? (
        <Alert tone="warning" title={`This estimate is ${estimate.statusLabel.toLowerCase()}`}>
          Saving changes keeps its status ({estimate.statusLabel.toLowerCase()}) and is recorded in its history. Make sure the client or partner gets the revised copy.
        </Alert>
      ) : null}
      <FormError>{formError}</FormError>
      {summary.length + lineErrorCount > 1 ? (
        <ErrorSummary
          items={[...summary, ...(lineErrorCount > 0 ? [{ name: "lines", label: "Items", message: `${lineErrorCount} item(s) need attention.` }] : [])]}
          onSelect={(name) => name !== "lines" && setFocus(name as keyof FormValues)}
        />
      ) : null}

      <FormSection title="Lead" description="The partner comes from the lead. Lost leads can't get new estimates.">
        <FormGrid>
          <Controller
            control={control}
            name="leadId"
            render={({ field }) => (
              <FormField
                label="Lead"
                error={errors.leadId?.message}
                required
                wide
                hint={selectedLead ? `Referred by ${selectedLead.partnerName}.` : "Search by client, lead number or partner. Lost leads aren't listed."}
              >
                <SearchableSelect
                  ref={field.ref}
                  options={leadOptions}
                  value={field.value}
                  onChange={(id) => {
                    setChosenLead(results.find((l) => l.id === id) ?? chosenLead);
                    field.onChange(id);
                  }}
                  onQueryChange={setLeadSearch}
                  loading={leadResults.isPending && !selectedLead}
                  placeholder="Choose a lead…"
                  searchPlaceholder="Search leads"
                  emptyText="No open leads match"
                />
              </FormField>
            )}
          />
          <FormField label="Bill to" error={errors.billTo?.message} hint={partnerBuys ? "Bulk purchase: the partner is billed and earns no fee." : undefined}>
            <Select {...register("billTo")}>
              {meta.billTo.map((b) => (
                <option key={b.value} value={b.value}>
                  {b.label}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField label="Tax" error={errors.igst?.message} required>
            <Select {...register("igst")}>
              {editing ? null : <option value="">Choose the tax type…</option>}
              <option value="false">CGST + SGST (same state)</option>
              <option value="true">IGST (other state)</option>
            </Select>
          </FormField>
          <FormField label="Estimate date" error={errors.estimateDate?.message} required>
            <DateInput {...register("estimateDate")} />
          </FormField>
          <FormField label="Valid until" error={errors.validUntil?.message} required hint={editing ? undefined : `${meta.validityDays} days from the estimate date unless changed.`}>
            <DateInput {...register("validUntil")} />
          </FormField>
        </FormGrid>
      </FormSection>

      <FormSection title="Items" description="Prices are copied from the product sheet when an item is added and can be changed here. Saved items keep their copy.">
        {linesError ? (
          <p role="alert" className="text-sm text-danger">
            {linesError}
          </p>
        ) : null}
        {lines.fields.length > 0 ? (
          <div className="relative overflow-x-auto rounded-lg border border-border">
            <table className="w-full min-w-[640px] text-sm">
              <caption className="sr-only">Estimate items</caption>
              <thead className="bg-surface-sunken text-left text-xs text-muted-foreground">
                <tr>
                  <th scope="col" className="px-3 py-2 font-medium">Item</th>
                  <th scope="col" className="w-28 px-3 py-2 font-medium">Qty</th>
                  <th scope="col" className="w-36 px-3 py-2 font-medium">Rate (₹)</th>
                  <th scope="col" className="w-28 px-3 py-2 font-medium">GST %</th>
                  <th scope="col" className="w-32 px-3 py-2 text-right font-medium">Amount</th>
                  <th scope="col" className="w-12 px-3 py-2"><span className="sr-only">Remove</span></th>
                </tr>
              </thead>
              <tbody>
                {lines.fields.map((line, i) => (
                  <LineRow
                    key={line.fieldKey}
                    index={i}
                    line={values.lines?.[i] ?? line}
                    amount={shown?.lines[i]?.amount}
                    b2c={internal ? shown?.internal?.lines[i] : undefined}
                    errors={lineErrors[i]}
                    control={control}
                    onRemove={() => lines.remove(i)}
                  />
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="rounded-lg border border-dashed border-border px-4 py-6 text-center text-sm text-muted-foreground">No items yet. Search the product sheet below to add one.</p>
        )}
        {lines.fields.length < meta.maxLines ? <ProductPicker onAdd={addProduct} internal={internal} /> : null}
      </FormSection>

      <FormSection title="Pricing" description="Totals are calculated by the server: line amounts, then the discount, then GST per line rate.">
        <div className="grid gap-6 lg:grid-cols-2">
          <FormGrid className="content-start">
            <Controller
              control={control}
              name="discountPercent"
              render={({ field }) => (
                <FormField label="Discount" error={errors.discountPercent?.message} hint="On the item total, before GST.">
                  <PercentageInput scale={3} value={field.value ?? ""} onValueChange={field.onChange} onBlur={field.onBlur} ref={field.ref} placeholder="0" />
                </FormField>
              )}
            />
            {meta.canOverrideFee && !partnerBuys ? (
              <Controller
                control={control}
                name="feePercent"
                render={({ field }) => (
                  <FormField
                    label="Fee rate"
                    error={errors.feePercent?.message}
                    hint={defaultFee !== undefined ? `Default ${formatPercent(defaultFee)}. Leave blank to use it.` : "Leave blank to use the default."}
                  >
                    <PercentageInput scale={3} value={field.value ?? ""} onValueChange={field.onChange} onBlur={field.onBlur} ref={field.ref} />
                  </FormField>
                )}
              />
            ) : (
              <FormField label="Fee rate" hint={partnerBuys ? "No fee on partner purchases." : "Set from the lead or the partner's tier. Changing it needs Partners approve."}>
                <Input readOnly value={partnerBuys ? formatPercent(0) : shown ? formatPercent(shown.feePercent) : editing ? formatPercent(estimate.feePercent) : "Calculated when items are added"} />
              </FormField>
            )}
            {overriding ? (
              <FormField label="Reason for the fee" error={errors.feeOverrideReason?.message} required wide hint="Kept in the estimate's history.">
                <Input autoComplete="off" {...register("feeOverrideReason")} />
              </FormField>
            ) : null}
            <FormField label="Notes and terms" error={errors.notes?.message} wide hint="Printed on the estimate.">
              <Textarea rows={5} {...register("notes")} />
            </FormField>
          </FormGrid>

          <div className="space-y-3" aria-live="polite">
            {shown ? (
              <EstimateTotals totals={shown.totals} discountPercent={values.discountPercent || "0"} igst={values.igst === "true"} busy={preview.isFetching} />
            ) : (
              <p className="rounded-lg border border-dashed border-border px-4 py-6 text-center text-sm text-muted-foreground">
                {preview.isError && previewable ? errorMessage(preview.error) : "Choose a lead and add items to see the totals."}
              </p>
            )}
            {internal && shown?.internal ? (
              <Alert tone={shown.internal.missingB2cPrices > 0 ? "warning" : "info"} title="Internal B2C comparison">
                Not shown on the partner copy. B2C taxable {formatCurrency(shown.internal.totals.taxableAmount)} · partner discount vs B2C list{" "}
                {formatCurrency(shown.internal.uplift)}.
                {shown.internal.missingB2cPrices > 0 ? ` ${shown.internal.missingB2cPrices} item(s) have no B2C price, so the comparison is incomplete.` : ""}
              </Alert>
            ) : null}
          </div>
        </div>
      </FormSection>

      <FormActions>
        <Button onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" variant="primary" loading={isSubmitting || saving} loadingText="Saving…">
          {editing ? "Save changes" : "Create estimate"}
        </Button>
      </FormActions>

      {warnOnSave ? (
        <ConfirmDialog
          open={pendingSave !== null}
          title={`Save changes to a ${estimate.statusLabel.toLowerCase()} estimate?`}
          confirmLabel="Save changes"
          busy={saving}
          onConfirm={() => {
            const input = pendingSave;
            setPendingSave(null);
            if (input) void save(input);
          }}
          onCancel={() => setPendingSave(null)}
        >
          <p>
            {estimate.estimateNumber} is {estimate.statusLabel.toLowerCase()}. Its status won't change, and the edit is recorded in its history.
          </p>
        </ConfirmDialog>
      ) : null}
    </form>
  );
}

interface LineRowProps {
  index: number;
  line: LineValues;
  amount: string | number | undefined;
  b2c?: { b2cName: string | null; b2cPriceMissing: boolean };
  errors?: Partial<Record<keyof LineValues, { message?: string }>>;
  control: ReturnType<typeof useForm<FormValues>>["control"];
  onRemove: () => void;
}

function LineRow({ index, line, amount, b2c, errors, control, onRemove }: LineRowProps) {
  const name = `${line.sku} ${line.description}`.trim();
  const errorId = `line-${index}-error`;
  const messages = [errors?.productId?.message, errors?.quantity?.message, errors?.unitPrice?.message, errors?.gstPercent?.message].filter(Boolean);
  const cell = (field: "quantity" | "unitPrice" | "gstPercent", label: string, scale: number) => (
    <Controller
      control={control}
      name={`lines.${index}.${field}`}
      render={({ field: f }) => (
        <Input
          ref={f.ref}
          value={f.value}
          onChange={(e) => f.onChange(sanitizeDecimal(e.target.value, scale))}
          onBlur={f.onBlur}
          inputMode="decimal"
          autoComplete="off"
          aria-label={`${label} for ${name}`}
          aria-invalid={errors?.[field] ? true : undefined}
          aria-describedby={errors?.[field] ? errorId : undefined}
          className="font-mono tabular"
        />
      )}
    />
  );
  return (
    <tr className="border-t border-border align-top">
      <td className="px-3 py-2">
        <div className="font-medium">{line.description}</div>
        <div className="text-xs text-muted-foreground">{[line.sku, line.unit].filter(Boolean).join(" · ")}</div>
        {b2c ? (
          <div className="text-xs text-muted-foreground">
            B2C: {b2c.b2cName ?? "—"}
            {b2c.b2cPriceMissing ? " · no B2C price" : ""}
          </div>
        ) : null}
        {messages.length > 0 ? (
          <p id={errorId} className="mt-1 text-xs text-danger">
            {messages.join(" ")}
          </p>
        ) : null}
      </td>
      <td className="px-3 py-2">{cell("quantity", "Quantity", 3)}</td>
      <td className="px-3 py-2">{cell("unitPrice", "Rate", 2)}</td>
      <td className="px-3 py-2">{cell("gstPercent", "GST", 3)}</td>
      <td className="px-3 py-2 text-right font-mono tabular whitespace-nowrap">{amount === undefined ? "—" : formatCurrency(amount)}</td>
      <td className="px-3 py-2 text-right">
        <Button size="icon-sm" variant="ghost" onClick={onRemove} aria-label={`Remove ${name}`}>
          <Trash2 aria-hidden="true" />
        </Button>
      </td>
    </tr>
  );
}

/** Server-side search of active products (at most 50 per search); a click adds the product as a new line. */
export function ProductPicker({ onAdd, internal }: { onAdd: (p: ProductOption) => void; internal: boolean }) {
  const [search, setSearch] = useState("");
  const [open, setOpen] = useState(false);
  const q = useDebounce(search.trim());
  const options = useProductOptions(q, open);
  const inputRef = useRef<HTMLInputElement>(null);

  if (!open) {
    return (
      <Button onClick={() => setOpen(true)}>
        <Plus aria-hidden="true" /> Add item
      </Button>
    );
  }

  return (
    <div className="space-y-2 rounded-lg border border-border p-3">
      <div className="flex items-center gap-2">
        <SearchInput
          ref={inputRef}
          autoFocus
          placeholder="Search products by name or SKU"
          aria-label="Search products"
          value={search}
          onValueChange={setSearch}
        />
        <Button onClick={() => setOpen(false)}>Done</Button>
      </div>
      {options.isError ? (
        <p className="text-sm text-danger">{errorMessage(options.error)}</p>
      ) : options.isPending ? (
        <p className="text-sm text-muted-foreground">Loading products…</p>
      ) : options.data.length === 0 ? (
        <p className="text-sm text-muted-foreground">No active products match.</p>
      ) : (
        <ul className="max-h-72 divide-y divide-border overflow-y-auto" aria-label="Matching products">
          {options.data.map((p) => (
            <li key={p.id} className="flex items-center justify-between gap-3 py-2">
              <div className="min-w-0">
                <div className="truncate text-sm font-medium">{p.name}</div>
                <div className="truncate text-xs text-muted-foreground">
                  {[p.sku, p.categoryLabel, `${formatCurrency(p.rate)} + ${formatPercent(p.gstPercent)} GST`].join(" · ")}
                  {internal && p.b2cName ? ` · B2C ${p.b2cName}${p.b2cRate !== undefined ? ` ${formatCurrency(p.b2cRate)}` : ""}` : ""}
                </div>
              </div>
              <Button
                size="sm"
                onClick={() => {
                  onAdd(p);
                  inputRef.current?.focus();
                }}
                aria-label={`Add ${p.name}`}
              >
                <Plus aria-hidden="true" /> Add
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** Server field names use `lines[0].quantity`; the form uses `lines.0.quantity`. */
function applyEstimateErrors(error: unknown, setError: ReturnType<typeof useForm<FormValues>>["setError"]): string[] {
  if (!(error instanceof ApiRequestError) || error.fieldErrors.length === 0) return [];
  const unmatched: string[] = [];
  let focused = false;
  for (const fe of error.fieldErrors) {
    const name = fe.field.replace(/\[(\d+)\]/g, ".$1");
    const known = (FIELDS as readonly string[]).includes(name) || /^lines\.\d+\.(productId|quantity|unitPrice|gstPercent)$/.test(name);
    if (known) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any -- dynamic line paths
      setError(name as any, { type: "server", message: fe.message }, { shouldFocus: !focused && !name.endsWith("productId") });
      focused = true;
    } else {
      unmatched.push(fe.message);
    }
  }
  return unmatched;
}
