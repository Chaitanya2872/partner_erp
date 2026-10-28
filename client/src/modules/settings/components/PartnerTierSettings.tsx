import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ContentCard } from "@/components/layout/Page";
import { ErrorState, LoadingState } from "@/components/feedback/States";
import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";
import { FormError } from "@/components/forms/FormField";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { ApiRequestError } from "@/services/api";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { formatCurrency, formatDateTime, formatNumber } from "@/utils/format";
import { settingsService } from "../services/settingsService";
import { usePartnerTiers } from "../hooks/useSettings";
import type { PartnerTier, TierInput } from "../types/settings.types";

type Draft = Record<number, { name: string; commissionPercent: string; minimumRevenue: string; payoutBusinessDays: string }>;
type Field = keyof Draft[number];

const toDraft = (tiers: PartnerTier[]): Draft =>
  Object.fromEntries(
    tiers.map((t) => [
      t.level,
      { name: t.name, commissionPercent: String(Number(t.commissionPercent)), minimumRevenue: String(Number(t.minimumRevenue)), payoutBusinessDays: String(t.payoutBusinessDays) },
    ]),
  );

/** Mirrors the server's checks so most mistakes show before saving; the server decides. */
function validate(tiers: PartnerTier[], draft: Draft): Record<string, string> {
  const errors: Record<string, string> = {};
  const names = new Set<string>();
  let previous: number | null = null;
  for (const t of tiers) {
    const d = draft[t.level];
    const at = (f: Field) => `tiers[${t.level}].${f}`;
    const name = d.name.trim().replace(/\s+/g, " ");
    if (name.length < 2 || name.length > 60) errors[at("name")] = "2 to 60 characters.";
    else if (names.has(name.toLowerCase())) errors[at("name")] = "Another tier has this name.";
    names.add(name.toLowerCase());
    if (!/^\d+(\.\d{1,3})?$/.test(d.commissionPercent.trim()) || Number(d.commissionPercent) > 100) errors[at("commissionPercent")] = "0 to 100, at most 3 decimals.";
    const min = d.minimumRevenue.trim();
    if (!/^\d+(\.\d{1,2})?$/.test(min)) errors[at("minimumRevenue")] = "An amount of at least 0, at most 2 decimals.";
    else if (t.level === 1 && Number(min) !== 0) errors[at("minimumRevenue")] = "The first tier starts at 0 so every partner has a tier.";
    else if (previous !== null && Number(min) <= previous) errors[at("minimumRevenue")] = "Must be higher than the tier below.";
    if (/^\d+(\.\d{1,2})?$/.test(min)) previous = Number(min);
    if (!/^\d+$/.test(d.payoutBusinessDays.trim()) || Number(d.payoutBusinessDays) < 1 || Number(d.payoutBusinessDays) > 365) errors[at("payoutBusinessDays")] = "1 to 365.";
  }
  return errors;
}

/**
 * The four partner tiers (BUSINESS_RULES §2.1). Tiers can't be added or removed. Changing a fee % never recalculates
 * anything already stored: acknowledged leads keep their locked rate, documents keep their fee %, payouts keep their
 * line percentages, and no partner changes tier until a tier review is applied.
 */
