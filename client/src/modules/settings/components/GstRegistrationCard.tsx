import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";
import { ContentCard } from "@/components/layout/Page";
import { ErrorState, LoadingState } from "@/components/feedback/States";
import { DescriptionList, DetailRow } from "@/components/data/DataDisplay";
import { FormActions, FormError, FormField, FormGrid } from "@/components/forms/FormField";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Input, Select, Textarea } from "@/components/ui/Input";
import { applyServerErrors } from "@/lib/validation";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { ApiRequestError } from "@/services/api";
import { NOT_AVAILABLE } from "@/utils/format";
import { settingsService } from "../services/settingsService";
import { useGstRegistration } from "../hooks/useGstRegistration";
import type { GstRegistration } from "@/modules/invoices/types/invoice.types";

const GSTIN = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9A-Z]{3}$/;
const schema = z
  .object({
    legalName: z.string().trim().min(1, "Enter the registered legal name.").max(200, "Use at most 200 characters."),
    address: z.string().trim().min(1, "Enter the registered address of the place of business.").max(1000, "Use at most 1000 characters."),
    stateCode: z.string().min(1, "Choose the state of the GST registration."),
    gstin: z
      .string()
      .trim()
      .min(1, "Enter the GSTIN.")
      .refine((v) => GSTIN.test(v.toUpperCase()), "Enter a 15-character GSTIN, e.g. 36AABCI1234A1ZC."),
    einvoicing: z.enum(["", "yes", "no"]).refine((v) => v !== "", "Say whether e-invoicing applies to the company."),
  })
  // The server also checks the GSTIN's check character.
  .refine((v) => !v.stateCode || !GSTIN.test(v.gstin.toUpperCase()) || v.gstin.toUpperCase().startsWith(v.stateCode), {
    path: ["gstin"],
    message: "The GSTIN's first two digits must match the registered state.",
  });
type Values = z.input<typeof schema>;
const FIELDS = ["legalName", "address", "stateCode", "gstin"] as const;

/**
 * Supplier GST registration (Phase 9 decision 1). Invoices copy these details when issued, so a later change never
 * alters an issued invoice. Settings write edits; Settings read or Invoices read views.
 */
export function GstRegistrationCard() {
  const { canWrite } = useAuth();
  const registration = useGstRegistration();
  const [editing, setEditing] = useState(false);
  const editable = canWrite("SETTINGS");

  if (registration.isPending) return <ContentCard title="GST registration"><LoadingState label="Loading GST registration…" /></ContentCard>;
  if (registration.isError) {
    return (
      <ContentCard title="GST registration">
        <ErrorState error={registration.error} onRetry={() => registration.refetch()} />
      </ContentCard>
    );
  }
  const r = registration.data;
  return (
    <ContentCard
      title="GST registration"
      description="Printed as the supplier on every tax invoice and used to decide CGST + SGST/UTGST or IGST."
      action={
        editable && !editing ? (
          <Button size="sm" onClick={() => setEditing(true)}>
            Edit
          </Button>
        ) : undefined
      }
    >
      {!r.complete ? (
        <Alert tone="warning" title="Incomplete">
          Invoices can&apos;t be issued until the legal name, address, registered state, GSTIN and the e-invoicing declaration are set.
        </Alert>
      ) : null}
      {r.einvoicingApplicable ? (
        <Alert tone="danger" title="E-invoicing applies: issuing is blocked">
          This system can&apos;t generate e-invoices (IRN and QR code), so invoices can&apos;t be issued here while e-invoicing applies to the company.
        </Alert>
      ) : null}
      {editing ? (
        <EditForm registration={r} onDone={() => setEditing(false)} />
      ) : (
        <DescriptionList columns={2}>
          <DetailRow label="Legal name">{r.legalName ?? NOT_AVAILABLE}</DetailRow>
          <DetailRow label="GSTIN" mono>
            {r.gstin ?? NOT_AVAILABLE}
          </DetailRow>
          <DetailRow label="Registered state">{r.stateCode ? `${r.stateCode} · ${r.stateName}` : NOT_AVAILABLE}</DetailRow>
          <DetailRow label="Address">
            <span className="whitespace-pre-line">{r.address ?? NOT_AVAILABLE}</span>
          </DetailRow>
          <DetailRow label="E-invoicing (IRN/QR)">{r.einvoicingApplicable === null ? "Not declared" : r.einvoicingApplicable ? "Applies" : "Does not apply"}</DetailRow>
        </DescriptionList>
      )}
    </ContentCard>
  );
}

