import { useState } from "react";
import { Controller, useForm, useWatch, type Control } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { AlertTriangle } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Checkbox } from "@/components/ui/Checkbox";
import { Input, Select, Textarea } from "@/components/ui/Input";
import { Alert } from "@/components/ui/Alert";
import { ErrorSummary, FormActions, FormError, FormField, FormGrid, FormSection, type SummaryItem } from "@/components/forms/FormField";
import { DateInput } from "@/components/forms/inputs";
import { UnsavedChangesGuard } from "@/components/forms/UnsavedChangesGuard";
import { applyServerErrors } from "@/lib/validation";
import { errorMessage } from "@/lib/errors";
import { ApiRequestError } from "@/services/api";
import { businessToday, formatCurrency, formatPercent } from "@/utils/format";
import type { Partner, PartnerCategory, PartnerInput, PartnerMeta, PartnerStatus, AgreementStatus } from "../types/partner.types";

/* Mirrors the server rules in PartnerService.normalize; the server stays authoritative. */
const optional = (max: number) => z.string().trim().max(max, `Use at most ${max} characters.`);
const PAN = /^[A-Z]{5}[0-9]{4}[A-Z]$/;
const GSTIN = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/;
const IFSC = /^[A-Z]{4}0[A-Z0-9]{6}$/;

const schema = z.object({
  name: z.string().trim().min(1, "Name is required.").max(200, "Use at most 200 characters."),
  category: z.string().min(1, "Select a category."),
  status: z.string(),
  contactName: optional(150),
  phone: optional(30).refine((v) => v === "" || (/^[0-9+()\-\s]+$/.test(v) && /^(\D*\d){7,15}\D*$/.test(v)), "Enter a valid phone number."),
  email: optional(254).refine((v) => v === "" || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v), "Enter a valid email address."),
  city: optional(100),
  tierLevel: z.string(),
  strategicProgramme: z.boolean(),
  agreementStatus: z.string(),
  agreementVersion: optional(20),
  agreementDate: z.string().refine((v) => v === "" || v <= businessToday(), "The agreement date can't be in the future."),
  pan: z.string().trim().toUpperCase().refine((v) => v === "" || PAN.test(v), "Enter a valid PAN (e.g. ABCDE1234F)."),
  removePan: z.boolean(),
  gstin: z.string().trim().toUpperCase().refine((v) => v === "" || GSTIN.test(v), "Enter a valid 15-character GSTIN."),
  removeGstin: z.boolean(),
  licenceNo: optional(100),
  bankName: optional(150),
  bankAccount: z
    .string()
    .transform((v) => v.replace(/[\s-]/g, ""))
    .refine((v) => v === "" || /^[0-9]{9,18}$/.test(v), "Enter a bank account number of 9 to 18 digits."),
  removeBankAccount: z.boolean(),
  ifsc: z.string().trim().toUpperCase().refine((v) => v === "" || IFSC.test(v), "Enter a valid IFSC (e.g. HDFC0001234)."),
  notes: optional(2000),
});

type FormValues = z.input<typeof schema>;
type ParsedValues = z.output<typeof schema>;

const FIELDS = [
  "name", "category", "status", "contactName", "phone", "email", "city", "tierLevel", "strategicProgramme", "agreementStatus",
  "agreementVersion", "agreementDate", "pan", "gstin", "licenceNo", "bankName", "bankAccount", "ifsc", "notes",
] as const;

const LABELS: Record<string, string> = {
  name: "Name", category: "Category", phone: "Phone", email: "Email", city: "City", contactName: "Contact person",
  agreementDate: "Signed / sent on", agreementVersion: "Agreement version", pan: "PAN", gstin: "GSTIN", ifsc: "IFSC",
  bankAccount: "Bank account", bankName: "Bank", licenceNo: "Licence / registration no.", notes: "Notes", tierLevel: "Tier",
};

interface PartnerFormProps {
  meta: PartnerMeta;
  partner?: Partner;
  /** Partners approve: may set the tier and the programme flag (they change commission rates). */
  canApprove: boolean;
  onSubmit: (input: PartnerInput) => Promise<void>;
  onCancel: () => void;
}

