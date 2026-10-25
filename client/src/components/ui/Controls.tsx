import { forwardRef, type ComponentPropsWithoutRef, type ComponentRef, type LabelHTMLAttributes } from "react";
import { RadioGroup as RadixRadio, Switch as RadixSwitch } from "radix-ui";
import { cn } from "@/lib/cn";

/** Field label. The required marker sits outside the <label> so the accessible name stays exactly `children`. */
export function Label({ className, required, children, ...props }: LabelHTMLAttributes<HTMLLabelElement> & { required?: boolean }) {
  const label = (
    <label className={cn("text-xs font-semibold text-muted-foreground", className)} {...props}>
      {children}
    </label>
  );
  if (!required) return label;
  return (
    <span className="inline-flex items-baseline gap-0.5">
      {label}
      <span className="text-xs font-semibold text-danger" aria-hidden="true">
        *
      </span>
    </span>
  );
}

export const Switch = forwardRef<ComponentRef<typeof RadixSwitch.Root>, ComponentPropsWithoutRef<typeof RadixSwitch.Root>>(
  function Switch({ className, ...props }, ref) {
    return (
      <RadixSwitch.Root
        ref={ref}
        className={cn(
          "inline-flex h-5 w-9 shrink-0 items-center rounded-full border border-transparent bg-border-strong transition-colors",
          "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring",
          "data-[state=checked]:bg-primary disabled:cursor-not-allowed disabled:opacity-50",
          className,
        )}
        {...props}
      >
        <RadixSwitch.Thumb className="pointer-events-none block size-4 translate-x-0.5 rounded-full bg-surface shadow-sm transition-transform data-[state=checked]:translate-x-[18px]" />
      </RadixSwitch.Root>
    );
  },
);

export const RadioGroup = forwardRef<ComponentRef<typeof RadixRadio.Root>, ComponentPropsWithoutRef<typeof RadixRadio.Root>>(
  function RadioGroup({ className, ...props }, ref) {
    return <RadixRadio.Root ref={ref} className={cn("grid gap-2", className)} {...props} />;
  },
);

export const RadioGroupItem = forwardRef<ComponentRef<typeof RadixRadio.Item>, ComponentPropsWithoutRef<typeof RadixRadio.Item>>(
  function RadioGroupItem({ className, ...props }, ref) {
    return (
      <RadixRadio.Item
        ref={ref}
        className={cn(
          "grid size-[18px] shrink-0 place-content-center rounded-full border border-input bg-surface shadow-xs",
          "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring",
          "data-[state=checked]:border-primary disabled:cursor-not-allowed disabled:opacity-50",
          className,
        )}
        {...props}
      >
        <RadixRadio.Indicator className="block size-2.5 rounded-full bg-primary" />
      </RadixRadio.Item>
    );
  },
);
