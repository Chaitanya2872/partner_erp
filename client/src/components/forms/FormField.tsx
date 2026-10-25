import { cloneElement, isValidElement, useId, type ReactElement, type ReactNode } from "react";
import { AlertCircle } from "lucide-react";
import { Label } from "@/components/ui/Controls";
import { cn } from "@/lib/cn";

interface FormFieldProps {
  label: string;
  error?: string;
  hint?: ReactNode;
  /** Spans both columns of a FormGrid. */
  wide?: boolean;
  required?: boolean;
  /** Explicit id for the control (lets an ErrorSummary or a label elsewhere point at it). */
  id?: string;
  className?: string;
  /** A single control; it receives id, aria-invalid, aria-required and aria-describedby. */
  children: ReactElement<Record<string, unknown>>;
}

/**
 * Label, control, hint and error in one accessible unit. The error is linked with aria-describedby and announced
 * (role="alert") when it appears; required fields show an asterisk and set aria-required.
 */
export function FormField({ label, error, hint, wide, required, id: idProp, className, children }: FormFieldProps) {
  const generated = useId();
  const id = idProp ?? generated;
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [errorId, hintId].filter(Boolean).join(" ") || undefined;
  const control = isValidElement(children)
    ? cloneElement(children, {
        id,
        "aria-invalid": error ? true : undefined,
        "aria-required": required || undefined,
        "aria-describedby": describedBy,
      })
    : children;
  return (
    <div className={cn("flex min-w-0 flex-col gap-1.5", wide && "sm:col-span-2", className)}>
      <Label htmlFor={id} required={required}>
        {label}
      </Label>
      {control}
      {error ? (
        <p id={errorId} role="alert" className="flex items-start gap-1 text-xs font-medium text-danger">
          <AlertCircle className="mt-px size-3.5 shrink-0" aria-hidden="true" />
          {error}
        </p>
      ) : null}
      {hint ? (
        <p id={hintId} className="text-xs text-muted-foreground">
          {hint}
        </p>
      ) : null}
    </div>
  );
}

/** Two-column grid on tablets and up, one column on phones. */
export function FormGrid({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("grid gap-x-4 gap-y-4 sm:grid-cols-2", className)}>{children}</div>;
}

/** Group of related fields with a heading (rendered as fieldset/legend). */
export function FormSection({ title, description, children, className }: { title: string; description?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <fieldset className={cn("min-w-0 space-y-4 border-b border-border pb-6 last:border-b-0 last:pb-0", className)}>
      <div>
        <legend className="text-sm font-semibold">{title}</legend>
        {description ? <p className="mt-0.5 text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {children}
    </fieldset>
  );
}

/** Form-level error (e.g. a server message that belongs to no single field). */
export function FormError({ children }: { children?: ReactNode }) {
  if (!children) return null;
  return (
    <div role="alert" className="flex gap-2 rounded-lg bg-danger-bg px-3.5 py-3 text-sm text-danger">
      <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <div>{children}</div>
    </div>
  );
}

export interface SummaryItem {
  name: string;
  label: string;
  message: string;
}

/**
 * List of all field errors after a failed submit, for long forms. Each entry moves focus to its field. Use it
 * when a form has more fields than fit on one screen; short forms rely on inline errors only.
 */
export function ErrorSummary({ items, onSelect, title = "Fix the following before saving" }: { items: SummaryItem[]; onSelect: (name: string) => void; title?: string }) {
  if (items.length === 0) return null;
  return (
    <div role="alert" aria-labelledby="error-summary-title" className="rounded-lg border border-danger/40 bg-danger-bg px-4 py-3 text-sm text-danger">
      <p id="error-summary-title" className="font-semibold">
        {title}
      </p>
      <ul className="mt-1.5 list-disc space-y-0.5 pl-5">
        {items.map((item) => (
          <li key={item.name}>
            <button type="button" className="text-left underline underline-offset-2" onClick={() => onSelect(item.name)}>
              {item.label}: {item.message}
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Right-aligned form buttons; stacks with the primary action on top on phones. */
export function FormActions({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end", className)}>{children}</div>;
}