export function PartnerTierSettings() {
  const tiers = usePartnerTiers();
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<Draft | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [saving, setSaving] = useState(false);

  if (tiers.isPending) return <ContentCard title="Partner tiers"><LoadingState label="Loading partner tiers…" /></ContentCard>;
  if (tiers.isError) {
    return (
      <ContentCard title="Partner tiers">
        <ErrorState error={tiers.error} onRetry={() => tiers.refetch()} />
      </ContentCard>
    );
  }
  const { tiers: rows, canEdit } = tiers.data;
  const lastChange = rows.filter((t) => t.updatedAt).sort((a, b) => (b.updatedAt ?? "").localeCompare(a.updatedAt ?? ""))[0];

  const review = () => {
    if (!draft) return;
    const found = validate(rows, draft);
    setErrors(found);
    setFormError(Object.keys(found).length > 0 ? "Correct the highlighted values." : null);
    if (Object.keys(found).length === 0) setConfirming(true);
  };

  const save = async () => {
    if (!draft) return;
    setSaving(true);
    try {
      const input: TierInput[] = rows.map((t) => ({
        level: t.level,
        name: draft[t.level].name.trim(),
        commissionPercent: draft[t.level].commissionPercent.trim(),
        minimumRevenue: draft[t.level].minimumRevenue.trim(),
        payoutBusinessDays: Number(draft[t.level].payoutBusinessDays),
        version: t.version,
      }));
      await settingsService.updateTiers(input);
      toast.success("Partner tiers saved. New rates apply from the next acknowledgement.");
      setDraft(null);
      setConfirming(false);
      await queryClient.invalidateQueries({ queryKey: queryKeys.settings.tiers });
      await queryClient.invalidateQueries({ queryKey: queryKeys.partners.all });
    } catch (error) {
      setConfirming(false);
      if (error instanceof ApiRequestError && error.fieldErrors.length > 0) {
        setErrors(Object.fromEntries(error.fieldErrors.map((fe) => [fe.field, fe.message])));
        setFormError(error.fieldErrors.filter((fe) => !/^tiers\[\d]\./.test(fe.field)).map((fe) => fe.message).join(" ") || "Correct the highlighted values.");
      } else if (error instanceof ApiRequestError && error.status === 409) {
        setFormError("Someone else changed the tiers. Cancel to see the current values, then try again.");
        await queryClient.invalidateQueries({ queryKey: queryKeys.settings.tiers });
      } else {
        setFormError(errorMessage(error));
      }
    } finally {
      setSaving(false);
    }
  };

  const cell = (t: PartnerTier, f: Field, label: string, props: { inputMode?: "decimal" | "numeric"; className?: string } = {}) => {
    const key = `tiers[${t.level}].${f}`;
    return (
      <div>
        <Input
          aria-label={`${t.name} ${label}`}
          aria-invalid={errors[key] ? true : undefined}
          aria-describedby={errors[key] ? `${key}-error` : undefined}
          value={draft![t.level][f]}
          inputMode={props.inputMode}
          className={props.className}
          onChange={(e) => setDraft({ ...draft!, [t.level]: { ...draft![t.level], [f]: e.target.value } })}
        />
        {errors[key] ? (
          <p id={`${key}-error`} className="mt-1 text-xs text-danger">
            {errors[key]}
          </p>
        ) : null}
      </div>
    );
  };

  return (
    <ContentCard
      title="Partner tiers"
      description="Referral fee, revenue threshold and payout days of the four fixed tiers."
      action={
        canEdit && !draft ? (
          <Button size="sm" onClick={() => { setDraft(toDraft(rows)); setErrors({}); setFormError(null); }} aria-label="Edit partner tiers">
            Edit
          </Button>
        ) : undefined
      }
    >
      <div className="space-y-4">
        <Alert tone="info" title="Changes apply going forward only">
          A new fee % is locked into leads acknowledged after the change and used on new documents without a locked rate. Acknowledged leads,
          estimates, invoices and payouts keep the rate they were created with. Thresholds are used by the next tier review; partners never
          move tier automatically.
        </Alert>
        <FormError>{formError}</FormError>
        <div className="overflow-x-auto rounded-md border border-border" role="region" aria-label="Partner tiers, scrolls horizontally" tabIndex={0}>
          <table className="w-full min-w-[40rem] text-sm" aria-label="Partner tiers">
            <thead>
              <tr className="border-b border-border bg-surface-sunken text-left text-2xs font-semibold uppercase tracking-wider text-muted-foreground">
                <th scope="col" className="px-3 py-2">Tier</th>
                <th scope="col" className="px-3 py-2 text-right">Fee %</th>
                <th scope="col" className="px-3 py-2 text-right">Min. paid revenue (12 months, ex-GST)</th>
                <th scope="col" className="px-3 py-2 text-right">Payout (business days)</th>
                <th scope="col" className="px-3 py-2 text-right">Partners</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((t) => (
                <tr key={t.level} className="border-b border-border last:border-b-0 align-top">
                  <th scope="row" className="px-3 py-2 text-left font-medium">
                    {draft ? cell(t, "name", "name") : t.name}
                    <div className="text-xs font-normal text-muted-foreground">Level {t.level}</div>
                  </th>
                  <td className="px-3 py-2 text-right tabular">{draft ? cell(t, "commissionPercent", "fee %", { inputMode: "decimal", className: "text-right" }) : `${Number(t.commissionPercent)}%`}</td>
                  <td className="px-3 py-2 text-right tabular">{draft ? cell(t, "minimumRevenue", "minimum revenue", { inputMode: "decimal", className: "text-right" }) : formatCurrency(t.minimumRevenue, { whole: true })}</td>
                  <td className="px-3 py-2 text-right tabular">{draft ? cell(t, "payoutBusinessDays", "payout days", { inputMode: "numeric", className: "text-right" }) : t.payoutBusinessDays}</td>
                  <td className="px-3 py-2 text-right tabular">{formatNumber(t.partners)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="text-xs text-muted-foreground">
          Payout days are recorded with each tier but don&apos;t set payout dates yet (pending decision L-4). Partners excludes inactive partners.
          {lastChange?.updatedAt ? ` Last changed ${formatDateTime(lastChange.updatedAt)}${lastChange.updatedBy ? ` by ${lastChange.updatedBy}` : ""}.` : ""}
        </p>
        {draft ? (
          <div className="flex flex-wrap justify-end gap-2">
            <Button onClick={() => { setDraft(null); setErrors({}); setFormError(null); }} disabled={saving}>
              Cancel
            </Button>
            <Button variant="primary" onClick={review}>
              Save tiers
            </Button>
          </div>
        ) : null}
      </div>
      <ConfirmDialog
        open={confirming}
        title="Change the partner tiers?"
        confirmLabel="Save tiers"
        busy={saving}
        onCancel={() => setConfirming(false)}
        onConfirm={() => void save()}
      >
        <p>The new values apply to leads acknowledged and documents created from now on. Nothing already stored is recalculated.</p>
        <p className="text-xs text-muted-foreground">Each changed tier is recorded in the audit log.</p>
      </ConfirmDialog>
    </ContentCard>
  );
}
