import { Link } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { CheckCircle2, ChevronRight } from "lucide-react";
import { AlertCard, DashboardSection } from "@/components/dashboard/Dashboard";
import { ErrorState } from "@/components/feedback/States";
import { Skeleton } from "@/components/ui/Skeleton";
import { useAuth } from "@/hooks/useAuth";
import { queryKeys } from "@/lib/queryKeys";
import { formatCurrency, formatDateTime, formatNumber } from "@/utils/format";
import type { ModuleCode } from "@/types/common.types";
import { dashboardService } from "../services/dashboardService";

/**
 * Operational alerts derived by the server from current data (overdue invoices, missed lead SLAs, payouts awaiting
 * approval, …). Only modules the caller can read with the All records scope contribute; each group lists up to
 * five records.
 */
export function AlertsPanel() {
  const { canRead } = useAuth();
  const alerts = useQuery({ queryKey: queryKeys.dashboard.alerts, queryFn: ({ signal }) => dashboardService.alerts(signal) });
  const groups = alerts.data?.groups ?? [];
  return (
    <DashboardSection
      title="Needs attention"
      description={alerts.data ? `As of ${formatDateTime(alerts.data.generatedAt)}; not affected by the period filter.` : "Current alerts; not affected by the period filter."}
    >
      {alerts.isPending ? (
        <div className="grid gap-3 lg:grid-cols-2">
          <Skeleton className="h-20" />
          <Skeleton className="h-20" />
        </div>
      ) : alerts.isError ? (
        <ErrorState error={alerts.error} onRetry={() => alerts.refetch()} />
      ) : groups.length === 0 ? (
        <p className="flex items-center gap-2 rounded-lg border border-border bg-surface p-4 text-sm text-muted-foreground">
          <CheckCircle2 className="size-4 text-success" aria-hidden="true" /> Nothing needs attention right now.
        </p>
      ) : (
        <ul className="grid items-start gap-3 md:grid-cols-2 2xl:grid-cols-4" aria-label="Alerts">
          {groups.map((g) => {
            const open = canRead(g.module as ModuleCode);
            return (
              <li key={g.code}>
                <AlertCard tone={g.severity} title={`${g.title} · ${formatNumber(g.count)}`} href={open && g.href ? g.href : undefined}>
                  <p className="line-clamp-2">
                    {g.detail}
                    {g.amount !== null && Number(g.amount) !== 0 ? ` Total ${formatCurrency(g.amount)}.` : ""}
                  </p>
                  {g.items.length > 0 ? (
                    <details className="group/records pt-0.5">
                      <summary className="inline-flex cursor-pointer select-none list-none items-center gap-1 rounded-sm text-xs font-medium text-accent-ink hover:underline focus-visible:outline-2 focus-visible:outline-ring [&::-webkit-details-marker]:hidden">
                        <ChevronRight className="size-3.5 transition-transform duration-150 group-open/records:rotate-90" aria-hidden="true" />
                        {g.count > g.items.length ? `Show first ${g.items.length} of ${formatNumber(g.count)}` : `Show ${g.items.length} ${g.items.length === 1 ? "record" : "records"}`}
                      </summary>
                    <ul className="mt-1.5 space-y-0.5">
                      {g.items.map((item) => (
                        <li key={item.id} className="text-sm">
                          {open && item.href ? (
                            <Link to={item.href} className="font-medium underline-offset-2 hover:underline">
                              {item.label}
                            </Link>
                          ) : (
                            <span className="font-medium">{item.label}</span>
                          )}
                          {item.detail ? <span className="text-muted-foreground"> · {item.detail}</span> : null}
                        </li>
                      ))}
                      {g.count > g.items.length ? <li className="text-xs text-muted-foreground">and {formatNumber(g.count - g.items.length)} more</li> : null}
                    </ul>
                    </details>
                  ) : null}
                </AlertCard>
              </li>
            );
          })}
        </ul>
      )}
    </DashboardSection>
  );
}
