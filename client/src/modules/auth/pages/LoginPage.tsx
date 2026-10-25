import { Navigate, useLocation, useNavigate } from "react-router";
import { AuthLayout } from "@/layouts/AuthLayout";
import { LoginForm } from "../components/LoginForm";
import { useAuth } from "@/hooks/useAuth";
import { LoadingSpinner } from "@/components/common/LoadingSpinner";

export function LoginPage() {
  const { status, login, sessionExpired } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;

  if (status === "loading") {
    return <LoadingSpinner fullPage label="Restoring your session…" />;
  }
  if (status === "authenticated") {
    return <Navigate to={from ?? "/"} replace />;
  }

  return (
    <AuthLayout>
      <div className="panel">
        <div>
          <h1>Sign in</h1>
          <p className="muted" style={{ margin: "4px 0 0" }}>
            IOTIQ Partner ERP
          </p>
        </div>
        {sessionExpired ? (
          <div className="notice warn" role="status">
            Your session has ended. Sign in again to continue.
          </div>
        ) : null}
        <LoginForm
          onSubmit={async ({ email, password }) => {
            const me = await login(email, password);
            navigate(me.mustChangePassword ? "/account/password" : (from ?? "/"), { replace: true });
          }}
        />
      </div>
    </AuthLayout>
  );
}
