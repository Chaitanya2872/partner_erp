import { Suspense, useState } from "react";
import { Outlet } from "react-router";
import { Sidebar } from "./Sidebar";
import { Topbar } from "./Topbar";
import { Sheet } from "@/components/ui/Dialog";
import { RouteErrorBoundary } from "@/components/feedback/ErrorBoundary";
import { PageSkeleton } from "@/components/feedback/PageSkeleton";
import { BREAKPOINTS, useMediaQuery } from "@/hooks/useMediaQuery";
import { PREFERENCE_KEYS, preferences } from "@/lib/storage";
import { cn } from "@/lib/cn";

/**
 * Application shell.
 * - Desktop (≥1024px): fixed sidebar the user can collapse to an icon rail (remembered on this device).
 * - Tablet (768–1023px): compact icon rail with tooltips.
 * - Phone (<768px): no rail; the top bar's menu button opens the navigation as a drawer.
 */
export function MainLayout() {
  const isDesktop = useMediaQuery(BREAKPOINTS.desktop);
  const isTablet = useMediaQuery(BREAKPOINTS.tablet);
  const [collapsedPref, setCollapsedPref] = useState(() => preferences.get(PREFERENCE_KEYS.sidebarCollapsed) === "true");
  const [drawerOpen, setDrawerOpen] = useState(false);

  const showRail = isTablet;
  const collapsed = isDesktop ? collapsedPref : true;

  const toggleCollapsed = () => {
    const next = !collapsedPref;
    preferences.set(PREFERENCE_KEYS.sidebarCollapsed, String(next));
    setCollapsedPref(next);
  };

  return (
    <div className="flex min-h-dvh">
      <a
        href="#main-content"
        className="sr-only z-50 rounded-md bg-primary px-3 py-2 text-primary-foreground focus:not-sr-only focus:fixed focus:left-3 focus:top-3"
      >
        Skip to main content
      </a>

      {showRail ? (
        <aside
          className={cn(
            "no-print sticky top-0 h-dvh shrink-0 transition-[width] duration-200",
            collapsed ? "w-[4.5rem]" : "w-64",
          )}
          data-testid="sidebar"
          data-collapsed={collapsed}
        >
          <Sidebar collapsed={collapsed} onToggleCollapsed={isDesktop ? toggleCollapsed : undefined} />
        </aside>
      ) : (
        <Sheet open={drawerOpen} onOpenChange={setDrawerOpen} title="Navigation" className="bg-sidebar">
          <div id="mobile-navigation" className="h-full">
            <Sidebar onNavigate={() => setDrawerOpen(false)} />
          </div>
        </Sheet>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar onOpenNavigation={showRail ? undefined : () => setDrawerOpen(true)} navigationOpen={drawerOpen} />
        <main id="main-content" tabIndex={-1} className="min-w-0 flex-1 px-4 pb-16 pt-5 focus:outline-none sm:px-6 sm:pt-6 lg:px-8">
          <RouteErrorBoundary>
            <Suspense fallback={<PageSkeleton />}>
              <Outlet />
            </Suspense>
          </RouteErrorBoundary>
        </main>
      </div>
    </div>
  );
}
