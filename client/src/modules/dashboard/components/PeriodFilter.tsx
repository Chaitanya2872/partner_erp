import { DateInput } from "@/components/forms/inputs";
import { Select } from "@/components/ui/Input";
import { formatDate } from "@/utils/format";
import { PERIOD_PRESETS } from "../hooks/usePeriod";
import type { PeriodFilter as Period, PeriodPreset, PeriodView } from "../types/dashboard.types";

interface PeriodFilterProps {
  value: Period;
  onChange: (value: Period) => void;
  /** The range the server resolved, shown so presets are never ambiguous. */
  resolved?: PeriodView;
  idPrefix?: string;
}

/**
 * Date preset and custom range. Presets resolve on the server in Asia/Kolkata (IST); both ends are inclusive and the
 * financial year runs 1 April – 31 March.
 */
export function PeriodFilter({ value, onChange, resolved, idPrefix = "period" }: PeriodFilterProps) {
  const custom = value.preset === "CUSTOM";
  const inverted = custom && !!value.from && !!value.to && value.from > value.to;
  return (
    <div className="flex flex-wrap items-center gap-2">
      <Select
        aria-label="Period"
        value={value.preset}
        wrapperClassName="w-full sm:w-48"
        onChange={(e) => onChange({ ...value, preset: e.target.value as PeriodPreset })}
      >
        {PERIOD_PRESETS.map((p) => (
          <option key={p.value} value={p.value}>
            {p.label}
          </option>
        ))}
      </Select>
      {custom ? (
        <>
          <DateInput
            id={`${idPrefix}-from`}
            aria-label="From date"
            value={value.from}
            max={value.to || undefined}
            min="2000-01-01"
            onChange={(e) => onChange({ ...value, from: e.target.value })}
            className="sm:w-40"
            aria-invalid={inverted || undefined}
          />
          <DateInput
            id={`${idPrefix}-to`}
            aria-label="To date"
            value={value.to}
            min={value.from || "2000-01-01"}
            onChange={(e) => onChange({ ...value, to: e.target.value })}
            className="sm:w-40"
            aria-invalid={inverted || undefined}
          />
        </>
      ) : null}
      <p className="w-full text-xs text-muted-foreground sm:w-auto" aria-live="polite">
        {inverted
          ? "The start date must be on or before the end date."
          : custom && (!value.from || !value.to)
            ? "Choose both dates (up to three years)."
            : resolved
              ? `${formatDate(resolved.from)} – ${formatDate(resolved.to)} · IST`
              : null}
      </p>
    </div>
  );
}
