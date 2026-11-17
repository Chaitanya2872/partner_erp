import { forwardRef, useState, type InputHTMLAttributes } from "react";
import { Eye, EyeOff } from "lucide-react";
import { AdornedInput, Input } from "@/components/ui/Input";
import { formatCurrency } from "@/utils/format";
import { cn } from "@/lib/cn";
import { sanitizeDecimal } from "@/utils/decimal";

type BaseProps = Omit<InputHTMLAttributes<HTMLInputElement>, "value" | "onChange" | "type" | "prefix">;

interface DecimalInputProps extends BaseProps {
  /** Decimal string ("125000.50"); kept as text so no floating-point conversion happens in the form. */
  value: string;
  onValueChange: (value: string) => void;
  /** Digits after the decimal point (default 2). */
  scale?: number;
}

/**
 * Amount entry in rupees with a ₹ prefix and the Indian-format value read back underneath. Use with RHF's
 * Controller and validate with `decimalString()` from lib/validation. The server recalculates every total.
 */
export const CurrencyInput = forwardRef<HTMLInputElement, DecimalInputProps & { showPreview?: boolean }>(function CurrencyInput(
  { value, onValueChange, scale = 2, showPreview = true, className, ...props },
  ref,
) {
  const preview = value && value !== "." ? formatCurrency(value) : null;
  return (
    <div className="space-y-1">
      <AdornedInput
        ref={ref}
        type="text"
        inputMode="decimal"
        autoComplete="off"
        prefix="₹"
        value={value}
        onChange={(e) => onValueChange(sanitizeDecimal(e.target.value, scale))}
        className={cn("font-mono tabular", className)}
        {...props}
      />
      {showPreview && preview ? (
        <p className="font-mono text-xs text-muted-foreground tabular" aria-hidden="true">
          {preview}
        </p>
      ) : null}
    </div>
  );
});

/** Percentage entry (18 means 18%) with a % suffix; values above `max` are rejected by validation, not clamped. */
export const PercentageInput = forwardRef<HTMLInputElement, DecimalInputProps>(function PercentageInput(
  { value, onValueChange, scale = 2, className, ...props },
  ref,
) {
  return (
    <AdornedInput
      ref={ref}
      type="text"
      inputMode="decimal"
      autoComplete="off"
      suffix="%"
      value={value}
      onChange={(e) => onValueChange(sanitizeDecimal(e.target.value, scale))}
      className={cn("font-mono tabular", className)}
      {...props}
    />
  );
});

/** Whole-number entry (quantities, units). */
export const NumberInput = forwardRef<HTMLInputElement, Omit<DecimalInputProps, "scale"> & { allowNegative?: boolean }>(function NumberInput(
  { value, onValueChange, allowNegative, className, ...props },
  ref,
) {
  return (
    <Input
      ref={ref}
      type="text"
      inputMode="numeric"
      autoComplete="off"
      value={value}
      onChange={(e) => onValueChange(sanitizeDecimal(e.target.value, 0, allowNegative))}
      className={cn("font-mono tabular", className)}
      {...props}
    />
  );
});

/** Calendar date (yyyy-mm-dd, the API's date format) using the platform date picker. */
export const DateInput = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function DateInput(props, ref) {
  return <Input ref={ref} type="date" {...props} />;
});

/** Date filter for toolbars: a visible label ("From", "To") so native mm/dd/yyyy fields are never anonymous. */
export const FilterDate = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { label: string }>(function FilterDate(
  { label, className, ...props },
  ref,
) {
  return (
    <label className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
      <span className="w-9 shrink-0 sm:w-auto">{label}</span>
      <DateInput ref={ref} className={cn("min-w-0 flex-1 text-foreground sm:w-40 sm:flex-none", className)} {...props} />
    </label>
  );
});

/** Password field with a show/hide toggle. */
export const PasswordInput = forwardRef<HTMLInputElement, Omit<InputHTMLAttributes<HTMLInputElement>, "type">>(function PasswordInput(
  { className, ...props },
  ref,
) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="relative">
      <Input ref={ref} type={visible ? "text" : "password"} className={cn("pr-10", className)} {...props} />
      <button
        type="button"
        onClick={() => setVisible((v) => !v)}
        aria-label={visible ? "Hide password" : "Show password"}
        aria-pressed={visible}
        className="absolute right-1 top-1/2 inline-flex size-8 -translate-y-1/2 items-center justify-center rounded-md text-muted-foreground hover:bg-hover hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring"
      >
        {visible ? <EyeOff className="size-4" aria-hidden="true" /> : <Eye className="size-4" aria-hidden="true" />}
      </button>
    </div>
  );
});
