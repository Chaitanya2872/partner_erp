import { useMemo, useState } from "react";
import { Controller, useFieldArray, useForm, useWatch, type Control } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Trash2 } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Alert } from "@/components/ui/Alert";
import { Input, Select, Textarea } from "@/components/ui/Input";
import { ErrorSummary, FormActions, FormError, FormField, FormGrid, FormSection, type SummaryItem } from "@/components/forms/FormField";
import { NumberInput, PercentageInput } from "@/components/forms/inputs";
import { SearchableSelect, type SelectOption } from "@/components/forms/SearchableSelect";
import { UnsavedChangesGuard } from "@/components/forms/UnsavedChangesGuard";
import { useDebounce } from "@/hooks/useDebounce";
import { decimalString } from "@/lib/validation";
import { errorMessage } from "@/lib/errors";
import { ApiRequestError } from "@/services/api";
import { sanitizeDecimal } from "@/utils/decimal";
import { formatCurrency, formatPercent } from "@/utils/format";
import { ProductPicker } from "@/modules/estimates/components/EstimateForm";
import { InstallationLines, type InstallationFormValues, type InstallationRow } from "@/modules/estimates/components/InstallationLines";
import type { BillTo, ProductOption } from "@/modules/estimates/types/estimate.types";
import { useInvoiceLeadOptions, useInvoicePreview } from "../hooks/useInvoices";
import { EstimateComparisonNotice, InvoiceLinesTable, InvoiceTotalsCard, TaxTreatmentNotice } from "./InvoiceParts";
import type { Invoice, InvoiceInput, InvoiceMeta, PlaceOfSupplyBasis } from "../types/invoice.types";

/** A lead the invoice can be for, from the restricted lead picker (or the invoice's own lead). */
export interface InvoiceLeadChoice {
  id: string;
  leadNumber: string;
  clientName: string;
  partnerName: string;
}

/* Mirrors the server's checks for quick feedback; the server validates again and computes every amount. */
const QUANTITY = /^\d{1,9}(\.\d{1,3})?$/;
const GSTIN = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9A-Z]{3}$/;
const lineSchema = z
  .object({
  key: z.string(),
  id: z.string().optional(),
  /** Installation lines are free-text priced work with no product. */
  kind: z.enum(["PRODUCT", "INSTALLATION"]),
  productId: z.string(),
  sku: z.string(),
  description: z.string(),
  unit: z.string(),
  /** The GST % the product (or the saved line) carries: changing it needs Invoices approve. */
  sourceGst: z.string(),
  quantity: z
    .string()
    .trim()
    .refine((v) => QUANTITY.test(v), "Enter a quantity with up to 3 decimals.")
    .refine((v) => !QUANTITY.test(v) || Number(v) > 0, "Quantity must be more than 0."),
  unitPrice: decimalString({ label: "Rate", max: "9999999999.99" }),
  gstPercent: decimalString({ label: "GST", scale: 3, max: "100" }),
  })
  .superRefine((l, ctx) => {
    if (l.kind === "INSTALLATION") {
      const description = l.description.trim();
      if (!description) ctx.addIssue({ code: "custom", path: ["description"], message: "Describe the installation work." });
      else if (description.length > 300) ctx.addIssue({ code: "custom", path: ["description"], message: "Use at most 300 characters." });
      if (l.unit.trim().length > 20) ctx.addIssue({ code: "custom", path: ["unit"], message: "Use at most 20 characters." });
    } else if (!l.productId) {
      ctx.addIssue({ code: "custom", path: ["productId"], message: "Choose a product." });
    }
  });

