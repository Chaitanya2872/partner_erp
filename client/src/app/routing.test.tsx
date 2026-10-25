import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import { renderApp } from "@/test/renderApp";
import { catalog, makeMe, roles } from "@/test/fixtures";
import { authService } from "@/services/auth.service";
import { productService } from "@/modules/products/services/productService";
import { roleService } from "@/modules/settings/services/roleService";

vi.mock("@/services/auth.service", () => ({
  authService: { restore: vi.fn(), me: vi.fn(), modules: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() },
}));
vi.mock("@/modules/products/services/productService", () => ({ productService: { list: vi.fn() } }));
vi.mock("@/modules/settings/services/roleService", () => ({ roleService: { list: vi.fn(), update: vi.fn() } }));

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

  it("labels unbuilt modules as not yet available", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("ADMIN"));
    renderApp("/leads");
    expect(await screen.findByText(/delivered in Phase 6/)).toBeInTheDocument();
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
