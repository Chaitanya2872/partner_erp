import type { ReactNode } from "react";
import { Link } from "react-router";
import { AlertTriangle, ArrowDownRight, ArrowRight, ArrowUpRight, BarChart3, Info, Minus, XCircle, type LucideIcon } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { Skeleton } from "@/components/ui/Skeleton";
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
      <div className="flex items-start justify-between gap-3">
        <span className="pt-1 text-xs font-medium leading-snug text-muted-foreground">{label}</span>
        {Icon ? (
          <span className="inline-flex size-8 shrink-0 items-center justify-center rounded-lg bg-secondary text-accent-ink transition-colors duration-150 group-hover/metric:bg-accent group-hover/metric:text-accent-foreground">
            <Icon className="size-4" aria-hidden="true" />
          </span>
        ) : null}
      </div>
      <div className="mt-3 min-h-9">
        {loading ? (
          <Skeleton className="h-8 w-28" />
        ) : unavailable ? (
          <span className="text-sm text-muted-foreground">{unavailable}</span>
        ) : (
          <span className="block truncate font-mono text-2xl font-medium tracking-tight tabular">{value}</span>
        )}
      </div>
      {/* Footer is pinned to the bottom so trend and hint line up across a row of cards of different heights. */}
      {trend || hint ? (
        <div className="mt-auto space-y-1 border-t border-border/70 pt-3 text-xs text-muted-foreground">
          {trend ? <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5">{trend}</div> : null}
          {hint ? <div className="leading-snug">{hint}</div> : null}
        </div>
      ) : null}
    </>
  );
  const className = "group/metric flex h-full min-w-0 flex-col gap-0 rounded-xl border border-border bg-surface p-4";
  return href ? (
    <Link to={href} className={cn(className, "transition-[border-color,transform] duration-150 hover:-translate-y-px hover:border-accent-ink/60 focus-visible:outline-2 focus-visible:outline-ring")}>
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
    <span className="inline-flex flex-wrap items-center gap-x-1.5">
      <span
        className={cn(
          "inline-flex items-center gap-0.5 rounded-full px-1.5 py-0.5 font-semibold",
          good === null ? "bg-secondary text-muted-foreground" : good ? "bg-success-bg text-success" : "bg-danger-bg text-danger",
        )}
      >
        <Icon className="size-3" aria-hidden="true" />
        <span>
          <span className="sr-only">{direction === "up" ? "Up" : direction === "down" ? "Down" : "No change"} </span>
          {Math.abs(change) > 999 ? ">999" : Math.abs(change).toFixed(1)}%
        </span>
      </span>
      <span className="text-muted-foreground">{label}</span>
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

const ALERT_TONES = {
  danger: { bar: "before:bg-danger-solid", icon: "text-danger", Icon: XCircle },
  warning: { bar: "before:bg-warning", icon: "text-warning", Icon: AlertTriangle },
  info: { bar: "before:bg-info", icon: "text-info", Icon: Info },
} as const;

/**
 * Attention item (overdue invoice, expiring estimate). A calm neutral card: the severity shows as a left accent and
 * icon, and the text stays in normal ink so a page of alerts never shouts over the figures below it.
 */
export function AlertCard({ tone, title, children, href }: { tone: "warning" | "danger" | "info"; title: string; children?: ReactNode; href?: string }) {
  const { bar, icon, Icon } = ALERT_TONES[tone];
  return (
    <div
      className={cn(
        "relative flex gap-3 overflow-hidden rounded-lg border border-border bg-surface py-3.5 pl-5 pr-4 before:absolute before:inset-y-0 before:left-0 before:w-1",
        bar,
      )}
    >
      <Icon className={cn("mt-0.5 size-4 shrink-0", icon)} aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-3">
          <h3 className="text-sm font-semibold leading-snug">{title}</h3>
          {href ? (
            <Link
              to={href}
              className="-my-0.5 inline-flex shrink-0 items-center gap-1 rounded-sm py-0.5 text-sm font-medium text-accent-ink underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
            >
              Review <ArrowRight className="size-3.5" aria-hidden="true" />
            </Link>
          ) : null}
        </div>
        <div className="mt-1 space-y-1 text-sm text-muted-foreground [&_a]:text-foreground [&_li>span.font-medium]:text-foreground">{children}</div>
      </div>
    </div>
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