const makeSchema = (maxLines: number, locked: boolean) =>
  z.object({
    leadId: locked ? z.string() : z.string().min(1, "Choose the lead this invoice is for."),
    billTo: z.enum(["CLIENT", "PARTNER"]),
    discountPercent: decimalString({ required: false, label: "Discount", scale: 3, max: "100" }),
    feePercent: decimalString({ required: false, label: "Fee", scale: 3, max: "100" }),
    feeOverrideReason: z.string().trim().max(500, "Use at most 500 characters."),
    dueDays: z
      .string()
      .trim()
      .refine((v) => v === "" || (/^\d{1,3}$/.test(v) && Number(v) <= 365), "Enter 0 to 365 days."),
    billingName: z.string().trim().max(200, "Use at most 200 characters."),
    billingAddress: z.string().trim().max(1000, "Use at most 1000 characters."),
    billingStateCode: z.string(),
    billingGstin: z
      .string()
      .trim()
      .refine((v) => v === "" || GSTIN.test(v.toUpperCase()), "Enter a 15-character GSTIN, e.g. 36AABCI1234A1ZC."),
    placeOfSupplyCode: z.string(),
    placeOfSupplyBasis: z.string(),
    notes: z.string().max(5000, "Use at most 5000 characters."),
    lines: locked ? z.array(lineSchema) : z.array(lineSchema).min(1, "Add at least one item.").max(maxLines, `An invoice can have at most ${maxLines} items.`),
  });

type FormValues = z.input<ReturnType<typeof makeSchema>>;
type LineValues = FormValues["lines"][number];

const FIELDS = [
  "leadId", "billTo", "discountPercent", "feePercent", "feeOverrideReason", "dueDays", "billingName", "billingAddress",
  "billingStateCode", "billingGstin", "placeOfSupplyCode", "placeOfSupplyBasis", "notes", "lines",
] as const;
const LABELS: Record<string, string> = {
  leadId: "Lead", billTo: "Bill to", discountPercent: "Discount", feePercent: "Fee rate", feeOverrideReason: "Reason for the fee",
  dueDays: "Due in (days)", billingName: "Billing name", billingAddress: "Billing address", billingStateCode: "Billing state",
  billingGstin: "Customer GSTIN", placeOfSupplyCode: "Place of supply", placeOfSupplyBasis: "Basis", notes: "Notes and terms", lines: "Items",
};

const same = (a: string | number | null | undefined, b: string | number | null | undefined) =>
  a !== null && a !== undefined && b !== null && b !== undefined && a !== "" && b !== "" && Number(a) === Number(b);
const blank = (v: string | undefined) => (v?.trim() ? v.trim() : null);

let keySeq = 0;
const nextKey = () => `new-${++keySeq}`;

interface InvoiceFormProps {
  meta: InvoiceMeta;
  invoice?: Invoice;
  /** PRODUCTS:VIEW_INTERNAL_B2C: the preview may carry the B2C comparison (the server decides). */
  internal: boolean;
  onSubmit: (input: InvoiceInput) => Promise<void>;
  onCancel: () => void;
}

/**
 * Draft invoice editor (Phase 9). A direct draft picks its lead and products through restricted pickers; a draft
 * converted from an estimate keeps the estimate's commercial values (lead, buyer, items, prices, discount, fee) and
 * only takes the billing details, place of supply and due days. Tax and totals come from POST /invoices/preview.
 */
