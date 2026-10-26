import { lazy, Suspense, type ComponentType, type ReactNode } from "react";
import { createBrowserRouter, Navigate, type RouteObject } from "react-router";
import { ProtectedRoute } from "@/layouts/ProtectedRoute";
import { RouterErrorPage } from "@/components/feedback/ErrorBoundary";
import { EmptyState, LoadingState } from "@/components/feedback/States";
import { NotFoundPage } from "@/pages/NotFoundPage";
import { useAuth } from "@/hooks/useAuth";
import { NAV_ITEMS, isVisible } from "./navigation";
import type { RouteHandle } from "./breadcrumbs";
import type { ModuleCode } from "@/types/common.types";

/*
 * Route-level code splitting: every page is its own chunk, loaded on first visit (the shell's Suspense boundary
 * shows a skeleton meanwhile). The login page is split too, so signed-in users never download it and vice versa.
 */
const named = <K extends string, P extends object>(loader: () => Promise<Record<K, ComponentType<P>>>, name: K) =>
  lazy(() => loader().then((m) => ({ default: m[name] })));

const MainLayout = named(() => import("@/layouts/MainLayout"), "MainLayout");
const LoginPage = named(() => import("@/modules/auth/pages/LoginPage"), "LoginPage");
const ChangePasswordPage = named(() => import("@/modules/auth/pages/ChangePasswordPage"), "ChangePasswordPage");
const DashboardPage = named(() => import("@/modules/dashboard/pages/DashboardPage"), "DashboardPage");
const ProductsPage = named(() => import("@/modules/products/pages/ProductsPage"), "ProductsPage");
const ProductDetailsPage = named(() => import("@/modules/products/pages/ProductDetailsPage"), "ProductDetailsPage");
const ProductFormPage = named(() => import("@/modules/products/pages/ProductFormPage"), "ProductFormPage");
const PartnersPage = named(() => import("@/modules/partners/pages/PartnersPage"), "PartnersPage");
const PartnerDetailsPage = named(() => import("@/modules/partners/pages/PartnerDetailsPage"), "PartnerDetailsPage");
const PartnerFormPage = named(() => import("@/modules/partners/pages/PartnerFormPage"), "PartnerFormPage");
const LeadsPage = named(() => import("@/modules/leads/pages/LeadsPage"), "LeadsPage");
const LeadDetailsPage = named(() => import("@/modules/leads/pages/LeadDetailsPage"), "LeadDetailsPage");
const LeadFormPage = named(() => import("@/modules/leads/pages/LeadFormPage"), "LeadFormPage");
const EstimatesPage = named(() => import("@/modules/estimates/pages/EstimatesPage"), "EstimatesPage");
const EstimateDetailsPage = named(() => import("@/modules/estimates/pages/EstimateDetailsPage"), "EstimateDetailsPage");
const EstimateFormPage = named(() => import("@/modules/estimates/pages/EstimateFormPage"), "EstimateFormPage");
const InvoicesPage = named(() => import("@/modules/invoices/pages/InvoicesPage"), "InvoicesPage");
const InvoiceDetailsPage = named(() => import("@/modules/invoices/pages/InvoiceDetailsPage"), "InvoiceDetailsPage");
const InvoiceFormPage = named(() => import("@/modules/invoices/pages/InvoiceFormPage"), "InvoiceFormPage");
const PaymentsPage = named(() => import("@/modules/payments/pages/PaymentsPage"), "PaymentsPage");
const PaymentDetailsPage = named(() => import("@/modules/payments/pages/PaymentDetailsPage"), "PaymentDetailsPage");
const SettingsPage = named(() => import("@/modules/settings/pages/SettingsPage"), "SettingsPage");
const UsersPage = named(() => import("@/modules/users/pages/UsersPage"), "UsersPage");
const UserPermissionsPage = named(() => import("@/modules/users/pages/UserPermissionsPage"), "UserPermissionsPage");
const RolesPage = named(() => import("@/modules/settings/pages/RolesPage"), "RolesPage");
const AuditLogsPage = named(() => import("@/modules/audit/pages/AuditLogsPage"), "AuditLogsPage");
const ComingSoonPage = named(() => import("@/pages/ComingSoonPage"), "ComingSoonPage");

/** Sends the user to the first module they can read (the dashboard for most). */
function HomeRedirect() {
  const auth = useAuth();
  const first = NAV_ITEMS.find((item) => isVisible(item, auth));
  return first ? (
    <Navigate to={first.path} replace />
  ) : (
    <EmptyState title="No modules assigned">Your account has no module access yet. Ask an administrator to assign permissions.</EmptyState>
  );
}

const guard = (element: ReactNode, anyOf?: ModuleCode[]) => <ProtectedRoute anyOf={anyOf}>{element}</ProtectedRoute>;
const handle = (h: RouteHandle) => h;

const comingSoon = NAV_ITEMS.filter((item) => item.phase !== undefined).map<RouteObject>((item) => ({
  path: item.path.slice(1),
  handle: handle({ nav: item.path }),
  element: guard(<ComingSoonPage module={item.module} phase={item.phase!} />, [item.module]),
}));

