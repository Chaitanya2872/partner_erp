import type { ReactNode } from "react";
import { Link } from "react-router";
import { ArrowDownRight, ArrowRight, ArrowUpRight, BarChart3, Minus, type LucideIcon } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { Skeleton } from "@/components/ui/Skeleton";
import { Alert } from "@/components/ui/Alert";
import { cn } from "@/lib/cn";

/*
 * Dashboard building blocks for Phase 12. They render only what they are given: no sample numbers. When a
 * metric has no backend source yet, pass `unavailable` and say why, rather than inventing a value.
 */

interface MetricCardProps {
  label: string;
  /** Preformatted value (use the format utilities); omitted while loading or when unavailable. */
  value?: ReactNode;
  icon?: LucideIcon;
  hint?: ReactNode;
  trend?: ReactNode;
  loading?: boolean;
  /** Explanation shown instead of a value when no data source exists yet. */
  unavailable?: string;
  href?: string;
}

export function MetricCard({ label, value, icon: Icon, hint, trend, loading, unavailable, href }: MetricCardProps) {
  const body = (
    <>
      <div className="flex items-center justify-between gap-2">
        <span className="text-2xs font-semibold uppercase tracking-wider text-muted-foreground">{label}</span>
        {Icon ? <Icon className="size-4 text-accent-ink" aria-hidden="true" /> : null}
      </div>
      <div className="mt-2 min-h-8">
        {loading ? (
          <Skeleton className="h-7 w-24" />
        ) : unavailable ? (
          <span className="text-sm text-muted-foreground">{unavailable}</span>
        ) : (
          <span className="font-mono text-2xl font-medium tabular">{value}</span>
        )}
      </div>
      {trend || hint ? (
        <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          {trend}
          {hint}
        </div>
      ) : null}
    </>
  );
  const className = "block rounded-lg border border-border bg-surface p-4 shadow-xs";
  return href ? (
    <Link to={href} className={cn(className, "transition-colors hover:border-accent focus-visible:outline-2 focus-visible:outline-ring")}>
      {body}
    </Link>
  ) : (
    <div className={className}>{body}</div>
  );
}

/** Change versus a previous period. Direction is spelled out in text, not only by colour or arrow. */
export function TrendIndicator({ change, label = "vs previous period", positiveIsGood = true }: { change: number; label?: string; positiveIsGood?: boolean }) {
  const direction = change > 0 ? "up" : change < 0 ? "down" : "flat";
  const good = direction === "flat" ? null : (direction === "up") === positiveIsGood;
  const Icon = direction === "up" ? ArrowUpRight : direction === "down" ? ArrowDownRight : Minus;
  return (
    <span className={cn("inline-flex items-center gap-0.5 font-medium", good === null ? "text-muted-foreground" : good ? "text-success" : "text-danger")}>
      <Icon className="size-3.5" aria-hidden="true" />
      <span>
        <span className="sr-only">{direction === "up" ? "Up" : direction === "down" ? "Down" : "No change"} </span>
        {Math.abs(change).toFixed(1)}%
      </span>
      <span className="font-normal text-muted-foreground">{label}</span>
    </span>
  );
}

interface ChartContainerProps {
  title: string;
  description?: ReactNode;
  /** The chart itself (Phase 12 uses Recharts with the --chart-* colour tokens). */
  children?: ReactNode;
  loading?: boolean;
  empty?: boolean;
  /** Explanation when the data source is not built yet. */
  unavailable?: string;
  action?: ReactNode;
  height?: number;
}

/**
 * Frame for charts: title, fixed height (no layout shift), and loading, empty or unavailable states. Charts
 * must use the --chart-1…5 and --chart-grid tokens so they stay legible in both themes, and each chart needs a
 * text summary or table alternative.
 */
export function ChartContainer({ title, description, children, loading, empty, unavailable, action, height = 240 }: ChartContainerProps) {
  return (
    <Card>
      <CardHeader>
        <div className="min-w-0 flex-1 basis-48">
          <CardTitle>{title}</CardTitle>
          {description ? <CardDescription>{description}</CardDescription> : null}
        </div>
        {action}
      </CardHeader>
      <CardContent>
        <div style={{ height }} className="relative">
          {loading ? (
            <Skeleton className="size-full" />
          ) : unavailable || empty ? (
            <div className="flex size-full flex-col items-center justify-center gap-2 rounded-md border border-dashed border-border-strong text-center text-sm text-muted-foreground">
              <BarChart3 className="size-5" aria-hidden="true" />
              <span className="max-w-xs">{unavailable ?? "No data for this period."}</span>
            </div>
          ) : (
            children
          )}
        </div>
      </CardContent>
    </Card>
  );
}

export function DashboardSection({ title, description, action, children }: { title: string; description?: ReactNode; action?: ReactNode; children: ReactNode }) {
  return (
    <section aria-labelledby={`dash-${title}`} className="space-y-3">
      <div className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h2 id={`dash-${title}`} className="text-base font-semibold">
            {title}
          </h2>
          {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
        </div>
        {action}
      </div>
      {children}
    </section>
  );
}

export interface ActivityItem {
  id: string;
  title: ReactNode;
  meta: ReactNode;
  icon?: LucideIcon;
}

export function ActivityList({ items, empty = "Nothing to show yet." }: { items: ActivityItem[]; empty?: ReactNode }) {
  if (items.length === 0) return <p className="py-4 text-sm text-muted-foreground">{empty}</p>;
  return (
    <ul className="divide-y divide-border">
      {items.map(({ id, title, meta, icon: Icon }) => (
        <li key={id} className="flex items-start gap-3 py-2.5">
          {Icon ? (
            <span className="mt-0.5 inline-flex size-7 shrink-0 items-center justify-center rounded-full bg-secondary">
              <Icon className="size-3.5" aria-hidden="true" />
            </span>
          ) : null}
          <div className="min-w-0">
            <div className="text-sm">{title}</div>
            <div className="text-xs text-muted-foreground">{meta}</div>
          </div>
        </li>
      ))}
    </ul>
  );
}

/** Attention item (overdue invoice, expiring estimate) for the Phase 12 alert feed. */
export function AlertCard({ tone, title, children, href }: { tone: "warning" | "danger" | "info"; title: string; children?: ReactNode; href?: string }) {
  return (
    <Alert
      tone={tone}
      title={title}
      action={
        href ? (
          <Link to={href} className="inline-flex items-center gap-1 text-sm font-medium underline-offset-2 hover:underline">
            Open <ArrowRight className="size-3.5" aria-hidden="true" />
          </Link>
        ) : undefined
      }
    >
      {children}
    </Alert>
  );
}

/** Totals panel, e.g. receivables summary: label and value rows with an emphasised last row. */
export function SummaryPanel({ title, rows }: { title: string; rows: { label: string; value: ReactNode; emphasis?: boolean }[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
      </CardHeader>
      <CardContent className="pt-3 sm:pt-3">
        <dl className="space-y-1.5">
          {rows.map((row) => (
            <div key={row.label} className={cn("flex items-baseline justify-between gap-4 text-sm", row.emphasis && "border-t border-border pt-2 font-semibold")}>
              <dt className={cn(!row.emphasis && "text-muted-foreground")}>{row.label}</dt>
              <dd className="font-mono tabular">{row.value}</dd>
            </div>
          ))}
        </dl>
      </CardContent>
    </Card>
  );
}
