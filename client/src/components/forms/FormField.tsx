import { useId, type ReactElement, type ReactNode } from "react";
import { cloneElement, isValidElement } from "react";
import { cn } from "@/lib/cn";

interface FormFieldProps {
  label: string;
  error?: string;
  hint?: ReactNode;
  wide?: boolean;
  /** A single input/select/textarea; it receives id, aria-invalid and aria-describedby. */
  children: ReactElement<Record<string, unknown>>;
}

export function FormField({ label, error, hint, wide, children }: FormFieldProps) {
  const id = useId();
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  const control = isValidElement(children)
    ? cloneElement(children, { id, "aria-invalid": error ? true : undefined, "aria-describedby": describedBy })
    : children;
  return (
    <div className={cn("fld", wide && "w2")}>
      <label htmlFor={id}>{label}</label>
      {control}
      {error ? (
        <span id={`${id}-error`} className="ferr" role="alert">
          {error}
        </span>
      ) : hint ? (
        <small id={`${id}-hint`}>{hint}</small>
      ) : null}
    </div>
  );
}
