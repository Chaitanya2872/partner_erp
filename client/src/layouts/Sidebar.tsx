import { NavLink } from "react-router";
import { NAV_ITEMS, isVisible, type NavItem } from "@/app/navigation";
import { useAuth } from "@/hooks/useAuth";
import { useModules } from "@/hooks/useModules";

/** Navigation built from the signed-in user's READ permissions; labels come from the module catalogue. */
export function Sidebar() {
  const auth = useAuth();
  const { labelOf } = useModules();
  const visible = NAV_ITEMS.filter((item) => isVisible(item, auth));
  const groups: NavItem["group"][] = ["Workspace", "Administration"];

  return (
    <nav className="rail" aria-label="Main navigation">
      <div className="brand">
        IOTIQ<small>Partner ERP</small>
      </div>
      {groups.map((group) => {
        const items = visible.filter((i) => i.group === group);
        if (items.length === 0) return null;
        return (
          <div key={group} style={{ display: "contents" }}>
            <div className="grp">{group}</div>
            {items.map((item) => {
              const Icon = item.icon;
              const label = item.path === "/settings" ? "Settings" : labelOf(item.module);
              return (
                <NavLink key={item.path} to={item.path}>
                  <Icon size={16} aria-hidden="true" />
                  <span>{label}</span>
                </NavLink>
              );
            })}
          </div>
        );
      })}
      <div className="foot">v0.1 · Phase 3</div>
    </nav>
  );
}
