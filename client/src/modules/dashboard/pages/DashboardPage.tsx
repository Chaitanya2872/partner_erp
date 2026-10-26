import { useCallback, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { ArrowRight } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useModules } from "@/hooks/useModules";
import { NAV_ITEMS, isVisible } from "@/app/navigation";
import { PageContainer, PageHeader } from "@/components/layout/Page";
import { DashboardSection } from "@/components/dashboard/Dashboard";
import { Badge } from "@/components/ui/Badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/Tabs";
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

      <AlertsPanel />

      {tabs.length > 0 && tab ? (
        <section aria-labelledby="dash-analytics" className="space-y-4">
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div>
              <h2 id="dash-analytics" className="text-base font-semibold">
                Business performance
              </h2>
              <p className="text-sm text-muted-foreground">Amounts in ₹, rounded to the paisa. Cancelled invoices and reversed receipts are excluded.</p>
            </div>
            <PeriodFilter value={period} onChange={setPeriod} resolved={complete ? resolved : undefined} idPrefix="dashboard-period" />
          </div>
          <Tabs value={tab} onValueChange={selectTab}>
            <TabsList aria-label="Dashboard sections" className="max-w-full overflow-x-auto">
              {tabs.map((t) => (
                <TabsTrigger key={t.value} value={t.value}>
                  {t.label}
                </TabsTrigger>
              ))}
            </TabsList>
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
      ) : null}

      <DashboardSection title="Your access">
        {items.length === 0 ? (
          <p className="text-sm text-muted-foreground">No other modules are assigned to your account.</p>
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {items.map((item) => {
              const Icon = item.icon;
              const write = auth.canWrite(item.module);
              return (
                <li key={item.path}>
                  <Link
                    to={item.path}
                    className="group flex h-full items-center gap-3 rounded-lg border border-border bg-surface p-4 shadow-xs transition-colors hover:border-accent focus-visible:outline-2 focus-visible:outline-ring"
                  >
                    <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-md bg-secondary text-accent-ink">
                      <Icon className="size-4" aria-hidden="true" />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-medium">{labelOf(item.module)}</span>
                      <span className="mt-0.5 flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
                        {write ? "Read & write" : "Read only"}
                        {item.phase ? <Badge tone="info">Phase {item.phase}</Badge> : null}
                      </span>
                    </span>
                    <ArrowRight className="size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
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
