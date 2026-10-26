import { isValidElement, type ReactElement, type ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import type { RouteObject } from "react-router";
import { routes } from "./router";
import { renderApp } from "@/test/renderApp";
import { catalog, makeMe } from "@/test/fixtures";
import { authService } from "@/services/auth.service";

vi.mock("@/services/auth.service", () => ({
  authService: { restore: vi.fn(), me: vi.fn(), modules: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() },
}));
vi.mock("@/modules/reports/services/reportService", () => ({
  reportService: { catalog: vi.fn().mockResolvedValue([]), run: vi.fn(), exportCsv: vi.fn(), partnerOptions: vi.fn() },
}));

const LAZY = Symbol.for("react.lazy");

/** The innermost element of a route (unwrapping ProtectedRoute/Suspense wrappers). */
function pageElement(node: ReactNode): ReactElement | null {
  if (!isValidElement(node)) return null;
  const type = node.type as { $$typeof?: symbol };
  if (type.$$typeof === LAZY) return node;
  return pageElement((node.props as { children?: ReactNode }).children);
}

function allRoutes(list: RouteObject[]): RouteObject[] {
  return list.flatMap((r) => [r, ...allRoutes(r.children ?? [])]);
}

describe("route-level code splitting", () => {
  it("loads every page lazily, including the shell and the login page", () => {
    const paths = ["/login", "/", "dashboard", "products", "users", "users/:id/permissions", "roles", "audit-logs", "account/password", "leads", "leads/:id", "leads/new", "estimates", "estimates/new", "estimates/:id", "estimates/:id/edit", "invoices", "invoices/new", "invoices/:id", "invoices/:id/edit", "payments", "payments/:id", "payouts", "payouts/:id", "reports", "settings"];
    const byPath = new Map(allRoutes(routes).map((r) => [r.path, r]));
    for (const path of paths) {
      const route = byPath.get(path);
      expect(route, path).toBeDefined();
      expect(pageElement(route!.element), `${path} should render a React.lazy component`).not.toBeNull();
    }
  });

  it("renders a lazily loaded page behind the authenticated shell", async () => {
    vi.mocked(authService.modules).mockResolvedValue(catalog);
    vi.mocked(authService.restore).mockResolvedValue(makeMe("ADMIN"));
    renderApp("/reports");
    expect(await screen.findByRole("heading", { name: "Reports" })).toBeInTheDocument();
    expect(await screen.findByText("No reports are available.")).toBeInTheDocument();
  });
});
