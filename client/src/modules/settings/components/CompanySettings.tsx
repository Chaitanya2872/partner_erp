import { useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ContentCard } from "@/components/layout/Page";
import { ErrorState, LoadingState } from "@/components/feedback/States";
import { DescriptionList, DetailRow } from "@/components/data/DataDisplay";
import { FormActions, FormError, FormField, FormGrid } from "@/components/forms/FormField";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Input, Textarea } from "@/components/ui/Input";
import { ApiRequestError } from "@/services/api";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { formatDateTime, NOT_AVAILABLE } from "@/utils/format";
import { settingsService } from "../services/settingsService";
import { useCompanySettings } from "../hooks/useSettings";
import type { CompanySettings as Company, CompanySettingsInput } from "../types/settings.types";
import type { CompanyKey, FieldDef } from "../lib/companyFields";

function valueOf(c: Company, key: CompanyKey): string {
  const v = c[key];
  return v === null || v === undefined ? "" : String(v);
}

/** The full company request, with this section's edits on top of the current values. */
function companyInput(c: Company, edits: Record<string, string>): CompanySettingsInput {
  const pick = (key: CompanyKey) => (key in edits ? edits[key] : valueOf(c, key));
  const int = (key: CompanyKey) => (pick(key).trim() === "" ? null : Number(pick(key)));
  const text = (key: CompanyKey) => pick(key).trim() || null;
  return {
    phone: text("phone"),
    email: text("email"),
    terms: text("terms"),
    estimateValidityDays: int("estimateValidityDays"),
    invoiceDueDays: int("invoiceDueDays"),
    defaultGstPercent: pick("defaultGstPercent").trim() === "" ? null : pick("defaultGstPercent").trim(),
    acknowledgementHours: int("acknowledgementHours"),
    estimateSlaDays: int("estimateSlaDays"),
    version: c.version,
  };
}

/** Client-side checks mirroring the server's (the server decides). */
function check(field: FieldDef, raw: string, c: Company): string | null {
  const value = raw.trim();
  if (value === "") return field.required ? `${field.label} is required.` : null;
  const max = field.max?.(c);
  if (field.kind === "int") {
    if (!/^\d+$/.test(value)) return "Enter a whole number.";
    const n = Number(value);
    if ((field.min !== undefined && n < field.min) || (max !== undefined && n > max)) return `Enter ${field.min} to ${max}.`;
  } else if (field.kind === "decimal") {
    if (!/^\d+(\.\d{1,3})?$/.test(value)) return "Enter a number with at most 3 decimals.";
    if (Number(value) > (max ?? 100)) return `Enter 0 to ${max ?? 100}.`;
  } else if (field.kind === "email") {
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value)) return "Enter a valid email address.";
  } else if (field.kind === "tel") {
    const digits = value.replace(/\D/g, "").length;
    if (!/^[0-9+()\- ]+$/.test(value) || digits < 6 || digits > 15) return "Enter 6 to 15 digits.";
  } else if (max !== undefined && value.length > max) {
    return `Use at most ${max.toLocaleString("en-IN")} characters.`;
  }
  return null;
}

interface SectionProps {
  title: string;
  description?: ReactNode;
  fields: FieldDef[];
  /** "commercial" saves lead protection (Manage commercial settings); "company" saves the rest (Settings write). */
  target?: "company" | "commercial";
  /** Shown above the values, e.g. what a change affects. */
  note?: ReactNode;
  children?: ReactNode;
}

/**
 * One section of Company Settings: shows the stored values and, for users who may change them, an inline form. Each
 * save sends the whole record with the version the user saw, so a concurrent change is refused instead of overwritten.
 */