export const routes: RouteObject[] = [
  {
    path: "/login",
    element: (
      <Suspense fallback={<LoadingState fullPage label="Loading…" />}>
        <LoginPage />
      </Suspense>
    ),
    errorElement: <RouterErrorPage />,
  },
  {
    path: "/",
    element: guard(
      <Suspense fallback={<LoadingState fullPage label="Loading…" />}>
        <MainLayout />
      </Suspense>,
    ),
    errorElement: <RouterErrorPage />,
    children: [
      { index: true, element: <HomeRedirect /> },
      { path: "dashboard", handle: handle({ nav: "/dashboard" }), element: guard(<DashboardPage />, ["DASHBOARD"]) },
      { path: "partners", handle: handle({ nav: "/partners" }), element: guard(<PartnersPage />, ["PARTNERS"]) },
      { path: "partners/new", handle: handle({ nav: "/partners", crumb: "New partner" }), element: guard(<PartnerFormPage />, ["PARTNERS"]) },
      { path: "partners/:id", handle: handle({ nav: "/partners", crumb: "Details" }), element: guard(<PartnerDetailsPage />, ["PARTNERS"]) },
      {
        path: "partners/:id/edit",
        handle: handle({ nav: "/partners", crumb: "Edit" }),
        element: guard(<PartnerFormPage />, ["PARTNERS"]),
      },
      { path: "leads", handle: handle({ nav: "/leads" }), element: guard(<LeadsPage />, ["LEADS"]) },
      { path: "leads/new", handle: handle({ nav: "/leads", crumb: "New lead" }), element: guard(<LeadFormPage />, ["LEADS"]) },
      { path: "leads/:id", handle: handle({ nav: "/leads", crumb: "Details" }), element: guard(<LeadDetailsPage />, ["LEADS"]) },
      { path: "leads/:id/edit", handle: handle({ nav: "/leads", crumb: "Edit" }), element: guard(<LeadFormPage />, ["LEADS"]) },
      { path: "estimates", handle: handle({ nav: "/estimates" }), element: guard(<EstimatesPage />, ["ESTIMATES"]) },
      {
        path: "estimates/new",
        handle: handle({ nav: "/estimates", crumb: "New estimate" }),
        element: guard(<EstimateFormPage />, ["ESTIMATES"]),
      },
      { path: "estimates/:id", handle: handle({ nav: "/estimates", crumb: "Details" }), element: guard(<EstimateDetailsPage />, ["ESTIMATES"]) },
      {
        path: "estimates/:id/edit",
        handle: handle({ nav: "/estimates", crumb: "Edit" }),
        element: guard(<EstimateFormPage />, ["ESTIMATES"]),
      },
      { path: "invoices", handle: handle({ nav: "/invoices" }), element: guard(<InvoicesPage />, ["INVOICES"]) },
      { path: "invoices/new", handle: handle({ nav: "/invoices", crumb: "New invoice" }), element: guard(<InvoiceFormPage />, ["INVOICES"]) },
      { path: "invoices/:id", handle: handle({ nav: "/invoices", crumb: "Details" }), element: guard(<InvoiceDetailsPage />, ["INVOICES"]) },
      { path: "invoices/:id/edit", handle: handle({ nav: "/invoices", crumb: "Edit" }), element: guard(<InvoiceFormPage />, ["INVOICES"]) },
      { path: "payments", handle: handle({ nav: "/payments" }), element: guard(<PaymentsPage />, ["PAYMENTS"]) },
      { path: "payments/:id", handle: handle({ nav: "/payments", crumb: "Details" }), element: guard(<PaymentDetailsPage />, ["PAYMENTS"]) },
      { path: "products", handle: handle({ nav: "/products" }), element: guard(<ProductsPage />, ["PRODUCTS"]) },
      { path: "products/new", handle: handle({ nav: "/products", crumb: "New product" }), element: guard(<ProductFormPage />, ["PRODUCTS"]) },
      { path: "products/:id", handle: handle({ nav: "/products", crumb: "Details" }), element: guard(<ProductDetailsPage />, ["PRODUCTS"]) },
      { path: "products/:id/edit", handle: handle({ nav: "/products", crumb: "Edit" }), element: guard(<ProductFormPage />, ["PRODUCTS"]) },
      { path: "settings", handle: handle({ nav: "/settings" }), element: guard(<SettingsPage />, ["SETTINGS"]) },
      { path: "users", handle: handle({ nav: "/users" }), element: guard(<UsersPage />, ["USER_MANAGEMENT"]) },
      {
        path: "users/:id/permissions",
        handle: handle({ nav: "/users", crumb: "Permissions" }),
        element: guard(<UserPermissionsPage />, ["USER_MANAGEMENT"]),
      },
      { path: "roles", handle: handle({ nav: "/roles" }), element: guard(<RolesPage />, ["ROLE_MANAGEMENT"]) },
      { path: "audit-logs", handle: handle({ nav: "/audit-logs" }), element: guard(<AuditLogsPage />, ["AUDIT_LOGS"]) },
      { path: "account/password", handle: handle({ crumb: "Change password" }), element: <ChangePasswordPage /> },
      ...comingSoon,
      { path: "*", handle: handle({ crumb: "Not found" }), element: <NotFoundPage /> },
    ],
  },
];

export const createAppRouter = () => createBrowserRouter(routes);
