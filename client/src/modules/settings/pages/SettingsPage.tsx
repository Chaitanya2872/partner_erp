import { useAuth } from "@/hooks/useAuth";
import { RoleManagement } from "../components/RoleManagement";

export function SettingsPage() {
  const { canRead } = useAuth();
  return (
    <>
      <div className="top">
        <div>
          <h1>Settings</h1>
          <p>Administration of roles and company policy.</p>
        </div>
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        {canRead("ROLE_MANAGEMENT") ? <RoleManagement /> : null}
        {canRead("SETTINGS") ? (
          <div className="notice info">Company profile, partner tiers and commercial policy settings are delivered in Phase 13.</div>
        ) : null}
      </div>
    </>
  );
}