export function CompanySection({ title, description, fields, target = "company", note, children }: SectionProps) {
  const settings = useCompanySettings();
  const queryClient = useQueryClient();
  const [edits, setEdits] = useState<Record<string, string> | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  if (settings.isPending) return <ContentCard title={title}><LoadingState label={`Loading ${title.toLowerCase()}…`} /></ContentCard>;
  if (settings.isError) {
    return (
      <ContentCard title={title}>
        <ErrorState error={settings.error} onRetry={() => settings.refetch()} />
      </ContentCard>
    );
  }
  const c = settings.data;
  const editable = target === "commercial" ? c.canManageCommercial : c.canEdit;

  const start = () => {
    setEdits(Object.fromEntries(fields.map((f) => [f.key, valueOf(c, f.key)])));
    setErrors({});
    setFormError(null);
  };

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!edits) return;
    const found: Record<string, string> = {};
    for (const f of fields) {
      const problem = check(f, edits[f.key] ?? "", c);
      if (problem) found[f.key] = problem;
    }
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    setSaving(true);
    setFormError(null);
    try {
      if (target === "commercial") {
        const raw = (edits.leadProtectionDays ?? "").trim();
        await settingsService.updateCommercial(raw === "" ? null : Number(raw), c.version);
      } else {
        await settingsService.updateCompany(companyInput(c, edits));
      }
      toast.success(`${title} saved.`);
      setEdits(null);
      await queryClient.invalidateQueries({ queryKey: queryKeys.settings.all });
    } catch (error) {
      if (error instanceof ApiRequestError && error.fieldErrors.length > 0) {
        const mine = new Set<string>(fields.map((f) => f.key));
        setErrors(Object.fromEntries(error.fieldErrors.filter((fe) => mine.has(fe.field)).map((fe) => [fe.field, fe.message])));
        const other = error.fieldErrors.filter((fe) => !mine.has(fe.field));
        if (other.length > 0) setFormError(other.map((fe) => fe.message).join(" "));
      } else if (error instanceof ApiRequestError && error.status === 409) {
        setFormError("Someone else changed these settings. Cancel to see the current values, then try again.");
        await queryClient.invalidateQueries({ queryKey: queryKeys.settings.all });
      } else {
        setFormError(errorMessage(error));
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <ContentCard
      title={title}
      description={description}
      action={
        editable && !edits ? (
          <Button size="sm" onClick={start} aria-label={`Edit ${title.toLowerCase()}`}>
            Edit
          </Button>
        ) : undefined
      }
    >
      <div className="space-y-4">
        {note}
        {edits ? (
          <form onSubmit={save} noValidate className="space-y-4" aria-label={`Edit ${title.toLowerCase()}`}>
            <FormError>{formError}</FormError>
            <FormGrid>
              {fields.map((f) => (
                <FormField key={f.key} label={f.label} hint={f.hint} error={errors[f.key]} required={f.required} wide={f.kind === "textarea"}>
                  {f.kind === "textarea" ? (
                    <Textarea rows={4} value={edits[f.key] ?? ""} onChange={(e) => setEdits({ ...edits, [f.key]: e.target.value })} />
                  ) : (
                    <Input
                      type={f.kind === "int" || f.kind === "decimal" ? "text" : f.kind}
                      inputMode={f.kind === "int" ? "numeric" : f.kind === "decimal" ? "decimal" : undefined}
                      value={edits[f.key] ?? ""}
                      onChange={(e) => setEdits({ ...edits, [f.key]: e.target.value })}
                    />
                  )}
                </FormField>
              ))}
            </FormGrid>
            <FormActions>
              <Button onClick={() => setEdits(null)} disabled={saving}>
                Cancel
              </Button>
              <Button type="submit" variant="primary" loading={saving} loadingText="Saving…">
                Save
              </Button>
            </FormActions>
          </form>
        ) : (
          <DescriptionList>
            {fields.map((f) => (
              <DetailRow key={f.key} label={f.label}>
                {f.show ? f.show(c) : <span className="whitespace-pre-line">{valueOf(c, f.key) || NOT_AVAILABLE}</span>}
              </DetailRow>
            ))}
          </DescriptionList>
        )}
        {children}
        {c.updatedAt ? (
          <p className="text-xs text-muted-foreground">
            Company settings last changed {formatDateTime(c.updatedAt)}
            {c.updatedBy ? ` by ${c.updatedBy}` : ""}. Every change is recorded in the audit log.
          </p>
        ) : null}
      </div>
    </ContentCard>
  );
}

/** Display name, logo and branding have no approved storage or rendering rule yet (DECISIONS P13-PD1). */
export function BrandingNotice() {
  return (
    <Alert tone="neutral" title="Display name, logo and document branding">
      Documents print the registered legal name and the contact details above. A separate display name, a logo and branding options aren&apos;t
      available yet: they need an approved format, size limit and placement on documents first.
    </Alert>
  );
}

/** Clawback days are stored from the source but not applied (DECISIONS C11-2, L-3). */
export function ClawbackNotice() {
  const settings = useCompanySettings();
  return (
    <Alert tone="neutral" title="Clawback window">
      The source application limited clawbacks to {settings.data?.clawbackDays ?? 90} days. That limit is not applied: commission on a receipt
      reversed after payment is always carried forward as a deduction (pending Finance decision L-3), so it can&apos;t be changed here.
    </Alert>
  );
}
