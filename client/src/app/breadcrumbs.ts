import { useMatches } from "react-router";
import { useModules } from "@/hooks/useModules";
import type { Crumb } from "@/components/navigation/Breadcrumbs";
import { navItemByPath } from "./navigation";

/** Route `handle` describing where a page sits in the navigation. */
export interface RouteHandle {
  /** Path of the sidebar item this route belongs to; it supplies the group and module label. */
  nav?: string;
  /** Label of a sub-page below the nav item (e.g. "Permissions"). */
  crumb?: string;
}

/**
 * Breadcrumbs from the matched routes: navigation group, then module (labelled from the database catalogue), then
 * any sub-page. The last crumb is the current page title.
 */
export function useBreadcrumbs(): Crumb[] {
  const matches = useMatches();
  const { labelOf } = useModules();
  const crumbs: Crumb[] = [];
  for (const match of matches) {
    const handle = match.handle as RouteHandle | undefined;
    if (!handle) continue;
    if (handle.nav) {
      const item = navItemByPath(handle.nav);
      if (item) {
        if (item.group) crumbs.push({ label: item.group });
        crumbs.push({ label: labelOf(item.module), to: item.path });
      }
    }
    if (handle.crumb) crumbs.push({ label: handle.crumb });
  }
  return crumbs;
}
