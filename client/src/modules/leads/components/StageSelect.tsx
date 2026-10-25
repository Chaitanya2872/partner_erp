import { Select } from "@/components/ui/Input";
import { isClosed, type LeadOption, type LeadStage } from "../types/lead.types";

interface StageSelectProps {
  stage: LeadStage;
  stages: LeadOption<LeadStage>[];
  /** Accessible name, e.g. "Stage for Rao Residence". */
  label: string;
  canEdit: boolean;
  /** Leads approve: may reopen a Won or Lost lead (D-04). */
  canApprove: boolean;
  busy?: boolean;
  onChange: (stage: LeadStage) => void;
}

/** Inline stage control (source list). Without Leads approve a Won or Lost lead's select is locked. */
export function StageSelect({ stage, stages, label, canEdit, canApprove, busy, onChange }: StageSelectProps) {
  const locked = isClosed(stage) && !canApprove;
  return (
    <Select
      aria-label={label}
      value={stage}
      disabled={!canEdit || locked || busy}
      aria-busy={busy || undefined}
      title={canEdit && locked ? "Reopening a Won or Lost lead needs Leads approve." : undefined}
      wrapperClassName="min-w-36"
      onChange={(e) => onChange(e.target.value as LeadStage)}
    >
      {stages.map((s) => (
        <option key={s.code} value={s.code}>
          {s.label}
        </option>
      ))}
    </Select>
  );
}
