import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router";
import { useAuth } from "@/hooks/useAuth";
import { LoadingSpinner } from "@/components/common/LoadingSpinner";
import { UnauthorizedPage } from "@/pages/UnauthorizedPage";
import type { ModuleCode } from "@/types/common.types";

interface ProtectedRouteProps {
  children: ReactNode;
  /** The route is shown when the user can read any of these modules (omit for any signed-in user). */
  anyOf?: ModuleCode[];
}

/**
 * Requires a session, sends users who must change their password to that screen, and renders the
 * unauthorized page when module access is missing. Presentation only: the API enforces the same rules.
 */
export function ProtectedRoute({ children, anyOf }: ProtectedRouteProps) {
  const { status, user, canRead } = useAuth();
  const location = useLocation();

  if (status === "loading") {
    return <LoadingSpinner fullPage label="Restoring your session…" />;
  }
  if (status === "anonymous" || !user) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  if (user.mustChangePassword && location.pathname !== "/account/password") {
    return <Navigate to="/account/password" replace />;
  }
  if (anyOf && !anyOf.some((m) => canRead(m))) {
    return <UnauthorizedPage />;
  }
  return <>{children}</>;
}
