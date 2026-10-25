import { useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "@/components/ui/Button";
import { Input, Select, Textarea } from "@/components/ui/Input";
import { ErrorSummary, FormActions, FormError, FormField, FormGrid, FormSection, type SummaryItem } from "@/components/forms/FormField";
import { CurrencyInput, DateInput, NumberInput } from "@/components/forms/inputs";
import { UnsavedChangesGuard } from "@/components/forms/UnsavedChangesGuard";
import { applyServerErrors, decimalString } from "@/lib/validation";
import { errorMessage } from "@/lib/errors";
import { ApiRequestError } from "@/services/api";
import { formatPercent } from "@/utils/format";
import { isClosed, type Lead, type LeadInput, type LeadMeta, type LeadStage, type ProjectType } from "../types/lead.types";

/* Mirrors the server rules in LeadService.normalize; the server stays authoritative. */
const optional = (max: number) => z.string().trim().max(max, `Use at most ${max} characters.`);

const schema = z.object({
  clientName: z.string().trim().min(1, "Client name is required.").max(200, "Use at most 200 characters."),
  clientPhone: optional(30).refine((v) => v === "" || (/^[0-9+()\-\s]+$/.test(v) && /^(\D*\d){7,15}\D*$/.test(v)), "Enter a valid phone number."),
  city: optional(150),
  partnerId: z.string().min(1, "Choose the referring partner."),
  projectType: z.string().min(1, "Choose a project type."),
  units: z.string().refine((v) => v === "" || /^\d{1,9}$/.test(v), "Enter a whole number of units."),
  budget: decimalString({ required: false, max: "999999999999.99", label: "Budget" }),
  stage: z.string(),
  stageReason: optional(500),
  presalesOwner: optional(150),
  expectedClose: z.string(),
  source: optional(200),
  notes: optional(2000),
});

type FormValues = z.input<typeof schema>;
type ParsedValues = z.output<typeof schema>;

const FIELDS = [
  "clientName", "clientPhone", "city", "partnerId", "projectType", "units", "budget", "stage", "stageReason", "presalesOwner",
  "expectedClose", "source", "notes",
] as const;

const LABELS: Record<string, string> = {
  clientName: "Client name", clientPhone: "Phone", city: "City", partnerId: "Referring partner", projectType: "Project type",
  units: "Units", budget: "Budget", stage: "Stage", stageReason: "Reason for the stage change", presalesOwner: "Presales owner",
  expectedClose: "Expected close", source: "Source", notes: "Notes",
};

interface LeadFormProps {
  meta: LeadMeta;
  lead?: Lead;
  /** Pre-selected partner for a new lead (from a partner's page). */
  defaultPartnerId?: string;
  /** Leads approve: may reopen a Won or Lost lead (D-04). */
  canApprove: boolean;
  onSubmit: (input: LeadInput) => Promise<void>;
  onCancel: () => void;
}

export function LeadForm({ meta, lead, defaultPartnerId, canApprove, onSubmit, onCancel }: LeadFormProps) {
  const editing = !!lead;
  const acknowledged = !!lead?.acknowledgedAt;
  const stageLocked = editing && isClosed(lead.stage) && !canApprove;
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
      clientName: lead?.clientName ?? "",
      clientPhone: lead?.clientPhone ?? "",
      city: lead?.city ?? "",
      partnerId: lead?.partner.id ?? (meta.partners.some((p) => p.id === defaultPartnerId) ? defaultPartnerId : ""),
      projectType: lead?.projectType ?? "VILLA",
      units: lead?.units === null || lead?.units === undefined ? "" : String(lead.units),
      budget: lead?.budget === null || lead?.budget === undefined ? "" : String(lead.budget),
      stage: lead?.stage ?? "NEW",
      stageReason: "",
      presalesOwner: lead?.presalesOwner ?? "",
      expectedClose: lead?.expectedClose ?? "",
      source: lead?.source ?? "",
      notes: lead?.notes ?? "",
    },
  });

  const [partnerId, stage] = useWatch({ control, name: ["partnerId", "stage"] });
  const partner = meta.partners.find((p) => p.id === partnerId);
  const stageChanged = editing && stage !== lead.stage;
  const reopening = editing && isClosed(lead.stage) && stageChanged;

  const submit = handleSubmit(async (v) => {
    setFormError(null);
    const input: LeadInput = {
      clientName: v.clientName,
      clientPhone: v.clientPhone,
      city: v.city,
      partnerId: v.partnerId,
      projectType: v.projectType as ProjectType,
      units: v.units === "" ? null : Number(v.units),
      budget: v.budget === "" ? null : v.budget,
      stage: v.stage as LeadStage,
      stageReason: stageChanged ? v.stageReason : "",
      presalesOwner: v.presalesOwner,
      expectedClose: v.expectedClose === "" ? null : v.expectedClose,
      source: v.source,
      notes: v.notes,
      version: lead?.version,
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
    <form onSubmit={submit} noValidate className="space-y-6" aria-label={editing ? `Edit ${lead.clientName}` : "New lead"}>
      {/* Off while submitting: the page navigates away from inside onSubmit after a successful save. */}
      <UnsavedChangesGuard when={isDirty && !isSubmitting && !isSubmitSuccessful} />
      <FormError>{formError}</FormError>
      {summary.length > 1 ? <ErrorSummary items={summary} onSelect={(name) => setFocus(name as keyof FormValues)} /> : null}

      <FormSection title="Client" description="Who the lead is for. The phone number is checked against other partners' protected leads.">
        <FormGrid>
          <FormField label="Client name" error={errors.clientName?.message} required wide>
            <Input autoComplete="off" {...register("clientName")} />
          </FormField>
          <FormField label="Phone" error={errors.clientPhone?.message}>
            <Input type="tel" inputMode="tel" autoComplete="off" {...register("clientPhone")} />
          </FormField>
          <FormField label="City" error={errors.city?.message}>
            <Input autoComplete="off" {...register("city")} />
          </FormField>
        </FormGrid>
      </FormSection>

      <FormSection title="Registration" description="The referring partner and the deal stage.">
        <FormGrid>
          <FormField
            label="Referring partner"
            error={errors.partnerId?.message}
            required
            wide
            hint={
              acknowledged
                ? `Acknowledged with a ${formatPercent(lead.lockedFeePercent)} locked fee rate, so the partner can't change.`
                : partner
                  ? `${partner.tierName} · ${formatPercent(partner.feePercent)} fee rate, locked when the lead is acknowledged.`
                  : undefined
            }
          >
            {/* Locked fields are shown read-only and left unregistered, so the stored value is submitted. */}
            {acknowledged ? (
              <Input value={lead.partner.name} readOnly />
            ) : (
              <Select {...register("partnerId")}>
                <option value="">Choose a partner…</option>
                {meta.partners.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                    {p.active ? "" : ` (${p.statusLabel.toLowerCase()})`}
                  </option>
                ))}
              </Select>
            )}
          </FormField>
          <FormField label="Project type" error={errors.projectType?.message} required>
            <Select {...register("projectType")}>
              {meta.projectTypes.map((t) => (
                <option key={t.code} value={t.code}>
                  {t.label}
                </option>
              ))}
            </Select>
          </FormField>
          {/* The Controller wraps the field so FormField can label the input itself. */}
          <Controller
            control={control}
            name="units"
            render={({ field }) => (
              <FormField label="Units" error={errors.units?.message}>
                <NumberInput value={field.value} onValueChange={field.onChange} onBlur={field.onBlur} ref={field.ref} />
              </FormField>
            )}
          />
          <Controller
            control={control}
            name="budget"
            render={({ field }) => (
              <FormField label="Budget" error={errors.budget?.message}>
                <CurrencyInput value={field.value} onValueChange={field.onChange} onBlur={field.onBlur} ref={field.ref} />
              </FormField>
            )}
          />
          <FormField
            label="Stage"
            error={errors.stage?.message}
            hint={stageLocked ? "Reopening a Won or Lost lead needs Leads approve." : undefined}
          >
            {stageLocked ? (
              <Input value={lead.stageLabel} readOnly />
            ) : (
              <Select {...register("stage")}>
                {meta.stages.map((s) => (
                  <option key={s.code} value={s.code}>
                    {s.label}
                  </option>
                ))}
              </Select>
            )}
          </FormField>
          {stageChanged ? (
            <FormField
              label={reopening ? "Reason for reopening" : "Reason for the stage change"}
              error={errors.stageReason?.message}
              hint="Optional. Kept in the lead's history."
              wide
            >
              <Input autoComplete="off" {...register("stageReason")} />
            </FormField>
          ) : null}
        </FormGrid>
      </FormSection>

      <FormSection title="Follow-up">
        <FormGrid>
          <FormField label="Presales owner" error={errors.presalesOwner?.message}>
            <Input autoComplete="off" {...register("presalesOwner")} />
          </FormField>
          <FormField label="Expected close" error={errors.expectedClose?.message}>
            <DateInput {...register("expectedClose")} />
          </FormField>
          <FormField label="Source" error={errors.source?.message} wide>
            <Input autoComplete="off" placeholder="Partner referral, builder tie-up…" {...register("source")} />
          </FormField>
          <FormField label="Notes" error={errors.notes?.message} wide>
            <Textarea rows={4} {...register("notes")} />
          </FormField>
        </FormGrid>
      </FormSection>

      <FormActions>
        <Button onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" variant="primary" loading={isSubmitting} loadingText="Saving…">
          {editing ? "Save changes" : "Create lead"}
        </Button>
      </FormActions>
    </form>
  );
}
