import { Link } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { ArrowRight, Package, Users } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useModules } from "@/hooks/useModules";
import { NAV_ITEMS, isVisible } from "@/app/navigation";
import { PageContainer, PageHeader } from "@/components/layout/Page";
import { ChartContainer, DashboardSection, MetricCard } from "@/components/dashboard/Dashboard";
import { Badge } from "@/components/ui/Badge";
import { productService } from "@/modules/products/services/productService";
import { userService } from "@/modules/users/services/userService";
import { formatDateTime, formatNumber } from "@/utils/format";
import { queryKeys } from "@/lib/queryKeys";

/**
 * Landing page. Business metrics (pipeline, revenue, payouts) arrive with Phase 12; until then it shows only
 * figures that existing APIs already return, so nothing on it is invented.
 */
export function DashboardPage() {
  const auth = useAuth();
  const { labelOf } = useModules();
  const user = auth.user!;
  const items = NAV_ITEMS.filter((i) => i.path !== "/dashboard" && isVisible(i, auth));
  const canProducts = auth.canRead("PRODUCTS");
  const canUsers = auth.canRead("USER_MANAGEMENT");

  const products = useQuery({
    queryKey: queryKeys.products.list({ count: true }),
    queryFn: ({ signal }) => productService.list({ page: 0, size: 1 }, signal),
    enabled: canProducts,
  });
  const activeUsers = useQuery({
    queryKey: queryKeys.users.list({ count: true, status: "ACTIVE" }),
    queryFn: ({ signal }) => userService.list({ status: "ACTIVE", page: 0, size: 1 }, signal),
    enabled: canUsers,
  });

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

      {canProducts || canUsers ? (
        <DashboardSection title="At a glance">
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            {canProducts ? (
              <MetricCard
                label="Price sheet items"
                icon={Package}
                loading={products.isPending}
                value={products.data ? formatNumber(products.data.meta.totalElements) : undefined}
                unavailable={products.isError ? "Could not load" : undefined}
                href="/products"
              />
            ) : null}
            {canUsers ? (
              <MetricCard
                label="Active users"
                icon={Users}
                loading={activeUsers.isPending}
                value={activeUsers.data ? formatNumber(activeUsers.data.meta.totalElements) : undefined}
                unavailable={activeUsers.isError ? "Could not load" : undefined}
                href="/users"
              />
            ) : null}
          </div>
        </DashboardSection>
      ) : null}

      <DashboardSection title="Business performance" description="Pipeline, revenue, receivables and payouts.">
        <ChartContainer
          title="Revenue and pipeline"
          unavailable="Business metrics are added to the dashboard in Phase 12, once partners, leads and invoices are recorded in the system."
          height={180}
        />
      </DashboardSection>

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
