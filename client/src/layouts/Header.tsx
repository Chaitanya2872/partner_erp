import { Link, useNavigate } from "react-router";
import { KeyRound, LogOut } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";

export function Header() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  if (!user) return null;

  const signOut = async () => {
    await logout().catch(() => undefined);
    toast.success("Signed out.");
    navigate("/login", { replace: true });
  };

  return (
    <header style={{ display: "flex", justifyContent: "flex-end", alignItems: "center", gap: 10, marginBottom: 14 }}>
      <div style={{ textAlign: "right", lineHeight: 1.2 }}>
        <div style={{ fontWeight: 600 }}>{user.fullName}</div>
        <div className="muted small">
          {user.role.displayName} · {user.email}
        </div>
      </div>
      <Link to="/account/password" className="btn sm" title="Change password">
        <KeyRound size={14} aria-hidden="true" /> Password
      </Link>
      <button type="button" className="btn sm" onClick={signOut}>
        <LogOut size={14} aria-hidden="true" /> Sign out
      </button>
    </header>
  );
}
