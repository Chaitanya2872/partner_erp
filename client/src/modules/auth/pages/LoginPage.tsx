import { Navigate, useLocation, useNavigate } from "react-router";
import { AuthLayout } from "@/layouts/AuthLayout";
import { LoginForm } from "../components/LoginForm";
import { useAuth } from "@/hooks/useAuth";
import { LoadingState } from "@/components/feedback/States";
import { Alert } from "@/components/ui/Alert";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";

export function LoginPage() {
  const { status, login, sessionExpired } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;
  useDocumentTitle("Sign in");

  if (status === "loading") {
    return <LoadingState fullPage label="Restoring your session…" />;
  }
  if (status === "authenticated") {
    return <Navigate to={from ?? "/"} replace />;
  }

  return (
    <AuthLayout>
      <div className="flex flex-col gap-6">
        <div>
          <h1 className="text-3xl font-semibold">Sign in</h1>
          <p className="mt-1 text-sm text-muted-foreground">Use your IOTIQ Partner ERP account.</p>
        </div>
        {sessionExpired ? (
          <Alert tone="warning" role="status" title="Your session has ended">
            For your security you were signed out. Sign in again to continue where you left off.
          </Alert>
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
