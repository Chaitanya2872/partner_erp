import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ErrorState, LoadingState } from "@/components/feedback/States";
import { FormField } from "@/components/forms/FormField";
import { CurrencyInput, DateInput, PercentageInput } from "@/components/forms/inputs";
import { ContentCard } from "@/components/layout/Page";
import { DescriptionList, DetailRow } from "@/components/data/DataDisplay";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Input, Textarea } from "@/components/ui/Input";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { ApiRequestError } from "@/services/api";
import { formatCurrency, formatDate, formatDateTime, formatPercent, NOT_AVAILABLE } from "@/utils/format";
import { payoutService } from "../services/payoutService";
import { useTdsConfiguration } from "../hooks/usePayouts";
import type { TdsConfigurationInput, TdsConfigurationVersion } from "../types/payout.types";

type Errors = Record<string, string>;

const empty: TdsConfigurationInput = { sectionCode: "", description: "", ratePercent: "", rateWithoutPanPercent: "", fyThreshold: "", effectiveFrom: "" };

function toInput(v: TdsConfigurationVersion | null): TdsConfigurationInput {
  if (!v) return empty;
  const s = (x: unknown) => (x === null || x === undefined ? "" : String(x));
  return {
    sectionCode: s(v.sectionCode),
    description: s(v.description),
    ratePercent: s(v.ratePercent),
    rateWithoutPanPercent: s(v.rateWithoutPanPercent),
    fyThreshold: s(v.fyThreshold),
    effectiveFrom: s(v.effectiveFrom),
    version: v.status === "DRAFT" ? v.version : undefined,
  };
}

/**
 * TDS on partner commission. No rate, threshold or section is built in: Finance enters them as a draft (Settings
 * write) and someone else confirms it (Payouts approve) with a note of who confirmed it and on what basis. Confirmed
 * versions never change; a new rate is a new version from a later date. Until one is confirmed, payouts can be
 * previewed and drafted but not approved or paid.
 */
export function TdsSettingsCard() {
  const config = useTdsConfiguration();
  const [editing, setEditing] = useState(false);

  if (config.isPending) {
    return (
      <ContentCard title="TDS on partner commission">
        <LoadingState label="Loading TDS settings…" />
      </ContentCard>
    );
  }
  if (config.isError) {
    return (
      <ContentCard title="TDS on partner commission">
        <ErrorState error={config.error} onRetry={() => config.refetch()} />
      </ContentCard>
    );
  }
  const c = config.data;
  return (
    <div id="tds" className="scroll-mt-20">
      <ContentCard
        title="TDS on partner commission"
        description="Entered and confirmed by Finance. Nothing is pre-filled; the system never assumes a rate or threshold."
        action={
          c.canEdit && !editing ? (
            <Button size="sm" onClick={() => setEditing(true)}>
              {c.draft ? "Edit draft" : "New version"}
            </Button>
          ) : undefined
        }
      >
        <div className="space-y-5">
          {!c.configured ? (
            <Alert tone="warning" title="Not confirmed">
              No confirmed TDS settings are in force. Payouts can be previewed and drafted, but not approved or paid.
            </Alert>
          ) : null}
          {c.inForce ? (
            <section aria-label="In force">
              <h3 className="mb-2 text-sm font-semibold">In force</h3>
              <VersionDetails v={c.inForce} />
            </section>
          ) : null}
          {editing ? (
            <DraftForm initial={toInput(c.draft ?? c.inForce)} draftVersion={c.draft?.version} onDone={() => setEditing(false)} />
          ) : c.draft ? (
            <section aria-label="Draft" className="rounded-md border border-border p-3">
              <h3 className="mb-2 text-sm font-semibold">Draft waiting for confirmation</h3>
              <VersionDetails v={c.draft} />
              {!c.draft.complete ? <p className="mt-2 text-xs text-warning-ink">Incomplete: enter the section, rate, threshold and effective date.</p> : null}
              {c.canConfirm && c.draft.complete ? <ConfirmForm version={c.draft.version} /> : null}
            </section>
          ) : null}
          {c.history.length > 0 ? (
            <details className="text-sm">
              <summary className="cursor-pointer font-medium">All versions ({c.history.length})</summary>
              <ul className="mt-2 space-y-2">
                {c.history.map((h) => (
                  <li key={h.id} className="text-xs text-muted-foreground">
                    From {h.effectiveFrom ? formatDate(h.effectiveFrom) : NOT_AVAILABLE}: {h.sectionCode} · {h.ratePercent !== null ? formatPercent(h.ratePercent) : NOT_AVAILABLE} · threshold{" "}
                    {h.fyThreshold !== null ? formatCurrency(h.fyThreshold) : NOT_AVAILABLE}
                    {h.confirmedByName ? ` · confirmed by ${h.confirmedByName}` : ""}
                  </li>
                ))}
              </ul>
            </details>
          ) : null}
        </div>
      </ContentCard>
    </div>
  );
}

