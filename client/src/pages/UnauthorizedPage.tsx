import { Link } from "react-router";
import { ShieldAlert } from "lucide-react";

export function UnauthorizedPage() {
  return (
    <div className="card" style={{ maxWidth: 520, margin: "40px auto", textAlign: "center", padding: 28 }}>
      <ShieldAlert size={32} aria-hidden="true" style={{ color: "var(--warn)" }} />
      <h1 style={{ fontSize: 22, marginTop: 8 }}>You don't have access to this page</h1>
      <p className="muted">Ask an administrator to grant the permission you need.</p>
      <Link to="/" className="btn">
        Go to your home page
      </Link>
    </div>
  );
}
