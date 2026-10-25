import type { ReactNode } from "react";
import { cn } from "@/lib/cn";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";

/** Label/value pairs for detail screens. Renders a semantic <dl>; stacks on phones, two columns from `sm`. */
export function DescriptionList({ children, className, columns = 1 }: { children: ReactNode; className?: string; columns?: 1 | 2 }) {
  return <dl className={cn("grid gap-x-6", columns === 2 && "sm:grid-cols-2", className)}>{children}</dl>;
}

export function DetailRow({ label, children, mono, className }: { label: ReactNode; children: ReactNode; mono?: boolean; className?: string }) {
  return (
    <div className={cn("grid gap-0.5 border-b border-border py-2.5 last:border-b-0 sm:grid-cols-[minmax(8rem,40%)_1fr] sm:gap-3", className)}>
      <dt className="text-xs font-semibold text-muted-foreground sm:text-sm sm:font-medium">{label}</dt>
      <dd className={cn("min-w-0 break-words text-sm", mono && "font-mono text-[0.8125rem] tabular")}>{children}</dd>
    </div>
  );
}

/** A titled card of DetailRows, e.g. "Billing" or "Partner details" in later phases. */
export function KeyValueCard({ title, items, action }: { title: string; items: { label: string; value: ReactNode; mono?: boolean }[]; action?: ReactNode }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        {action}
      </CardHeader>
      <CardContent className="pt-2 sm:pt-2">
        <DescriptionList>
          {items.map((item) => (
            <DetailRow key={item.label} label={item.label} mono={item.mono}>
              {item.value}
            </DetailRow>
          ))}
        </DescriptionList>
      </CardContent>
    </Card>
  );
}

export interface TimelineEntry {
  id: string;
  title: ReactNode;
  /** ISO timestamp or preformatted text. */
  time: ReactNode;
  description?: ReactNode;
  tone?: "neutral" | "success" | "warning" | "danger" | "info";
}

const TIMELINE_DOT: Record<NonNullable<TimelineEntry["tone"]>, string> = {
  neutral: "bg-border-strong",
  success: "bg-success",
  warning: "bg-warning",
  danger: "bg-danger",
  info: "bg-info",
};

/** Vertical history (lead stage history, audit trail of a record). Ordered list for screen readers. */
export function Timeline({ entries, empty = "No history yet." }: { entries: TimelineEntry[]; empty?: ReactNode }) {
  if (entries.length === 0) return <p className="text-sm text-muted-foreground">{empty}</p>;
  return (
    <ol className="relative ml-1.5 border-l border-border">
      {entries.map((entry) => (
        <li key={entry.id} className="relative pb-4 pl-5 last:pb-0">
          <span
            aria-hidden="true"
            className={cn("absolute -left-[5px] top-1.5 size-2.5 rounded-full ring-4 ring-surface", TIMELINE_DOT[entry.tone ?? "neutral"])}
          />
          <div className="text-sm font-medium">{entry.title}</div>
          <div className="text-xs text-muted-foreground">{entry.time}</div>
          {entry.description ? <div className="mt-1 text-sm text-muted-foreground">{entry.description}</div> : null}
        </li>
      ))}
    </ol>
  );
}
