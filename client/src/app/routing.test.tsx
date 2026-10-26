import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import { renderApp } from "@/test/renderApp";
import { catalog, makeMe, roles } from "@/test/fixtures";
import { authService } from "@/services/auth.service";
import { productService } from "@/modules/products/services/productService";
import { roleService } from "@/modules/settings/services/roleService";
import { dashboardService } from "@/modules/dashboard/services/dashboardService";
import { reportService } from "@/modules/reports/services/reportService";

vi.mock("@/services/auth.service", () => ({
  authService: { restore: vi.fn(), me: vi.fn(), modules: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() },
}));
vi.mock("@/modules/products/services/productService", () => ({ productService: { list: vi.fn() } }));
vi.mock("@/modules/settings/services/roleService", () => ({ roleService: { list: vi.fn(), update: vi.fn() } }));
vi.mock("@/modules/dashboard/services/dashboardService", () => ({
  dashboardService: { overview: vi.fn(), sales: vi.fn(), partners: vi.fn(), products: vi.fn(), finance: vi.fn(), alerts: vi.fn() },
}));
vi.mock("@/modules/reports/services/reportService", () => ({
  reportService: { catalog: vi.fn(), run: vi.fn(), exportCsv: vi.fn(), partnerOptions: vi.fn() },
}));

const product = {
  id: "p1",
  sku: "STI-2M",
  category: "SMART_TOUCH_INTERFACES",
  categoryLabel: "Smart Touch Interfaces",
  partnerName: "Smart Touch Interface - 2M (STI-2M)",
  unit: "nos",
  hsn: null,
  partnerPrice: 5749,
  gstPercent: 18,
  active: true,
};

beforeEach(() => {
  vi.mocked(authService.modules).mockResolvedValue(catalog);
  vi.mocked(roleService.list).mockResolvedValue(roles);
  vi.mocked(dashboardService.alerts).mockResolvedValue({ restricted: [], groups: [], generatedAt: "2026-10-27T05:00:00Z" });
  vi.mocked(reportService.catalog).mockResolvedValue([]);
  vi.mocked(productService.list).mockResolvedValue({
    content: [product as never],
    meta: { page: 0, size: 50, totalElements: 1, totalPages: 1 },
  });
});

describe("protected routing and navigation", () => {
  it("sends anonymous visitors to the login page", async () => {
    vi.mocked(authService.restore).mockResolvedValue(null);
    renderApp("/users");
    expect(await screen.findByRole("heading", { name: "Sign in" })).toBeInTheDocument();
  });

  it("shows only the modules a User can read", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("USER"));
    renderApp("/dashboard");
    const nav = await screen.findByRole("navigation", { name: "Main navigation" });
    expect(await within(nav).findByRole("link", { name: "Dashboard" })).toBeInTheDocument();
    expect(within(nav).getByRole("link", { name: "Products" })).toBeInTheDocument();
    expect(within(nav).queryByRole("link", { name: "Leads" })).not.toBeInTheDocument();
    expect(within(nav).queryByRole("link", { name: "User Management" })).not.toBeInTheDocument();
    expect(within(nav).queryByRole("link", { name: "Settings" })).not.toBeInTheDocument();
  });

  it("uses module names from the catalogue for the sidebar", async () => {
    vi.mocked(authService.modules).mockResolvedValue(catalog.map((m) => (m.code === "PRODUCTS" ? { ...m, displayName: "Price Sheet" } : m)));
    vi.mocked(authService.restore).mockResolvedValue(makeMe("USER"));
    renderApp("/dashboard");
    const nav = await screen.findByRole("navigation", { name: "Main navigation" });
    expect(await within(nav).findByRole("link", { name: "Price Sheet" })).toBeInTheDocument();
  });

  it("shows the unauthorized page for a module without read access", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("USER"));
    renderApp("/users");
    expect(await screen.findByRole("heading", { name: /don't have access/i })).toBeInTheDocument();
  });

  it("forces a password change before anything else", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("USER", { mustChangePassword: true }));
    renderApp("/products");
    expect(await screen.findByRole("heading", { name: "Change password" })).toBeInTheDocument();
    expect(screen.getByText(/must set a new password/i)).toBeInTheDocument();
  });

  it("opens the reports module for a Reports reader and refuses it otherwise", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("ADMIN"));
    renderApp("/reports");
    expect(await screen.findByRole("heading", { name: "Reports" })).toBeInTheDocument();
    expect(screen.queryByText(/delivered in Phase 12/)).not.toBeInTheDocument();
  });

  it("refuses the reports module without Reports read", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("USER"));
    renderApp("/reports");
    expect(await screen.findByRole("heading", { name: /don't have access/i })).toBeInTheDocument();
    expect(reportService.catalog).not.toHaveBeenCalled();
  });

  it("hides internal B2C columns without VIEW_INTERNAL_B2C", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("USER"));
    renderApp("/products");
    expect(await screen.findByText("STI-2M")).toBeInTheDocument();
    expect(screen.queryByRole("columnheader", { name: "B2C price" })).not.toBeInTheDocument();
    expect(screen.queryByRole("columnheader", { name: "Landed cost" })).not.toBeInTheDocument();
  });

  it("shows internal B2C columns with VIEW_INTERNAL_B2C", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("ADMIN", {}, ["PRODUCTS:VIEW_INTERNAL_B2C"]));
    renderApp("/products");
    expect(await screen.findByRole("columnheader", { name: "B2C price" })).toBeInTheDocument();
  });
});