function VersionDetails({ v }: { v: TdsConfigurationVersion }) {
  return (
    <DescriptionList columns={2}>
      <DetailRow label="Section">{v.sectionCode ?? NOT_AVAILABLE}</DetailRow>
      <DetailRow label="Effective from">{v.effectiveFrom ? formatDate(v.effectiveFrom) : NOT_AVAILABLE}</DetailRow>
      <DetailRow label="Rate (PAN verified)">{v.ratePercent !== null ? formatPercent(v.ratePercent) : NOT_AVAILABLE}</DetailRow>
      <DetailRow label="Rate without a verified PAN">{v.rateWithoutPanPercent !== null ? formatPercent(v.rateWithoutPanPercent) : "Not set — payouts are blocked"}</DetailRow>
      <DetailRow label="Financial-year threshold">{v.fyThreshold !== null ? formatCurrency(v.fyThreshold) : NOT_AVAILABLE}</DetailRow>
      <DetailRow label={v.status === "CONFIRMED" ? "Confirmed" : "Last edited"}>
        {v.status === "CONFIRMED"
          ? `${v.confirmedAt ? formatDateTime(v.confirmedAt) : NOT_AVAILABLE}${v.confirmedByName ? ` · ${v.confirmedByName}` : ""}`
          : `${v.updatedAt ? formatDateTime(v.updatedAt) : NOT_AVAILABLE}${v.editedByName ? ` · ${v.editedByName}` : ""}`}
      </DetailRow>
      {v.description ? <DetailRow label="Description">{v.description}</DetailRow> : null}
      {v.confirmationNote ? <DetailRow label="Confirmation note">{v.confirmationNote}</DetailRow> : null}
    </DescriptionList>
  );
}

function DraftForm({ initial, draftVersion, onDone }: { initial: TdsConfigurationInput; draftVersion?: number; onDone: () => void }) {
  const queryClient = useQueryClient();
  const [v, setV] = useState<TdsConfigurationInput>({ ...initial, version: draftVersion });
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const set = (field: keyof TdsConfigurationInput) => (value: string) => {
    setV((prev) => ({ ...prev, [field]: value }));
    setErrors({});
  };
  const save = async () => {
    setSaving(true);
    try {
      await payoutService.saveTdsDraft(v);
      toast.success("TDS draft saved. Someone else must confirm it.");
      await queryClient.invalidateQueries({ queryKey: queryKeys.payouts.all });
      onDone();
    } catch (error) {
      if (error instanceof ApiRequestError && error.fieldErrors.length > 0) setErrors(Object.fromEntries(error.fieldErrors.map((f) => [f.field, f.message])));
      else setErrors({ form: errorMessage(error) });
    } finally {
      setSaving(false);
    }
  };
  return (
    <section aria-label="Edit draft" className="space-y-4 rounded-md border border-border p-3">
      <h3 className="text-sm font-semibold">{draftVersion !== undefined ? "Edit draft" : "New draft version"}</h3>
      <p className="text-xs text-muted-foreground">Enter the values Finance has confirmed apply. A draft may be incomplete; it can only be confirmed once complete.</p>
      {errors.form ? <Alert tone="danger">{errors.form}</Alert> : null}
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField label="Section" error={errors.sectionCode} hint="As Finance records it.">
          <Input value={v.sectionCode} maxLength={20} onChange={(e) => set("sectionCode")(e.target.value)} />
        </FormField>
        <FormField label="Effective from" error={errors.effectiveFrom}>
          <DateInput value={v.effectiveFrom} onChange={(e) => set("effectiveFrom")(e.target.value)} />
        </FormField>
        <FormField label="Rate with a verified PAN" error={errors.ratePercent}>
          <PercentageInput value={v.ratePercent} scale={3} onValueChange={set("ratePercent")} />
        </FormField>
        <FormField label="Rate without a verified PAN" error={errors.rateWithoutPanPercent} hint="Leave empty to block payouts to partners without a verified PAN.">
          <PercentageInput value={v.rateWithoutPanPercent} scale={3} onValueChange={set("rateWithoutPanPercent")} />
        </FormField>
        <FormField label="Financial-year threshold" error={errors.fyThreshold} hint="TDS applies once a partner's commission in the year exceeds this.">
          <CurrencyInput value={v.fyThreshold} onValueChange={set("fyThreshold")} />
        </FormField>
        <FormField label="Description" error={errors.description}>
          <Input value={v.description} maxLength={200} onChange={(e) => set("description")(e.target.value)} />
        </FormField>
      </div>
      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button onClick={onDone} disabled={saving}>
          Cancel
        </Button>
        <Button variant="primary" onClick={save} loading={saving} loadingText="Saving…">
          Save draft
        </Button>
      </div>
    </section>
  );
}

function ConfirmForm({ version }: { version: number }) {
  const queryClient = useQueryClient();
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const confirm = async () => {
    if (!note.trim()) return setError("Record Finance's confirmation (who confirmed it and on what basis).");
    setSaving(true);
    try {
      await payoutService.confirmTds(version, note.trim());
      toast.success("TDS settings confirmed.");
      await queryClient.invalidateQueries({ queryKey: queryKeys.payouts.all });
    } catch (e) {
      if (e instanceof ApiRequestError && e.fieldErrors.length > 0) setError(e.fieldErrors[0].message);
      else {
        setError(errorMessage(e));
        void queryClient.invalidateQueries({ queryKey: queryKeys.payouts.tds });
      }
    } finally {
      setSaving(false);
    }
  };
  return (
    <div className="mt-3 space-y-2 border-t border-border pt-3">
      <FormField label="Confirmation note" required error={error ?? undefined} hint="Who in Finance confirmed these values and on what basis. Confirmed versions can't be changed.">
        <Textarea
          rows={2}
          value={note}
          onChange={(e) => {
            setNote(e.target.value);
            setError(null);
          }}
        />
      </FormField>
      <div className="flex justify-end">
        <Button variant="primary" onClick={confirm} loading={saving} loadingText="Confirming…">
          Confirm TDS settings
        </Button>
      </div>
    </div>
  );
}
