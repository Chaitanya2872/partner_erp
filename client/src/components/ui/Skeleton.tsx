import type { HTMLAttributes } from "react";
import { cn } from "@/lib/cn";

/** Decorative placeholder; pair it with a LoadingState or an aria-busy region for screen readers. */
export function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div aria-hidden="true" className={cn("animate-shimmer rounded-md bg-[linear-gradient(90deg,var(--active)_25%,var(--hover)_50%,var(--active)_75%)] bg-[length:200%_100%]", className)} {...props} />;
}

export function Separator({ className, orientation = "horizontal" }: { className?: string; orientation?: "horizontal" | "vertical" }) {
  return (
    <div
      role="separator"
      aria-orientation={orientation}
      className={cn("shrink-0 bg-border", orientation === "horizontal" ? "h-px w-full" : "h-full w-px", className)}
    />
  );
}
