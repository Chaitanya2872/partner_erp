import { cn } from "@/lib/cn";
import { initialsOf } from "@/utils/format";

/** Initials avatar (the system stores no profile photos). Decorative: the name is always shown or labelled nearby. */
export function Avatar({ name, className, size = "md" }: { name: string; className?: string; size?: "sm" | "md" }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        "inline-flex shrink-0 items-center justify-center rounded-full bg-accent font-semibold text-accent-foreground",
        size === "sm" ? "size-7 text-2xs" : "size-9 text-xs",
        className,
      )}
    >
      {initialsOf(name)}
    </span>
  );
}
