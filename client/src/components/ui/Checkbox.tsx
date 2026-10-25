import { forwardRef, type ComponentPropsWithoutRef, type ComponentRef } from "react";
import { Checkbox as RadixCheckbox } from "radix-ui";
import { Check, Minus } from "lucide-react";
import { cn } from "@/lib/cn";

/** Accessible checkbox (role="checkbox", Space toggles). Supports the indeterminate state. */
export const Checkbox = forwardRef<ComponentRef<typeof RadixCheckbox.Root>, ComponentPropsWithoutRef<typeof RadixCheckbox.Root>>(
  function Checkbox({ className, ...props }, ref) {
    return (
      <RadixCheckbox.Root
        ref={ref}
        className={cn(
          "peer grid size-[18px] shrink-0 place-content-center rounded-sm border border-input bg-surface shadow-xs transition-colors",
          "hover:border-accent-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring",
          "data-[state=checked]:border-primary data-[state=checked]:bg-primary data-[state=checked]:text-primary-foreground",
          "data-[state=indeterminate]:border-primary data-[state=indeterminate]:bg-primary data-[state=indeterminate]:text-primary-foreground",
          "disabled:cursor-not-allowed disabled:border-border disabled:bg-surface-sunken disabled:opacity-60",
          "disabled:data-[state=checked]:border-muted-foreground disabled:data-[state=checked]:bg-muted-foreground",
          className,
        )}
        {...props}
      >
        <RadixCheckbox.Indicator className="grid place-content-center">
          {props.checked === "indeterminate" ? (
            <Minus className="size-3.5" strokeWidth={3} aria-hidden="true" />
          ) : (
            <Check className="size-3.5" strokeWidth={3} aria-hidden="true" />
          )}
        </RadixCheckbox.Indicator>
      </RadixCheckbox.Root>
    );
  },
);