export function PartnerForm({ meta, partner, canApprove, onSubmit, onCancel }: PartnerFormProps) {
  const editing = !!partner;
  // Without VIEW_PARTNER_SENSITIVE the stored PAN, GSTIN and bank account are masked: the fields start empty and
  // only replace.
  const writeOnly = editing && !partner.sensitiveVisible;
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    setFocus,
    control,
    formState: { errors, isSubmitting, isDirty, isSubmitSuccessful },
  } = useForm<FormValues, unknown, ParsedValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: partner?.name ?? "",
      category: partner?.category ?? "",
      status: partner?.status ?? "PROSPECT",
      contactName: partner?.contactName ?? "",
      phone: partner?.phone ?? "",
      email: partner?.email ?? "",
      city: partner?.city ?? "",
      tierLevel: String(partner?.tier.level ?? 1),
      strategicProgramme: partner?.strategicProgramme ?? false,
      agreementStatus: partner?.agreementStatus ?? "NOT_SENT",
      agreementVersion: partner?.agreementVersion ?? meta.defaultAgreementVersion,
      agreementDate: partner?.agreementDate ?? "",
      pan: writeOnly ? "" : (partner?.pan ?? ""),
      removePan: false,
      gstin: writeOnly ? "" : (partner?.gstin ?? ""),
      removeGstin: false,
      licenceNo: partner?.licenceNo ?? "",
      bankName: partner?.bankName ?? "",
      bankAccount: writeOnly ? "" : (partner?.bankAccount ?? ""),
      removeBankAccount: false,
      ifsc: partner?.ifsc ?? "",
      notes: partner?.notes ?? "",
    },
  });

  const [category, programme, tierLevel, removePan, removeGstin, removeBank] = useWatch({
    control,
    name: ["category", "strategicProgramme", "tierLevel", "removePan", "removeGstin", "removeBankAccount"],
  });
  const selectedTier = meta.tiers.find((t) => t.level === (programme ? 4 : Number(tierLevel)));

  /** PAN/GSTIN/bank: null keeps the stored value, "" removes it. */
  const sensitive = (value: string, remove: boolean) => {
    if (!editing) return value === "" ? null : value;
    if (!writeOnly) return value;
    if (remove) return "";
    return value === "" ? null : value;
  };

  const submit = handleSubmit(async (v) => {
    setFormError(null);
    const input: PartnerInput = {
      name: v.name,
      category: v.category as PartnerCategory,
      status: v.status as PartnerStatus,
      contactName: v.contactName,
      phone: v.phone,
      email: v.email,
      city: v.city,
      tierLevel: canApprove ? Number(v.tierLevel) : null,
      strategicProgramme: canApprove ? v.strategicProgramme : null,
      agreementStatus: v.agreementStatus as AgreementStatus,
      agreementVersion: v.agreementVersion,
      agreementDate: v.agreementDate === "" ? null : v.agreementDate,
      pan: sensitive(v.pan, v.removePan),
      gstin: sensitive(v.gstin, v.removeGstin),
      licenceNo: v.licenceNo,
      bankName: v.bankName,
      bankAccount: sensitive(v.bankAccount, v.removeBankAccount),
      ifsc: v.ifsc,
      notes: v.notes,
      version: partner?.version,
    };
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

  return (
    <form onSubmit={submit} noValidate className="space-y-6" aria-label={editing ? `Edit ${partner.name}` : "New partner"}>
      {/* Off while submitting: the page navigates away from inside onSubmit after a successful save. */}
      <UnsavedChangesGuard when={isDirty && !isSubmitting && !isSubmitSuccessful} />
      <FormError>{formError}</FormError>
      {summary.length > 1 ? <ErrorSummary items={summary} onSelect={(name) => setFocus(name as keyof FormValues)} /> : null}

      <FormSection title="Partner" description="Who they are and how to reach them.">
        <FormGrid>
          <FormField label="Name" error={errors.name?.message} required wide>
            <Input autoComplete="organization" {...register("name")} />
          </FormField>
          <FormField label="Category" error={errors.category?.message} required>
            <Select {...register("category")}>
              <option value="">Select a category…</option>
              {meta.categories.map((c) => (
                <option key={c.code} value={c.code}>
                  {c.label}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField label="Status" error={errors.status?.message}>
            <Select {...register("status")}>
              {meta.statuses.map((s) => (
                <option key={s.code} value={s.code}>
                  {s.label}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField label="Contact person" error={errors.contactName?.message}>
            <Input autoComplete="name" {...register("contactName")} />
          </FormField>
          <FormField label="Phone" error={errors.phone?.message}>
            <Input type="tel" inputMode="tel" autoComplete="tel" {...register("phone")} />
          </FormField>
          <FormField label="Email" error={errors.email?.message}>
            <Input type="email" inputMode="email" autoComplete="email" {...register("email")} />
          </FormField>
          <FormField label="City" error={errors.city?.message}>
            <Input autoComplete="address-level2" {...register("city")} />
          </FormField>
        </FormGrid>
        {category === "ARCHITECT" ? (
          <Alert tone="warning" title="Architects" className="mt-4">
            Take legal advice before paying a cash referral fee (CoA conduct rules).
          </Alert>
        ) : null}
      </FormSection>

      <FormSection title="Agreement & tier" description="The partner agreement and the referral fee tier.">
        <FormGrid>
          <FormField label="Agreement" error={errors.agreementStatus?.message}>
            <Select {...register("agreementStatus")}>
              {meta.agreementStatuses.map((a) => (
                <option key={a.code} value={a.code}>
                  {a.label}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField label="Agreement version" error={errors.agreementVersion?.message}>
            <Input {...register("agreementVersion")} />
          </FormField>
          <FormField label="Signed / sent on" error={errors.agreementDate?.message}>
            <DateInput max={businessToday()} {...register("agreementDate")} />
          </FormField>
          <FormField
            label="Tier"
            error={errors.tierLevel?.message}
            hint={
              !canApprove
                ? "Only users with Partners approve can change the tier."
                : programme
                  ? "Programme partners are always Strategic."
                  : selectedTier
                    ? `${formatPercent(selectedTier.commissionPercent)} referral fee · paid in ${selectedTier.payoutBusinessDays} business days`
                    : undefined
            }
          >
            <Select {...register("tierLevel")} disabled={!canApprove || programme}>
              {meta.tiers.map((t) => (
                <option key={t.level} value={String(t.level)}>
                  {t.level} · {t.name} (from {formatCurrency(t.minimumRevenue, { whole: true })})
                </option>
              ))}
            </Select>
          </FormField>
        </FormGrid>
        <Controller
          control={control}
          name="strategicProgramme"
          render={({ field }) => (
            <div className="mt-4 flex items-start gap-2.5">
              <Checkbox
                id="partner-programme"
                checked={field.value}
                onCheckedChange={(checked) => field.onChange(checked === true)}
                disabled={!canApprove}
                aria-describedby="partner-programme-hint"
              />
              <div className="text-sm">
                <label htmlFor="partner-programme" className="font-medium">
                  Signed builder / developer / contractor programme
                </label>
                <p id="partner-programme-hint" className="text-muted-foreground">
                  Sets the tier to Strategic on save.
                </p>
              </div>
            </div>
          )}
        />
      </FormSection>

      <FormSection title="Compliance & bank" description="Needed before the first referral fee can be paid out.">
        {writeOnly ? (
          <Alert tone="neutral" title="PAN, GSTIN and bank account are masked" className="mb-4">
            Stored values are not shown to you. Leave the fields empty to keep them, or type a new value to replace them.
          </Alert>
        ) : null}
        <FormGrid>
          <FormField
            label="PAN"
            error={errors.pan?.message}
            hint={writeOnly && partner.panOnFile ? `On file: ${partner.pan}` : writeOnly ? "Not on file." : undefined}
          >
            <Input className="uppercase" autoComplete="off" spellCheck={false} disabled={removePan} {...register("pan")} />
          </FormField>
          <FormField
            label="GSTIN"
            error={errors.gstin?.message}
            hint={writeOnly && partner.gstinOnFile ? `On file: ${partner.gstin}` : writeOnly ? "Not on file." : undefined}
          >
            <Input className="uppercase" autoComplete="off" spellCheck={false} disabled={removeGstin} {...register("gstin")} />
          </FormField>
          <FormField label="Licence / registration no." error={errors.licenceNo?.message}>
            <Input autoComplete="off" {...register("licenceNo")} />
          </FormField>
          <FormField label="Bank" error={errors.bankName?.message}>
            <Input autoComplete="off" {...register("bankName")} />
          </FormField>
          <FormField
            label="Bank account"
            error={errors.bankAccount?.message}
            hint={writeOnly && partner.bankAccountOnFile ? `On file: ${partner.bankAccount}` : writeOnly ? "Not on file." : undefined}
          >
            <Input inputMode="numeric" autoComplete="off" spellCheck={false} disabled={removeBank} {...register("bankAccount")} />
          </FormField>
          <FormField label="IFSC" error={errors.ifsc?.message}>
            <Input className="uppercase" autoComplete="off" spellCheck={false} {...register("ifsc")} />
          </FormField>
        </FormGrid>
        {writeOnly && (partner.panOnFile || partner.gstinOnFile || partner.bankAccountOnFile) ? (
          <div className="mt-4 flex flex-wrap gap-x-6 gap-y-2 text-sm">
            {partner.panOnFile ? <RemoveToggle control={control} name="removePan" label="Remove the stored PAN" /> : null}
            {partner.gstinOnFile ? <RemoveToggle control={control} name="removeGstin" label="Remove the stored GSTIN" /> : null}
            {partner.bankAccountOnFile ? <RemoveToggle control={control} name="removeBankAccount" label="Remove the stored bank account" /> : null}
          </div>
        ) : null}
      </FormSection>

      <FormSection title="Notes">
        <FormField label="Notes" error={errors.notes?.message} wide>
          <Textarea rows={4} {...register("notes")} />
        </FormField>
      </FormSection>

      {editing && canApprove && Number(tierLevel) !== partner.tier.level && !programme ? (
        <Alert tone="warning" title="Tier change">
          <span className="inline-flex items-center gap-1">
            <AlertTriangle className="size-3.5" aria-hidden="true" />
            New leads will use the {selectedTier?.name} fee rate. Fee rates already locked on acknowledged leads don't change.
          </span>
        </Alert>
      ) : null}

      <FormActions>
        <Button onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" variant="primary" loading={isSubmitting} loadingText="Saving…">
          {editing ? "Save changes" : "Create partner"}
        </Button>
      </FormActions>
    </form>
  );
}

interface RemoveToggleProps {
  control: Control<FormValues, unknown, ParsedValues>;
  name: "removePan" | "removeGstin" | "removeBankAccount";
  label: string;
}

function RemoveToggle({ control, name, label }: RemoveToggleProps) {
  return (
    <Controller
      control={control}
      name={name}
      render={({ field }) => (
        <label className="inline-flex items-center gap-2">
          <Checkbox checked={field.value} onCheckedChange={(checked) => field.onChange(checked === true)} />
          {label}
        </label>
      )}
    />
  );
}
