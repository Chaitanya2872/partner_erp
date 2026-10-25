import { useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "@/components/ui/Button";
import { Input, Select, Textarea } from "@/components/ui/Input";
import { Alert } from "@/components/ui/Alert";
import { ErrorSummary, FormActions, FormError, FormField, FormGrid, FormSection, type SummaryItem } from "@/components/forms/FormField";
import { CurrencyInput, PercentageInput } from "@/components/forms/inputs";
import { UnsavedChangesGuard } from "@/components/forms/UnsavedChangesGuard";
import { applyServerErrors, compareDecimal, decimalString } from "@/lib/validation";
import { errorMessage } from "@/lib/errors";
import { ApiRequestError } from "@/services/api";
import { discountPreview } from "../utils/productDisplay";
import { PRODUCT_CATEGORIES, type Product, type ProductCategory, type ProductInput, type ProductMeta } from "../types/product.types";

/* Mirrors ProductRules on the server; the server stays authoritative. */
const MAX_PRICE = "999999999999.99";
const text = (max: number) => z.string().trim().max(max, `Use at most ${max} characters.`);

function makeSchema(internal: boolean, maxAliases: number) {
  return z
    .object({
      sku: text(50)
        .min(1, "SKU is required.")
        .regex(/^[A-Za-z0-9][A-Za-z0-9._/+-]*$/, "Use letters, digits and . _ / + - only, starting with a letter or digit."),
      category: z.string().min(1, "Choose a category."),
      unit: text(20),
      hsn: z
        .string()
        .trim()
        .refine((v) => v === "" || /^\d{4}(\d{2}){0,2}$/.test(v.replaceAll(" ", "")), "Enter an HSN or SAC code of 4, 6 or 8 digits."),
      partnerName: text(300).min(1, "Partner-facing name is required."),
      partnerPrice: decimalString({ max: MAX_PRICE, label: "Partner price" }),
      gstPercent: decimalString({ scale: 3, max: "100", label: "GST %" }),
      b2cName: text(300),
      aliases: z.string(),
      b2cPrice: decimalString({ required: false, max: MAX_PRICE, label: "B2C price" }),
      costPrice: decimalString({ required: false, max: MAX_PRICE, label: "Landed cost" }),
    })
    .superRefine((v, ctx) => {
      if (!internal) return;
      if (v.b2cName === "") ctx.addIssue({ code: "custom", path: ["b2cName"], message: "B2C name is required." });
      const names = aliasList(v.aliases);
      if (names.length > maxAliases) ctx.addIssue({ code: "custom", path: ["aliases"], message: `Use at most ${maxAliases} alternative names.` });
      if (names.some((n) => n.length > 300)) ctx.addIssue({ code: "custom", path: ["aliases"], message: "Each name can have at most 300 characters." });
    });
}

type FormValues = z.input<ReturnType<typeof makeSchema>>;
type ParsedValues = z.output<ReturnType<typeof makeSchema>>;

const FIELDS = ["sku", "category", "unit", "hsn", "partnerName", "partnerPrice", "gstPercent", "b2cName", "aliases", "b2cPrice", "costPrice"] as const;

const LABELS: Record<string, string> = {
  sku: "SKU", category: "Category", unit: "Unit", hsn: "HSN / SAC", partnerName: "Partner-facing name", partnerPrice: "Partner price",
  gstPercent: "GST %", b2cName: "B2C name", aliases: "Alternative names", b2cPrice: "B2C price", costPrice: "Landed cost",
};

/** One name per line, trimmed, blank lines dropped, duplicates (ignoring case) removed. */
function aliasList(value: string): string[] {
  const seen = new Set<string>();
  return value
    .split("\n")
    .map((s) => s.trim().replace(/\s+/g, " "))
    .filter((s) => s && !seen.has(s.toLowerCase()) && seen.add(s.toLowerCase()));
}

const decimal = (value: number | null | undefined) => (value === null || value === undefined ? "" : String(value));

interface ProductFormProps {
  meta: ProductMeta;
  product?: Product;
  /** PRODUCTS:VIEW_INTERNAL_B2C: shows and sends the B2C name, alternative names, B2C price and landed cost. */
  internal: boolean;
  onSubmit: (input: ProductInput) => Promise<void>;
  onCancel: () => void;
}

export function ProductForm({ meta, product, internal, onSubmit, onCancel }: ProductFormProps) {
  const editing = !!product;
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    setFocus,
    control,
    formState: { errors, isSubmitting, isDirty, isSubmitSuccessful },
  } = useForm<FormValues, unknown, ParsedValues>({
    resolver: zodResolver(makeSchema(internal, meta.maxAliases)),
    defaultValues: {
      sku: product?.sku ?? "",
      category: product?.category ?? "",
      unit: product?.unit ?? meta.defaultUnit,
      hsn: product?.hsn ?? "",
      partnerName: product?.partnerName ?? "",
      partnerPrice: decimal(product?.partnerPrice),
      gstPercent: decimal(product?.gstPercent ?? meta.defaultGstPercent),
      b2cName: product?.b2cName ?? "",
      aliases: (product?.aliases ?? []).join("\n"),
      b2cPrice: decimal(product?.b2cPrice),
      costPrice: decimal(product?.costPrice),
    },
  });

  const [partnerName, b2cName, partnerPrice, b2cPrice, costPrice, aliases] = useWatch({
    control,
    name: ["partnerName", "b2cName", "partnerPrice", "b2cPrice", "costPrice", "aliases"],
  });
  const discount = internal ? discountPreview(partnerPrice, b2cPrice) : null;
  const warnings: string[] = [];
  if (internal) {
    if (partnerName.trim() && partnerName.trim().toLowerCase() === b2cName.trim().toLowerCase()) {
      warnings.push("The partner-facing name is the same as the B2C name, so partners could match it to B2C listings.");
    }
    if (b2cPrice === "" || Number(b2cPrice) === 0) warnings.push("B2C price needed: the internal B2C copy of documents will show it as missing.");
    else if (partnerPrice && compareDecimal(partnerPrice, b2cPrice) > 0) warnings.push("The partner price is above the B2C price.");
    if (costPrice && Number(costPrice) > 0 && partnerPrice && compareDecimal(partnerPrice, costPrice) < 0) {
      warnings.push("The partner price is below the landed cost.");
    }
  }

  const submit = handleSubmit(async (v) => {
    setFormError(null);
    const input: ProductInput = {
      sku: v.sku,
      category: v.category as ProductCategory,
      unit: v.unit,
      hsn: v.hsn.replaceAll(" ", ""),
      partnerName: v.partnerName,
      partnerPrice: v.partnerPrice,
      gstPercent: v.gstPercent,
      version: product?.version,
    };
    // Without the internal action the B2C fields are left out entirely; the server keeps the stored values.
    if (internal) {
      input.b2cName = v.b2cName;
      input.aliases = aliasList(v.aliases);
      input.b2cPrice = v.b2cPrice === "" ? null : v.b2cPrice;
      input.costPrice = v.costPrice === "" ? null : v.costPrice;
    }
    try {
      await onSubmit(input);
    } catch (error) {
      const unmatched = applyServerErrors(error, setError, FIELDS);
      const hasFieldErrors = error instanceof ApiRequestError && error.fieldErrors.length > 0;
      if (!hasFieldErrors) setFormError(errorMessage(error));
      else if (unmatched.length > 0) setFormError(unmatched.join(" "));
    }
  });

  const summary: SummaryItem[] = Object.entries(errors)
    .filter(([, e]) => e?.message)
    .map(([name, e]) => ({ name, label: LABELS[name] ?? name, message: String(e!.message) }));
  const aliasCount = aliasList(aliases).length;

  return (
    <form onSubmit={submit} noValidate className="space-y-6" aria-label={editing ? `Edit ${product.sku}` : "New product"}>
      <UnsavedChangesGuard when={isDirty && !isSubmitting && !isSubmitSuccessful} />
      <FormError>{formError}</FormError>
      {summary.length > 1 ? <ErrorSummary items={summary} onSelect={(name) => setFocus(name as keyof FormValues)} /> : null}

      <FormSection title="Identity" description="The SKU is unique, ignoring case.">
        <FormGrid>
          <FormField label="SKU" error={errors.sku?.message} required>
            <Input autoComplete="off" className="font-mono" {...register("sku")} />
          </FormField>
          <FormField label="Category" error={errors.category?.message} required>
            <Select {...register("category")}>
              <option value="">Choose a category…</option>
              {PRODUCT_CATEGORIES.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField label="Unit" error={errors.unit?.message} hint={`Defaults to “${meta.defaultUnit}”.`}>
            <Input autoComplete="off" {...register("unit")} />
          </FormField>
          <FormField label="HSN / SAC" error={errors.hsn?.message} hint="4, 6 or 8 digits.">
            <Input autoComplete="off" inputMode="numeric" className="font-mono" {...register("hsn")} />
          </FormField>
        </FormGrid>
      </FormSection>

      <FormSection
        title="Names"
        description={internal ? "The partner-facing name is used on partner documents; the B2C name only on internal copies." : "The name used on partner documents."}
      >
        <FormGrid>
          <FormField label="Partner-facing name" error={errors.partnerName?.message} required wide>
            <Input autoComplete="off" {...register("partnerName")} />
          </FormField>
          {internal ? (
            <>
              <FormField label="B2C name (internal reference)" error={errors.b2cName?.message} required wide>
                <Input autoComplete="off" {...register("b2cName")} />
              </FormField>
              <FormField
                label="Alternative partner names"
                error={errors.aliases?.message}
                hint={`One per line (6 to 7 options suggested; ${aliasCount} entered). Use technical wording so partners can't match it to B2C listings.`}
                wide
              >
                <Textarea rows={6} {...register("aliases")} />
              </FormField>
            </>
          ) : null}
        </FormGrid>
      </FormSection>

      <FormSection title="Price & tax" description="Prices are in rupees, excluding GST.">
        <FormGrid>
          <Controller
            control={control}
            name="partnerPrice"
            render={({ field }) => (
              <FormField label="Partner price" error={errors.partnerPrice?.message} required>
                <CurrencyInput value={field.value} onValueChange={field.onChange} onBlur={field.onBlur} ref={field.ref} />
              </FormField>
            )}
          />
          <Controller
            control={control}
            name="gstPercent"
            render={({ field }) => (
              <FormField label="GST %" error={errors.gstPercent?.message} required>
                <PercentageInput scale={3} value={field.value} onValueChange={field.onChange} onBlur={field.onBlur} ref={field.ref} />
              </FormField>
            )}
          />
          {internal ? (
            <>
              <Controller
                control={control}
                name="b2cPrice"
                render={({ field }) => (
                  <FormField
                    label="B2C price"
                    error={errors.b2cPrice?.message}
                    hint={discount === null ? "Leave blank until it is known; never estimate it." : `Partner discount: ${discount}%`}
                  >
                    <CurrencyInput value={field.value} onValueChange={field.onChange} onBlur={field.onBlur} ref={field.ref} />
                  </FormField>
                )}
              />
              <Controller
                control={control}
                name="costPrice"
                render={({ field }) => (
                  <FormField label="Landed cost" error={errors.costPrice?.message} hint="Optional.">
                    <CurrencyInput value={field.value} onValueChange={field.onChange} onBlur={field.onBlur} ref={field.ref} />
                  </FormField>
                )}
              />
            </>
          ) : null}
        </FormGrid>
        {warnings.length > 0 ? (
          <Alert tone="warning" title="Check before saving" className="mt-4">
            <ul className="list-disc space-y-0.5 pl-4">
              {warnings.map((w) => (
                <li key={w}>{w}</li>
              ))}
            </ul>
          </Alert>
        ) : null}
      </FormSection>

      <FormActions>
        <Button onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" variant="primary" loading={isSubmitting} loadingText="Saving…">
          {editing ? "Save changes" : "Create product"}
        </Button>
      </FormActions>
    </form>
  );
}
