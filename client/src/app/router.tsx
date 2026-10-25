import { createBrowserRouter, Navigate, type RouteObject } from "react-router";
import { MainLayout } from "@/layouts/MainLayout";
import { ProtectedRoute } from "@/layouts/ProtectedRoute";
import { LoginPage } from "@/modules/auth/pages/LoginPage";
import { ChangePasswordPage } from "@/modules/auth/pages/ChangePasswordPage";
import { DashboardPage } from "@/modules/dashboard/pages/DashboardPage";
import { ProductsPage } from "@/modules/products/pages/ProductsPage";
import { UsersPage } from "@/modules/users/pages/UsersPage";
import { UserPermissionsPage } from "@/modules/users/pages/UserPermissionsPage";
import { SettingsPage } from "@/modules/settings/pages/SettingsPage";
import { AuditLogsPage } from "@/modules/audit/pages/AuditLogsPage";
import { ComingSoonPage } from "@/pages/ComingSoonPage";
import { NotFoundPage } from "@/pages/NotFoundPage";
import { useAuth } from "@/hooks/useAuth";
import { NAV_ITEMS, isVisible } from "./navigation";
import type { ModuleCode } from "@/types/common.types";
import type { ReactNode } from "react";

/** Sends the user to the first module they can read (the dashboard for most). */
function HomeRedirect() {
  const auth = useAuth();
  const first = NAV_ITEMS.find((item) => isVisible(item, auth));
  return first ? <Navigate to={first.path} replace /> : <NoAccess />;
}

function NoAccess() {
  return (
    <div className="card" style={{ maxWidth: 520, margin: "40px auto", textAlign: "center", padding: 28 }}>
      <h1 style={{ fontSize: 22 }}>No modules assigned</h1>
      <p className="muted">Your account has no module access yet. Ask an administrator to assign permissions.</p>
    </div>
  );
}

const guard = (element: ReactNode, anyOf?: ModuleCode[]) => <ProtectedRoute anyOf={anyOf}>{element}</ProtectedRoute>;

const comingSoon = NAV_ITEMS.filter((item) => item.phase !== undefined).map<RouteObject>((item) => ({
  path: item.path.slice(1),
  element: guard(<ComingSoonPage module={item.module} phase={item.phase!} />, [item.module]),
}));

export const routes: RouteObject[] = [
  { path: "/login", element: <LoginPage /> },
  {
    path: "/",
    element: guard(<MainLayout />),
    children: [
      { index: true, element: <HomeRedirect /> },
      { path: "dashboard", element: guard(<DashboardPage />, ["DASHBOARD"]) },
      { path: "products", element: guard(<ProductsPage />, ["PRODUCTS"]) },
      { path: "users", element: guard(<UsersPage />, ["USER_MANAGEMENT"]) },
      { path: "users/:id/permissions", element: guard(<UserPermissionsPage />, ["USER_MANAGEMENT"]) },
      { path: "settings", element: guard(<SettingsPage />, ["SETTINGS", "ROLE_MANAGEMENT"]) },
      { path: "audit-logs", element: guard(<AuditLogsPage />, ["AUDIT_LOGS"]) },
      { path: "account/password", element: <ChangePasswordPage /> },
      ...comingSoon,
      { path: "*", element: <NotFoundPage /> },
    ],
  },
];

export const createAppRouter = () => createBrowserRouter(routes);
