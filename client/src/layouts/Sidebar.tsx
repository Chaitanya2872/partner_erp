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
  /** Whether the user has pinned the rail collapsed; drives the toggle's label while a hover temporarily expands it. */
  pinnedCollapsed?: boolean;
  /** Shows the collapse/expand control (desktop only). */
  onToggleCollapsed?: () => void;
  /** Called after a link is chosen (closes the phone drawer). */
  onNavigate?: () => void;
}

/** Navigation built from the signed-in user's effective READ permissions; labels come from the module catalogue. */
export function Sidebar({ collapsed = false, pinnedCollapsed = collapsed, onToggleCollapsed, onNavigate }: SidebarProps) {
  const auth = useAuth();
  const { labelOf } = useModules();
  const sections = visibleSections(auth);

  return (
    <nav aria-label="Main navigation" className="flex h-full w-full min-w-64 flex-col bg-sidebar text-sidebar-foreground">
      <div className="relative flex h-14 shrink-0 items-center px-5">
        <div className={cn("leading-none transition-opacity duration-200", collapsed ? "opacity-0" : "opacity-100 delay-100")} aria-hidden={collapsed}>
          <div className="font-display text-xl font-bold tracking-wide">IOTIQ</div>
          <div className="mt-1 whitespace-nowrap text-2xs font-medium uppercase tracking-[0.14em] text-accent">Partner ERP</div>
        </div>
        <span
          className={cn(
            "absolute left-0 top-1/2 w-[4.5rem] -translate-y-1/2 text-center font-display text-lg font-bold tracking-wide transition-opacity duration-200",
            collapsed ? "opacity-100 delay-100" : "pointer-events-none opacity-0",
          )}
          aria-label={collapsed ? "IOTIQ Partner ERP" : undefined}
          aria-hidden={!collapsed}
        >
          IQ
        </span>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto overflow-x-hidden px-2 pb-3 [scrollbar-color:transparent_transparent] [scrollbar-width:thin] hover:[scrollbar-color:var(--sidebar-border)_transparent]">
        {sections.map((section) => (
          <div key={section.group ?? "top"} className="mt-1 first:mt-1">
            {section.group ? (
              <div className="relative">
                <div
                  className={cn(
                    "whitespace-nowrap px-3 pb-1 pt-2.5 text-2xs font-semibold uppercase tracking-[0.14em] text-sidebar-muted transition-opacity duration-200",
                    collapsed && "opacity-0",
                  )}
                >
                  {section.group}
                </div>
                <div
                  role={collapsed ? "separator" : undefined}
                  aria-label={collapsed ? section.group : undefined}
                  className={cn(
                    "absolute inset-x-3 top-1/2 h-px bg-sidebar-border transition-opacity duration-200",
                    collapsed ? "opacity-100" : "opacity-0",
                  )}
                />
              </div>
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
                          "relative flex min-h-9 items-center gap-3 [@media(max-height:720px)]:min-h-8 rounded-md px-[19px] text-sm font-medium transition-[background-color,color] duration-150",
                          "text-sidebar-foreground hover:bg-sidebar-hover",
                          "aria-[current=page]:bg-sidebar-active/15 aria-[current=page]:text-sidebar-active aria-[current=page]:hover:bg-sidebar-active/20",
                          "before:absolute before:left-0 before:top-2 before:bottom-2 before:w-0.5 before:scale-y-0 before:rounded-full before:bg-sidebar-active before:transition-transform before:duration-200 aria-[current=page]:before:scale-y-100",
                          "focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-accent",
                        )}
                      >
                        <Icon className="size-[18px] shrink-0" aria-hidden="true" />
                        <span className={cn("truncate whitespace-nowrap transition-opacity duration-200", collapsed ? "opacity-0" : "opacity-100 delay-100")}>{label}</span>
                      </NavLink>
                    </Tooltip>
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </div>

      <div className="shrink-0 border-t border-sidebar-border p-2">
        {onToggleCollapsed ? (
          <Tooltip content={pinnedCollapsed ? "Expand sidebar" : "Collapse sidebar"} side="right" enabled={collapsed}>
            <button
              type="button"
              onClick={onToggleCollapsed}
              aria-label={pinnedCollapsed ? "Expand sidebar" : "Collapse sidebar"}
              aria-expanded={!pinnedCollapsed}
              className={cn(
                "flex min-h-10 w-full items-center gap-3 rounded-md px-[19px] text-sm text-sidebar-muted transition-colors hover:bg-sidebar-hover hover:text-sidebar-foreground",
                "focus-visible:outline-2 focus-visible:outline-accent",
              )}
            >
              {pinnedCollapsed ? <PanelLeftOpen className="size-[18px]" aria-hidden="true" /> : <PanelLeftClose className="size-[18px]" aria-hidden="true" />}
              <span className={cn("whitespace-nowrap transition-opacity duration-200", collapsed ? "opacity-0" : "opacity-100 delay-100")}>{pinnedCollapsed ? "Expand" : "Collapse"}</span>
            </button>
          </Tooltip>
        ) : null}
        <p
          className={cn(
            "px-3 pb-1 pt-2 text-2xs leading-relaxed text-sidebar-muted transition-opacity duration-200 [@media(max-height:820px)]:hidden",
            collapsed ? "pointer-events-none h-0 overflow-hidden py-0 opacity-0" : "opacity-100 delay-100",
          )}
          aria-hidden={collapsed}
        >
          Internal use only. Partner copies never show B2C names or prices.
        </p>
      </div>
    </nav>
  );
}
