import { NavLink } from "react-router";
import { PanelLeftClose, PanelLeftOpen } from "lucide-react";
import { visibleSections } from "@/app/navigation";
import { useAuth } from "@/hooks/useAuth";
import { useModules } from "@/hooks/useModules";
import { Tooltip } from "@/components/ui/Tooltip";
import { cn } from "@/lib/cn";

interface SidebarProps {
  /** Icon-only rail (desktop collapsed, tablet). Labels stay available to assistive technology and as tooltips. */
  collapsed?: boolean;
  /** Shows the collapse/expand control (desktop only). */
  onToggleCollapsed?: () => void;
  /** Called after a link is chosen (closes the phone drawer). */
  onNavigate?: () => void;
}

/** Navigation built from the signed-in user's effective READ permissions; labels come from the module catalogue. */
export function Sidebar({ collapsed = false, onToggleCollapsed, onNavigate }: SidebarProps) {
  const auth = useAuth();
  const { labelOf } = useModules();
  const sections = visibleSections(auth);

  return (
    <nav aria-label="Main navigation" className="flex h-full flex-col bg-sidebar text-sidebar-foreground">
      <div className={cn("flex h-14 shrink-0 items-center", collapsed ? "justify-center px-2" : "px-5")}>
        {collapsed ? (
          <span className="font-display text-lg font-bold tracking-wide" aria-label="IOTIQ Partner ERP">
            IQ
          </span>
        ) : (
          <div className="leading-none">
            <div className="font-display text-xl font-bold tracking-wide">IOTIQ</div>
            <div className="mt-1 text-2xs font-medium uppercase tracking-[0.14em] text-accent">Partner ERP</div>
          </div>
        )}
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto overflow-x-hidden px-2 pb-3">
        {sections.map((section) => (
          <div key={section.group ?? "top"} className="mt-2 first:mt-1">
            {section.group ? (
              collapsed ? (
                <div role="separator" aria-label={section.group} className="mx-3 my-2.5 h-px bg-sidebar-border" />
              ) : (
                <div className="px-3 pb-1 pt-3 text-2xs font-semibold uppercase tracking-[0.14em] text-sidebar-muted">{section.group}</div>
              )
            ) : null}
            <ul className="space-y-0.5">
              {section.items.map((item) => {
                const Icon = item.icon;
                const label = labelOf(item.module);
                return (
                  <li key={item.path}>
                    <Tooltip content={label} side="right" enabled={collapsed}>
                      <NavLink
                        to={item.path}
                        onClick={onNavigate}
                        aria-label={collapsed ? label : undefined}
                        // A static class string: the tooltip trigger (Radix Slot) merges className as a string, so
                        // NavLink's function form would be stringified. The active state is styled via aria-current.
                        className={cn(
                          "flex min-h-10 items-center gap-3 rounded-md px-3 text-sm font-medium transition-colors",
                          "text-sidebar-foreground hover:bg-sidebar-hover",
                          "aria-[current=page]:bg-sidebar-active aria-[current=page]:text-sidebar-active-foreground aria-[current=page]:hover:bg-sidebar-active",
                          "focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-accent",
                          collapsed && "justify-center px-0",
                        )}
                      >
                        <Icon className="size-[18px] shrink-0" aria-hidden="true" />
                        {collapsed ? null : <span className="truncate">{label}</span>}
                      </NavLink>
                    </Tooltip>
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </div>

      <div className={cn("shrink-0 border-t border-sidebar-border p-2", collapsed && "flex justify-center")}>
        {onToggleCollapsed ? (
          <Tooltip content={collapsed ? "Expand sidebar" : "Collapse sidebar"} side="right" enabled={collapsed}>
            <button
              type="button"
              onClick={onToggleCollapsed}
              aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
              aria-expanded={!collapsed}
              className={cn(
                "flex min-h-10 w-full items-center gap-3 rounded-md px-3 text-sm text-sidebar-muted hover:bg-sidebar-hover hover:text-sidebar-foreground",
                "focus-visible:outline-2 focus-visible:outline-accent",
                collapsed && "w-10 justify-center px-0",
              )}
            >
              {collapsed ? <PanelLeftOpen className="size-[18px]" aria-hidden="true" /> : <PanelLeftClose className="size-[18px]" aria-hidden="true" />}
              {collapsed ? null : <span>Collapse</span>}
            </button>
          </Tooltip>
        ) : null}
        {collapsed ? null : (
          <p className="px-3 pb-1 pt-2 text-2xs leading-relaxed text-sidebar-muted">
            Internal use only. Partner copies never show B2C names or prices.
          </p>
        )}
      </div>
    </nav>
  );
}
