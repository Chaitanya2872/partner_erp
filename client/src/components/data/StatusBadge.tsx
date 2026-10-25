import { Badge } from "@/components/ui/Badge";
import { statusDefinition, type StatusDomain } from "@/lib/status";
import { cn } from "@/lib/cn";

const DOT: Record<string, string> = {
  neutral: "bg-muted-foreground",
  success: "bg-success",
  warning: "bg-warning",
  danger: "bg-danger",
  info: "bg-info",
  accent: "bg-accent-foreground",
  outline: "border border-muted-foreground bg-transparent",
};

interface StatusBadgeProps {
  domain: StatusDomain;
  /** Backend status code (stored or derived). */
  status: string;
  className?: string;
  /** Extra text appended to the label (e.g. "password reset pending"). */
  suffix?: string;
}

/**
 * Consistent status pill for every module. The text label always carries the meaning (colour is reinforcement
 * only), and derived statuses are marked for assistive technology.
 */
export function StatusBadge({ domain, status, className, suffix }: StatusBadgeProps) {
  const def = statusDefinition(domain, status);
  return (
    <Badge tone={def.tone} className={className} title={def.hint} data-status={status}>
      <span aria-hidden="true" className={cn("inline-block size-1.5 rounded-full", DOT[def.tone])} />
      {def.label}
      {suffix ? <span className="font-medium"> · {suffix}</span> : null}
      {def.derived ? <span className="sr-only"> (calculated)</span> : null}
    </Badge>
  );
}
