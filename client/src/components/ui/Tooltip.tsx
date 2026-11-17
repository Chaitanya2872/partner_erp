import type { ReactNode } from "react";
import { Tooltip as RadixTooltip } from "radix-ui";
import { cn } from "@/lib/cn";

export const TooltipProvider = RadixTooltip.Provider;

interface TooltipProps {
  content: ReactNode;
  children: ReactNode;
  side?: "top" | "right" | "bottom" | "left";
  /** When false the trigger renders without a tooltip (e.g. sidebar labels that are already visible). */
  enabled?: boolean;
  className?: string;
}

/** Hover and focus tooltip (Esc dismisses). Supplementary only: the trigger must already have an accessible name. */
export function Tooltip({ content, children, side = "top", enabled = true, className }: TooltipProps) {
  return (
    <RadixTooltip.Root open={enabled ? undefined : false}>
      <RadixTooltip.Trigger asChild>{children}</RadixTooltip.Trigger>
      <RadixTooltip.Portal>
        <RadixTooltip.Content
          side={side}
          sideOffset={6}
          className={cn(
            "z-[60] max-w-xs rounded-md bg-primary px-2.5 py-1.5 text-xs font-medium text-primary-foreground shadow-md origin-[var(--radix-tooltip-content-transform-origin)] data-[state=delayed-open]:animate-pop-in data-[state=instant-open]:animate-pop-in data-[state=closed]:animate-pop-out",
            className,
          )}
        >
          {content}
          <RadixTooltip.Arrow className="fill-primary" />
        </RadixTooltip.Content>
      </RadixTooltip.Portal>
    </RadixTooltip.Root>
  );
}
