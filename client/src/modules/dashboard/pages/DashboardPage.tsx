import { useCallback, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { useAuth } from "@/hooks/useAuth";
import { useModules } from "@/hooks/useModules";
import { NAV_ITEMS, isVisible } from "@/app/navigation";
import { PageContainer, PageHeader } from "@/components/layout/Page";
import { DashboardSection } from "@/components/dashboard/Dashboard";
import { Badge } from "@/components/ui/Badge";
import { Tabs, TabsContent, SegmentedList, SegmentedTrigger } from "@/components/ui/Tabs";
import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/lib/queryKeys";
import { dashboardService } from "../services/dashboardService";
import { formatDateTime } from "@/utils/format";
import type { ModuleCode } from "@/types/common.types";
import { AlertsPanel } from "../components/AlertsPanel";
import { FinanceTab, OverviewTab, PartnersTab, ProductsTab, SalesTab } from "../components/AnalyticsTabs";
import { PeriodFilter } from "../components/PeriodFilter";
import { usePeriod } from "../hooks/usePeriod";
import type { PeriodView } from "../types/dashboard.types";

/** Tabs and the modules that make each worth showing; the server still decides what each returns. */
const TABS: { value: string; label: string; anyOf: ModuleCode[] }[] = [
  { value: "overview", label: "Overview", anyOf: ["PARTNERS", "LEADS", "ESTIMATES", "INVOICES", "PAYMENTS", "PAYOUTS"] },
  { value: "sales", label: "Sales", anyOf: ["LEADS"] },
  { value: "partners", label: "Partners", anyOf: ["PARTNERS"] },
  { value: "products", label: "Products", anyOf: ["PRODUCTS"] },
  { value: "finance", label: "Finance", anyOf: ["INVOICES", "PAYMENTS", "PAYOUTS"] },
];

/**
 * Executive dashboard (Phase 12): KPIs, analytics and alerts computed on the server for the chosen period. Figures
 * cover all records, so a module contributes only when the user reads it with the All records scope; nothing is
 * cached between users (DECISIONS.md §M).
 */
export function DashboardPage() {
  const auth = useAuth();
  const { labelOf } = useModules();
  const user = auth.user!;
  const items = NAV_ITEMS.filter((i) => i.path !== "/dashboard" && isVisible(i, auth));
  const { period, setPeriod, complete } = usePeriod();
  const [resolved, setResolved] = useState<PeriodView>();
  const onResolved = useCallback((p: PeriodView) => setResolved(p), []);
  const [params, setParams] = useSearchParams();
  const tabs = TABS.filter((t) => t.anyOf.some((m) => auth.canRead(m)));
  const requested = params.get("tab");
  const tab = tabs.find((t) => t.value === requested)?.value ?? tabs[0]?.value;
  const selectTab = (value: string) =>
    setParams(
      (current) => {
        const out = new URLSearchParams(current);
        out.set("tab", value);
        return out;
      },
      { replace: true },
    );
  // Two peer views behind a switch; the alert count rides on the switch so attention items are never out of sight.
  const alerts = useQuery({ queryKey: queryKeys.dashboard.alerts, queryFn: ({ signal }) => dashboardService.alerts(signal) });
  const alertCount = alerts.data?.groups.length ?? 0;
  const view = params.get("view") === "attention" ? "attention" : "performance";
  const selectView = (value: string) =>
    setParams(
      (current) => {
        const out = new URLSearchParams(current);
        out.set("view", value);
        return out;
      },
      { replace: true },
    );
  const tabProps = (value: string) => ({ period, enabled: complete && tab === value, onResolved });

  return (
    <PageContainer>
      <PageHeader
        title={`Welcome, ${user.fullName.split(" ")[0]}`}
        documentTitle="Dashboard"
        description={
          <>
            Signed in as <span className="font-medium text-foreground">{user.role.displayName}</span>
            {user.lastLoginAt ? ` · previous sign-in ${formatDateTime(user.lastLoginAt)}` : ""}
          </>
        }
      />

      <Tabs value={view} onValueChange={selectView} className="space-y-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <SegmentedList aria-label="Dashboard view">
            <SegmentedTrigger value="performance">Business performance</SegmentedTrigger>
            <SegmentedTrigger value="attention">
              Needs attention
              {alertCount > 0 ? (
                <span className="inline-flex min-w-5 items-center justify-center rounded-full bg-danger-solid px-1.5 text-2xs font-semibold leading-5 text-white">
                  {alertCount}
                </span>
              ) : null}
            </SegmentedTrigger>
          </SegmentedList>
          {view === "performance" && tabs.length > 0 ? (
            <PeriodFilter value={period} onChange={setPeriod} resolved={complete ? resolved : undefined} idPrefix="dashboard-period" />
          ) : null}
        </div>

        <TabsContent value="attention" className="pt-0">
          <AlertsPanel />
        </TabsContent>

        <TabsContent value="performance" className="pt-0">
          {tabs.length > 0 && tab ? (
            <section aria-label="Business performance" className="space-y-4">
              <p className="text-sm text-muted-foreground">Amounts in ₹, rounded to the paisa. Cancelled invoices and reversed receipts are excluded.</p>
              <Tabs value={tab} onValueChange={selectTab}>
                <SegmentedList aria-label="Dashboard sections" className="bg-transparent p-0 ring-1 ring-inset ring-border">
                  {tabs.map((t) => (
                    <SegmentedTrigger key={t.value} value={t.value} className="data-[state=active]:bg-primary data-[state=active]:text-primary-foreground data-[state=active]:shadow-none">
                      {t.label}
                    </SegmentedTrigger>
                  ))}
                </SegmentedList>
                <TabsContent value="overview" className="pt-4">
                  <OverviewTab {...tabProps("overview")} />
                </TabsContent>
                <TabsContent value="sales" className="pt-4">
                  <SalesTab {...tabProps("sales")} />
                </TabsContent>
                <TabsContent value="partners" className="pt-4">
                  <PartnersTab {...tabProps("partners")} />
                </TabsContent>
                <TabsContent value="products" className="pt-4">
                  <ProductsTab {...tabProps("products")} />
                </TabsContent>
                <TabsContent value="finance" className="pt-4">
                  <FinanceTab {...tabProps("finance")} />
                </TabsContent>
              </Tabs>
            </section>
          ) : (
            <p className="text-sm text-muted-foreground">No analytics are available for your modules.</p>
          )}
        </TabsContent>
      </Tabs>

      <DashboardSection title="Your access" description="Modules on your account. Click to open.">
        {items.length === 0 ? (
          <p className="text-sm text-muted-foreground">No other modules are assigned to your account.</p>
        ) : (
          <ul className="flex flex-wrap gap-2">
            {items.map((item) => {
              const Icon = item.icon;
              const write = auth.canWrite(item.module);
              return (
                <li key={item.path}>
                  <Link
                    to={item.path}
                    className="inline-flex min-h-9 items-center gap-2 rounded-md border border-border bg-surface px-3 text-sm font-medium transition-colors duration-150 hover:border-accent-ink/60 hover:bg-hover focus-visible:outline-2 focus-visible:outline-ring"
                  >
                    <Icon className="size-4 text-accent-ink" aria-hidden="true" />
                    {labelOf(item.module)}
                    {write ? null : <span className="text-xs font-normal text-muted-foreground">Read only</span>}
                    {item.phase ? <Badge tone="info">Phase {item.phase}</Badge> : null}
                  </Link>
                </li>
              );
            })}
          </ul>
        )}
      </DashboardSection>
    </PageContainer>
  );
}