export function InvoiceForm({ meta, invoice, internal, onSubmit, onCancel }: InvoiceFormProps) {
  const editing = !!invoice;
  const locked = !!invoice?.estimate;
  const schema = useMemo(() => makeSchema(meta.maxLines, locked), [meta.maxLines, locked]);
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    setFocus,
    control,
    formState: { errors, isSubmitting, isDirty, isSubmitSuccessful },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      leadId: invoice?.lead?.id ?? "",
      billTo: invoice?.billTo ?? "CLIENT",
      discountPercent: invoice ? String(invoice.discountPercent) : "",
      feePercent: invoice ? String(invoice.feePercent) : "",
      feeOverrideReason: "",
      dueDays: String(invoice?.dueDays ?? meta.dueDays),
      billingName: invoice?.billing.name ?? "",
      billingAddress: invoice?.billing.address ?? "",
      billingStateCode: invoice?.billing.stateCode ?? "",
      billingGstin: invoice?.billing.gstin ?? "",
      placeOfSupplyCode: invoice?.placeOfSupplyCode ?? "",
      placeOfSupplyBasis: invoice?.placeOfSupplyBasis ?? "",
      notes: invoice ? (invoice.notes ?? "") : meta.terms,
      lines: locked
        ? []
        : (invoice?.lines.map((l) => ({
            key: l.id ?? nextKey(),
            id: l.id ?? undefined,
            kind: l.kind ?? "PRODUCT",
            productId: l.productId ?? "",
            sku: l.sku ?? "",
            description: l.description,
            unit: l.unit ?? "",
            sourceGst: String(l.gstPercent),
            quantity: String(l.quantity),
            unitPrice: String(l.unitPrice),
            gstPercent: String(l.gstPercent),
          })) ?? []),
    },
  });
  const lines = useFieldArray({ control, name: "lines", keyName: "fieldKey" });
  const values = useWatch({ control }) as FormValues;

  const partnerBuys = values.billTo === "PARTNER";
  const sameDeal = editing && !locked && values.leadId === invoice.lead?.id && values.billTo === invoice.billTo;
  /** Null keeps the stored fee (same lead and buyer) or applies the default; partner purchases carry no fee. */
  const feeToSend = (fee: string) => {
    if (locked || !meta.canOverrideFee || partnerBuys || fee.trim() === "") return null;
    if (sameDeal && same(fee, invoice.feePercent)) return null;
    return fee.trim();
  };

  const toInput = (v: FormValues): InvoiceInput => ({
    // Converted drafts send no commercial values: the server keeps the estimate's.
    leadId: locked ? null : v.leadId,
    billTo: locked ? null : (v.billTo as BillTo),
    discountPercent: locked ? null : blank(v.discountPercent),
    feePercent: feeToSend(v.feePercent ?? ""),
    feeOverrideReason: locked ? null : blank(v.feeOverrideReason),
    notes: v.notes ?? "",
    dueDays: v.dueDays?.trim() ? Number(v.dueDays) : null,
    billingName: blank(v.billingName),
    billingAddress: blank(v.billingAddress),
    billingStateCode: v.billingStateCode || null,
    billingGstin: v.billingGstin?.trim() ? v.billingGstin.trim().toUpperCase() : null,
    placeOfSupplyCode: v.placeOfSupplyCode || null,
    placeOfSupplyBasis: (v.placeOfSupplyBasis || null) as PlaceOfSupplyBasis | null,
    lines: locked
      ? null
      : (v.lines ?? []).map((l) =>
          l.kind === "INSTALLATION"
            ? { id: l.id, kind: "INSTALLATION", description: l.description.trim(), unit: l.unit.trim() || "job", quantity: l.quantity.trim(), unitPrice: l.unitPrice.trim(), gstPercent: l.gstPercent.trim() }
            : { id: l.id, kind: "PRODUCT", productId: l.productId, quantity: l.quantity.trim() },
        ),
    version: invoice?.version,
  });

  // Live tax and totals: debounced, and only for input the server would accept.
  const linesValid = (values.lines ?? []).every(
    (l) => (l.kind === "INSTALLATION" ? l.description.trim() !== "" && l.description.trim().length <= 300 && l.unit.trim().length <= 20 : !!l.productId) && QUANTITY.test(l.quantity.trim()) && Number(l.quantity) > 0 && /^\d+(\.\d{1,2})?$/.test(l.unitPrice.trim()) && /^\d+(\.\d{1,3})?$/.test(l.gstPercent.trim()) && Number(l.gstPercent) <= 100,
  );
  const gstinOk = !values.billingGstin?.trim() || GSTIN.test(values.billingGstin.trim().toUpperCase());
  const previewable = gstinOk && (locked || (!!values.leadId && (values.lines?.length ?? 0) > 0 && linesValid));
  const previewKey = useDebounce(previewable ? JSON.stringify({ ...toInput(values), feeOverrideReason: null }) : null, 400);
  const preview = useInvoicePreview(previewKey ? (JSON.parse(previewKey) as InvoiceInput) : null, invoice?.id);
  const shown = previewable ? preview.data : undefined;

  const feeInput = values.feePercent ?? "";
  const defaultFee = shown?.defaultFeePercent;
  const overriding =
    !locked && meta.canOverrideFee && !partnerBuys && feeInput.trim() !== "" && defaultFee !== undefined && !same(feeInput, defaultFee) && !(sameDeal && same(feeInput, invoice.feePercent));

  const submit = handleSubmit(async (v) => {
    setFormError(null);
    if (overriding && !v.feeOverrideReason?.trim()) {
      setError("feeOverrideReason", { type: "manual", message: "Give a reason for using a fee other than the default." }, { shouldFocus: true });
      return;
    }
    try {
      await onSubmit(toInput(v));
    } catch (error) {
      const unmatched = applyInvoiceErrors(error, setError);
      const hasFieldErrors = error instanceof ApiRequestError && error.fieldErrors.length > 0;
      if (!hasFieldErrors) setFormError(errorMessage(error));
      else if (unmatched.length > 0) setFormError(unmatched.join(" "));
    }
  });

  const isInstallation = (i: number) => (values.lines?.[i] ?? lines.fields[i])?.kind === "INSTALLATION";
  const productCount = lines.fields.filter((_, i) => !isInstallation(i)).length;

  /** Installation lines always sit after the product lines, so the form order is the document order. */
  const addInstallation = () => {
    lines.append({
      key: nextKey(),
      kind: "INSTALLATION",
      productId: "",
      sku: "",
      description: "",
      unit: "job",
      sourceGst: "",
      quantity: "1",
      unitPrice: "",
      gstPercent: String(meta.defaultGstPercent),
    });
  };

  const addProduct = (p: ProductOption) => {
    lines.insert(productCount, {
      key: nextKey(),
      kind: "PRODUCT",
      productId: p.id,
      sku: p.sku,
      description: p.name,
      unit: p.unit ?? "",
      sourceGst: String(p.gstPercent),
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
  const linesError = errors.lines?.message ?? errors.lines?.root?.message;

  const installationRows: InstallationRow[] = lines.fields.flatMap((line, i) =>
    isInstallation(i)
      ? [
          {
            index: i,
            fieldKey: line.fieldKey,
            line: (values.lines?.[i] ?? line) as InstallationRow["line"],
            errors: lineErrors[i] as InstallationRow["errors"],
            amount: shown?.lines[i]?.amount,
            tax: shown?.lines[i]?.taxAmount,
            total: shown?.lines[i]?.lineTotal,
          },
        ]
      : [],
  );
  const installationTotal = shown && installationRows.length > 0 ? installationRows.reduce((sum, r) => sum + Number(r.total ?? 0), 0).toFixed(2) : undefined;

  const [leadSearch, setLeadSearch] = useState("");
  const leadResults = useInvoiceLeadOptions(useDebounce(leadSearch.trim()), !locked);
  const results: InvoiceLeadChoice[] = (leadResults.data ?? []).map((l) => ({ id: l.id, leadNumber: l.leadNumber, clientName: l.clientName, partnerName: l.partner.name }));
  const [chosenLead, setChosenLead] = useState<InvoiceLeadChoice | undefined>(() =>
    invoice?.lead ? { id: invoice.lead.id, leadNumber: invoice.lead.leadNumber, clientName: invoice.lead.clientName, partnerName: invoice.partner.name } : undefined,
  );
  const selectedLead = chosenLead?.id === values.leadId ? chosenLead : results.find((l) => l.id === values.leadId);
  const leadChoices = selectedLead && !results.some((l) => l.id === selectedLead.id) ? [selectedLead, ...results] : results;
  const leadOptions: SelectOption[] = leadChoices.map((l) => ({ value: l.id, label: `${l.clientName} · ${l.leadNumber}`, description: l.partnerName }));

  const stateOptions = meta.states.map((s) => (
    <option key={s.code} value={s.code}>
      {s.code} · {s.name}
    </option>
  ));

  return (
    <form onSubmit={submit} noValidate className="space-y-6" aria-label={editing ? "Edit draft invoice" : "New invoice"}>
      <UnsavedChangesGuard when={isDirty && !isSubmitting && !isSubmitSuccessful} />
      {!meta.supplierComplete ? (
        <Alert tone="warning" title="GST registration incomplete">
          The supplier&apos;s registered state, GSTIN and address must be set in Settings before an invoice can be issued. Drafts can still be saved.
        </Alert>
      ) : null}
      <FormError>{formError}</FormError>
      {summary.length + lineErrorCount > 1 ? (
        <ErrorSummary
          items={[...summary, ...(lineErrorCount > 0 ? [{ name: "lines", label: "Items", message: `${lineErrorCount} item(s) need attention.` }] : [])]}
          onSelect={(name) => name !== "lines" && setFocus(name as keyof FormValues)}
        />
      ) : null}

      {locked ? (
        <Alert tone="info" title={`From estimate ${invoice.estimate!.estimateNumber}`}>
          The lead, buyer, items, prices, discount and fee are copied from the estimate and can&apos;t be changed here. GST is calculated per line for the
          place of supply set below.
        </Alert>
      ) : (
        <FormSection title="Lead" description="The partner comes from the lead. Lost leads can't be invoiced.">
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
                  hint={selectedLead ? `Referred by ${selectedLead.partnerName}.` : "Search by client, lead number or partner."}
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
          </FormGrid>
        </FormSection>
      )}

      <FormSection title="Billing details" description="Printed on the invoice. The billing state does not decide the tax: the place of supply does.">
        <FormGrid>
          <FormField label="Billing name" error={errors.billingName?.message} hint="Required to issue.">
            <Input autoComplete="off" {...register("billingName")} />
          </FormField>
          <FormField label="Customer GSTIN" error={errors.billingGstin?.message} hint="Only for a GST-registered customer.">
            <Input autoComplete="off" className="font-mono uppercase" maxLength={15} {...register("billingGstin")} />
          </FormField>
          <FormField label="Billing address" error={errors.billingAddress?.message} wide hint="Required to issue.">
            <Textarea rows={3} {...register("billingAddress")} />
          </FormField>
          <FormField label="Billing state" error={errors.billingStateCode?.message}>
            <Select {...register("billingStateCode")}>
              <option value="">Not given</option>
              {stateOptions}
            </Select>
          </FormField>
          <Controller
            control={control}
            name="dueDays"
            render={({ field }) => (
              <FormField label="Due in (days)" error={errors.dueDays?.message} hint={`From the invoice date at issue. Default ${meta.dueDays}.`}>
                <NumberInput value={field.value ?? ""} onValueChange={field.onChange} onBlur={field.onBlur} ref={field.ref} />
              </FormField>
            )}
          />
        </FormGrid>
      </FormSection>

      <FormSection
        title="Place of supply"
        description="Choose the state where the supply takes place and why. Same state as the supplier: CGST + SGST/UTGST; any other state: IGST."
      >
        <FormGrid>
          <FormField label="Place of supply" error={errors.placeOfSupplyCode?.message} hint="Required to issue.">
            <Select {...register("placeOfSupplyCode")}>
              <option value="">Choose the state…</option>
              {stateOptions}
            </Select>
          </FormField>
          <FormField label="Basis" error={errors.placeOfSupplyBasis?.message} hint="Required to issue. Other bases need an approver's confirmation.">
            <Select {...register("placeOfSupplyBasis")}>
              <option value="">Choose the basis…</option>
              {meta.placeOfSupplyBases.map((b) => (
                <option key={b.value} value={b.value}>
                  {b.label}
                </option>
              ))}
            </Select>
          </FormField>
        </FormGrid>
        {shown ? <TaxTreatmentNotice tax={shown.tax} /> : null}
      </FormSection>

      <FormSection
        title="Items"
        description={locked ? "Copied from the estimate." : "Rate and GST come from the product sheet and can't be changed here. Installation work below is priced by you."}
      >
        {locked ? (
          shown ? (
            <InvoiceLinesTable lines={shown.lines} tax={shown.tax} />
          ) : (
            <InvoiceLinesTable lines={invoice.lines} tax={invoice.tax} />
          )
        ) : (
          <>
            {linesError ? (
              <p role="alert" className="text-sm text-danger">
                {linesError}
              </p>
            ) : null}
            {productCount > 0 ? (
              <div className="relative overflow-x-auto rounded-lg border border-border">
                <table className="w-full min-w-[720px] text-sm">
                  <caption className="sr-only">Invoice items</caption>
                  <thead className="bg-surface-sunken text-left text-xs text-muted-foreground">
                    <tr>
                      <th scope="col" className="px-3 py-2 font-medium">Item</th>
                      <th scope="col" className="w-28 px-3 py-2 font-medium">Qty</th>
                      <th scope="col" className="w-36 px-3 py-2 font-medium">Rate (₹)</th>
                      <th scope="col" className="w-28 px-3 py-2 font-medium">GST %</th>
                      <th scope="col" className="w-28 px-3 py-2 text-right font-medium">Tax</th>
                      <th scope="col" className="w-32 px-3 py-2 text-right font-medium">Total</th>
                      <th scope="col" className="w-12 px-3 py-2"><span className="sr-only">Remove</span></th>
                    </tr>
                  </thead>
                  <tbody>
                    {lines.fields.map((line, i) =>
                      isInstallation(i) ? null : (
                      <LineRow
                        key={line.fieldKey}
                        index={i}
                        line={values.lines?.[i] ?? line}
                        tax={shown?.lines[i]?.taxAmount}
                        total={shown?.lines[i]?.lineTotal}
                        b2c={internal && shown?.internal ? shown.internal.lines[i] : undefined}
                        errors={lineErrors[i]}
                        control={control}
                        onRemove={() => lines.remove(i)}
                      />
                      ),
                    )}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="rounded-lg border border-dashed border-border px-4 py-6 text-center text-sm text-muted-foreground">No items yet. Search the product sheet below to add one.</p>
            )}
            {lines.fields.length < meta.maxLines ? <ProductPicker onAdd={addProduct} internal={internal} /> : null}
          </>
        )}
      </FormSection>

      {locked ? null : (
        <FormSection title="Installation" description="Installation work is priced separately from the products. It is optional, printed in its own section and included in the totals.">
          <InstallationLines
            control={control as unknown as Control<InstallationFormValues>}
            rows={installationRows}
            showTax
            subtotal={installationTotal}
            subtotalLabel="Installation total (incl. GST)"
            gstLocked={(l) => !meta.canApprove && !!l.id && same(l.gstPercent, (l as LineValues).sourceGst)}
            canAdd={lines.fields.length < meta.maxLines}
            onAdd={addInstallation}
            onRemove={(i) => lines.remove(i)}
          />
        </FormSection>
      )}

      <FormSection title="Pricing" description="The server calculates each line's taxable value and GST, rounded per line to the paisa.">
        <div className="grid gap-6 lg:grid-cols-2">
          <FormGrid className="content-start">
            {locked ? (
              <>
                <FormField label="Discount">
                  <Input readOnly value={formatPercent(invoice.discountPercent)} />
                </FormField>
                <FormField label="Fee rate">
                  <Input readOnly value={formatPercent(invoice.feePercent)} />
                </FormField>
              </>
            ) : (
              <>
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
                    <Input readOnly value={partnerBuys ? formatPercent(0) : shown ? formatPercent(shown.feePercent) : editing ? formatPercent(invoice.feePercent) : "Calculated when items are added"} />
                  </FormField>
                )}
                {overriding ? (
                  <FormField label="Reason for the fee" error={errors.feeOverrideReason?.message} required wide hint="Kept in the invoice's history.">
                    <Input autoComplete="off" {...register("feeOverrideReason")} />
                  </FormField>
                ) : null}
              </>
            )}
            <FormField label="Notes and terms" error={errors.notes?.message} wide hint="Printed on the invoice.">
              <Textarea rows={5} {...register("notes")} />
            </FormField>
          </FormGrid>

          <div className="space-y-3" aria-live="polite">
            {shown ? (
              <InvoiceTotalsCard totals={shown.totals} tax={shown.tax} discountPercent={locked ? invoice.discountPercent : values.discountPercent || "0"} busy={preview.isFetching} />
            ) : (
              <p className="rounded-lg border border-dashed border-border px-4 py-6 text-center text-sm text-muted-foreground">
                {preview.isError && previewable ? errorMessage(preview.error) : "Choose a lead and add items to see the tax and totals."}
              </p>
            )}
            {shown?.comparison ? <EstimateComparisonNotice comparison={shown.comparison} taxDecided={!!shown.tax.taxType} /> : null}
            {internal && shown?.internal ? (
              <Alert tone={shown.internal.missingB2cPrices > 0 ? "warning" : "info"} title="Internal B2C comparison">
                Not shown on the partner copy. B2C taxable {formatCurrency(shown.internal.b2cTaxable)} · partner discount vs B2C list {formatCurrency(shown.internal.uplift)}.
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
        <Button type="submit" variant="primary" loading={isSubmitting} loadingText="Saving…">
          {editing ? "Save draft" : "Create draft"}
        </Button>
      </FormActions>
    </form>
  );
}

interface LineRowProps {
  index: number;
  line: LineValues;
  tax: string | number | undefined;
  total: string | number | undefined;
  b2c?: { b2cName: string | null; b2cPriceMissing: boolean };
  errors?: Partial<Record<keyof LineValues, { message?: string }>>;
  control: ReturnType<typeof useForm<FormValues>>["control"];
  onRemove: () => void;
}

function LineRow({ index, line, tax, total, b2c, errors, control, onRemove }: LineRowProps) {
  const name = `${line.sku} ${line.description}`.trim();
  const errorId = `line-${index}-error`;
  const messages = [errors?.productId?.message, errors?.quantity?.message].filter(Boolean);
  const cell = (field: "quantity", label: string, scale: number) => (
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
      <td className="px-3 py-2 text-right font-mono tabular whitespace-nowrap" title="From the product sheet">
        {formatCurrency(line.unitPrice)}
      </td>
      <td className="px-3 py-2 text-right font-mono tabular whitespace-nowrap" title="From the product sheet">
        {formatPercent(line.gstPercent)}
      </td>
      <td className="px-3 py-2 text-right font-mono tabular whitespace-nowrap">{tax === undefined ? "—" : formatCurrency(tax)}</td>
      <td className="px-3 py-2 text-right font-mono tabular whitespace-nowrap">{total === undefined ? "—" : formatCurrency(total)}</td>
      <td className="px-3 py-2 text-right">
        <Button size="icon-sm" variant="ghost" onClick={onRemove} aria-label={`Remove ${name}`}>
          <Trash2 aria-hidden="true" />
        </Button>
      </td>
    </tr>
  );
}

/** Server field names use `lines[0].quantity`; the form uses `lines.0.quantity`. */
function applyInvoiceErrors(error: unknown, setError: ReturnType<typeof useForm<FormValues>>["setError"]): string[] {
  if (!(error instanceof ApiRequestError) || error.fieldErrors.length === 0) return [];
  const unmatched: string[] = [];
  let focused = false;
  for (const fe of error.fieldErrors) {
    const name = fe.field.replace(/\[(\d+)\]/g, ".$1");
    const known = (FIELDS as readonly string[]).includes(name) || /^lines\.\d+\.(productId|description|unit|quantity|unitPrice|gstPercent)$/.test(name);
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
