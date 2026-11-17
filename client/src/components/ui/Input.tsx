import {
  forwardRef,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from "react";
import { ChevronDown } from "lucide-react";
import { cn } from "@/lib/cn";

/** Shared look of every text-like control; the invalid state comes from aria-invalid (set by FormField). */
export const controlClass =
  "w-full min-w-0 rounded-md border border-border-strong bg-surface px-3 text-sm text-foreground " +
  "transition-[border-color,box-shadow,background-color] duration-150 ease-out-soft " +
  "placeholder:text-muted-foreground hover:border-input " +
  "focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/20 " +
  "disabled:cursor-not-allowed disabled:bg-surface-sunken disabled:opacity-70 read-only:bg-surface-muted " +
  "aria-invalid:border-danger aria-invalid:focus-visible:ring-danger/20";

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function Input({ className, ...props }, ref) {
  return <input ref={ref} className={cn(controlClass, "h-9 py-1.5", className)} {...props} />;
});

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(function Textarea(
  { className, rows = 3, ...props },
  ref,
) {
  return <textarea ref={ref} rows={rows} className={cn(controlClass, "min-h-16 py-2 leading-normal", className)} {...props} />;
});

/**
 * Native select, styled. Native controls keep the platform picker on phones and full keyboard and screen-reader
 * support; use SearchableSelect for long option lists.
 */
export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement> & { wrapperClassName?: string }>(
  function Select({ className, wrapperClassName, ...props }, ref) {
    return (
      <div className={cn("relative min-w-0", wrapperClassName)}>
        <select ref={ref} className={cn(controlClass, "h-9 appearance-none py-1.5 pr-8", className)} {...props} />
        <ChevronDown className="pointer-events-none absolute right-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
      </div>
    );
  },
);

/** Input with fixed leading or trailing text (₹, %, units). The adornments are decorative; the label carries meaning. */
export const AdornedInput = forwardRef<
  HTMLInputElement,
  Omit<InputHTMLAttributes<HTMLInputElement>, "prefix"> & { prefix?: ReactNode; suffix?: ReactNode }
>(function AdornedInput({ prefix, suffix, className, ...props }, ref) {
  return (
    <div className="relative flex min-w-0 items-center">
      {prefix ? (
        <span className="pointer-events-none absolute left-3 text-sm text-muted-foreground" aria-hidden="true">
          {prefix}
        </span>
      ) : null}
      <Input ref={ref} className={cn(prefix ? "pl-7" : undefined, suffix ? "pr-8" : undefined, className)} {...props} />
      {suffix ? (
        <span className="pointer-events-none absolute right-3 text-sm text-muted-foreground" aria-hidden="true">
          {suffix}
        </span>
      ) : null}
    </div>
  );
});