function EditForm({ registration: r, onDone }: { registration: GstRegistration; onDone: () => void }) {
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      legalName: r.legalName ?? "",
      address: r.address ?? "",
      stateCode: r.stateCode ?? "",
      gstin: r.gstin ?? "",
      einvoicing: r.einvoicingApplicable === null ? "" : r.einvoicingApplicable ? "yes" : "no",
    },
  });

  const submit = handleSubmit(async (v) => {
    setFormError(null);
    try {
      await settingsService.updateGstRegistration({
        legalName: v.legalName.trim(),
        address: v.address.trim(),
        stateCode: v.stateCode,
        gstin: v.gstin.trim().toUpperCase(),
        einvoicingApplicable: v.einvoicing === "yes",
        version: r.version,
      });
      toast.success("GST registration saved.");
      await Promise.all([queryClient.invalidateQueries({ queryKey: queryKeys.settings.gstRegistration }), queryClient.invalidateQueries({ queryKey: queryKeys.invoices.all })]);
      onDone();
    } catch (error) {
      const declaration = error instanceof ApiRequestError ? error.fieldErrors.find((f) => f.field === "einvoicingApplicable") : undefined;
      if (declaration) setError("einvoicing", { type: "server", message: declaration.message });
      const unmatched = applyServerErrors(error, setError, FIELDS).filter((m) => m !== declaration?.message);
      if (!(error instanceof ApiRequestError) || error.fieldErrors.length === 0) setFormError(errorMessage(error));
      else if (unmatched.length > 0) setFormError(unmatched.join(" "));
    }
  });

  return (
    <form onSubmit={submit} noValidate className="space-y-4" aria-label="Edit GST registration">
      <FormError>{formError}</FormError>
      <FormGrid>
        <FormField label="Legal name" error={errors.legalName?.message} required>
          <Input autoComplete="organization" {...register("legalName")} />
        </FormField>
        <FormField label="GSTIN" error={errors.gstin?.message} required>
          <Input autoComplete="off" className="font-mono uppercase" maxLength={15} {...register("gstin")} />
        </FormField>
        <FormField label="Registered state" error={errors.stateCode?.message} required>
          <Select {...register("stateCode")}>
            <option value="">Choose the state…</option>
            {r.states.map((s) => (
              <option key={s.code} value={s.code}>
                {s.code} · {s.name}
              </option>
            ))}
          </Select>
        </FormField>
        <FormField label="Address" error={errors.address?.message} required wide hint="The registered place of business.">
          <Textarea rows={3} {...register("address")} />
        </FormField>
        <FormField
          label="E-invoicing (IRN/QR) applies to the company"
          error={errors.einvoicing?.message}
          required
          hint="Ask Finance: it depends on aggregate turnover. E-invoicing isn't supported, so issuing is blocked while it applies."
        >
          <Select {...register("einvoicing")}>
            <option value="">Choose…</option>
            <option value="no">No, e-invoicing does not apply</option>
            <option value="yes">Yes, e-invoicing applies</option>
          </Select>
        </FormField>
      </FormGrid>
      <p className="text-xs text-muted-foreground">Issued invoices keep the details they were issued with. The change is recorded in the audit log with the GSTIN masked.</p>
      <FormActions>
        <Button onClick={onDone} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" variant="primary" loading={isSubmitting} loadingText="Saving…">
          Save
        </Button>
      </FormActions>
    </form>
  );
}
