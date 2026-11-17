import type { ReactNode } from "react";
import { Link } from "react-router";
import { Lock, type LucideIcon } from "lucide-react";
import { MetricCard, TrendIndicator } from "@/components/dashboard/Dashboard";
import { Alert } from "@/components/ui/Alert";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { useModules } from "@/hooks/useModules";
import { cn } from "@/lib/cn";
import type { Money, Restriction } from "../types/dashboard.types";
import { percentChange } from "../utils/change";

/*
 * Shared dashboard pieces: KPI grids, period-over-period trends, restriction notices and compact tables. Every value
 * is computed by the server (DECISIONS.md §M); the client only formats it.
 */

export function Trend({ current, previous, positiveIsGood = true }: { current: Money | null | undefined; previous: Money | null | undefined; positiveIsGood?: boolean }) {
  const change = percentChange(current, previous);
  if (change === null) return <span className="text-muted-foreground">No comparable previous period</span>;
  return <TrendIndicator change={change} positiveIsGood={positiveIsGood} />;
}

export function KpiGrid({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("grid auto-rows-fr gap-3 sm:grid-cols-2 xl:grid-cols-4", className)}>{children}</div>;
}

export interface KpiProps {
  label: string;
  icon?: LucideIcon;
  value?: ReactNode;
  hint?: ReactNode;
  trend?: ReactNode;
  loading?: boolean;
  href?: string;
}

export function Kpi(props: KpiProps) {
  return <MetricCard {...props} />;
}

const REASONS: Record<string, string> = {
  NO_ACCESS: "not assigned to you",
  SCOPE_SELF: "you can see only your own records",
  SCOPE_TEAM: "you can see only your team's records",
};

/**
 * Says which modules' figures are withheld and why. Organisation-wide analytics need read access with the All
 * records scope; a narrower scope hides the module instead of showing a partial total that looks complete.
 */
export function RestrictionNotice({ restricted, modules }: { restricted?: Restriction[]; modules?: string[] }) {
  const { labelOf } = useModules();
  const shown = (restricted ?? []).filter((r) => !modules || modules.includes(r.module));
  if (shown.length === 0) return null;
  const scoped = shown.filter((r) => r.reason !== "NO_ACCESS");
  if (scoped.length === 0) return null;
  return (
    <Alert tone="info" title="Some figures are hidden">
      <ul className="mt-1 space-y-0.5">
        {scoped.map((r) => (
          <li key={r.module}>
            <span className="font-medium">{labelOf(r.module)}</span>: {REASONS[r.reason] ?? "restricted"}. Analytics cover all
            records, so they need the All records scope.
          </li>
        ))}
      </ul>
    </Alert>
  );
}

/** Placeholder for a whole section the caller can't see. */
export function Unavailable({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="flex items-start gap-3 rounded-lg border border-dashed border-border-strong p-4 text-sm text-muted-foreground">
      <Lock className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <div>
        <div className="font-medium text-foreground">{title}</div>
        {children}
      </div>
    </div>
  );
}

export interface MiniColumn<T> {
  header: string;
  cell: (row: T) => ReactNode;
  numeric?: boolean;
  className?: string;
}

/** Compact, non-paginated table for top-N lists on the dashboard. */
export function MiniTable<T>({ title, description, caption, columns, rows, rowKey, empty = "Nothing for this period." }: { title: string; description?: ReactNode; caption?: string; columns: MiniColumn<T>[]; rows: T[] | null | undefined; rowKey: (row: T) => string; empty?: ReactNode }) {
  return (
    <Card>
      <CardHeader>
        <div className="min-w-0">
          <CardTitle>{title}</CardTitle>
          {description ? <CardDescription>{description}</CardDescription> : null}
        </div>
      </CardHeader>
      <CardContent className="pt-2 sm:pt-2">
        {!rows || rows.length === 0 ? (
          <p className="py-4 text-sm text-muted-foreground">{empty}</p>
        ) : (
          <div className="-mx-1 overflow-x-auto">
            <table className="w-full min-w-max text-sm">
              <caption className="sr-only">{caption ?? title}</caption>
              <thead>
                <tr className="border-b border-border text-xs text-muted-foreground">
                  {columns.map((c) => (
                    <th key={c.header} scope="col" className={cn("px-1 py-2 font-medium", c.numeric ? "text-right" : "text-left")}>
                      {c.header}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={rowKey(row)} className="border-b border-border last:border-0">
                    {columns.map((c) => (
                      <td key={c.header} className={cn("px-1 py-2", c.numeric && "text-right font-mono tabular", c.className)}>
                        {c.cell(row)}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

const linkClass = "font-medium text-accent-ink underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-ring";

/** A record name that links to its detail page only when the caller can open it. */
export function RecordLink({ to, canOpen, children }: { to: string; canOpen: boolean; children: ReactNode }) {
  return canOpen ? (
    <Link to={to} className={linkClass}>
      {children}
    </Link>
  ) : (
    <span>{children}</span>
  );
}
