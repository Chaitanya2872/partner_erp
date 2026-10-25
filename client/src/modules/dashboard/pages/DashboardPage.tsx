import { Link } from "react-router";
import { useAuth } from "@/hooks/useAuth";
import { useModules } from "@/hooks/useModules";
import { NAV_ITEMS, isVisible } from "@/app/navigation";

/**
 * Landing page. Business metrics arrive with Phase 12; until then it shows the signed-in user's access so
 * nothing on it is invented.
 */
export function DashboardPage() {
  const auth = useAuth();
  const { labelOf } = useModules();
  const user = auth.user!;
  const items = NAV_ITEMS.filter((i) => i.path !== "/dashboard" && isVisible(i, auth));

  return (
    <>
      <div className="top">
        <div>
          <h1>Welcome, {user.fullName.split(" ")[0]}</h1>
          <p>
            Signed in as <strong>{user.role.displayName}</strong>
            {user.lastLoginAt ? ` · previous sign-in ${new Date(user.lastLoginAt).toLocaleString("en-IN")}` : ""}
          </p>
        </div>
      </div>
      <div className="notice info" style={{ marginBottom: 16 }}>
        Business metrics (pipeline, revenue, payouts) are added to the dashboard in Phase 12.
      </div>
      <div className="card">
        <h3>Your access</h3>
        {items.length === 0 ? (
          <p className="muted">No other modules are assigned to your account.</p>
        ) : (
          <div className="tiles" style={{ marginBottom: 0 }}>
            {items.map((item) => {
              const Icon = item.icon;
              const write = auth.canWrite(item.module);
              return (
                <Link key={item.path} to={item.path} className="tile" style={{ textDecoration: "none", color: "inherit" }}>
                  <div className="k" style={{ display: "flex", gap: 6, alignItems: "center" }}>
                    <Icon size={13} aria-hidden="true" /> {item.path === "/settings" ? "Settings" : labelOf(item.module)}
                  </div>
                  <div className="s">{write ? "Read & write" : "Read only"}{item.phase ? ` · Phase ${item.phase}` : ""}</div>
                </Link>
              );
            })}
          </div>
        )}
      </div>
    </>
  );
}
