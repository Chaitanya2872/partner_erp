import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderApp } from "@/test/renderApp";
import { setViewport } from "@/test/viewport";
import { catalog, makeMe, roles, row } from "@/test/fixtures";
import { authService } from "@/services/auth.service";
import { endSession } from "@/services/api";
import { productService } from "@/modules/products/services/productService";
import { userService } from "@/modules/users/services/userService";
import { roleService } from "@/modules/settings/services/roleService";

vi.mock("@/services/auth.service", () => ({
  authService: { restore: vi.fn(), me: vi.fn(), modules: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() },
}));
vi.mock("@/modules/products/services/productService", () => ({ productService: { list: vi.fn() } }));
vi.mock("@/modules/users/services/userService", () => ({
  userService: { list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), setStatus: vi.fn(), permissions: vi.fn(), replacePermissions: vi.fn() },
}));
vi.mock("@/modules/settings/services/roleService", () => ({ roleService: { list: vi.fn(), update: vi.fn() } }));

const emptyPage = { content: [], meta: { page: 0, size: 1, totalElements: 0, totalPages: 0 } };

beforeEach(() => {
  vi.mocked(authService.modules).mockResolvedValue(catalog);
  vi.mocked(authService.me).mockResolvedValue(makeMe("SUPER_ADMIN"));
  vi.mocked(productService.list).mockResolvedValue(emptyPage);
  vi.mocked(userService.list).mockResolvedValue(emptyPage);
  vi.mocked(roleService.list).mockResolvedValue(roles);
});

async function mainNav() {
  return screen.findByRole("navigation", { name: "Main navigation" });
}

describe("sidebar responsiveness", () => {
  it("shows the full sidebar with labels on desktop", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    renderApp("/dashboard");
    const nav = await mainNav();
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-collapsed", "false");
    expect(await within(nav).findByText("Dashboard")).toBeVisible();
    expect(within(nav).getByRole("button", { name: "Collapse sidebar" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Open navigation" })).not.toBeInTheDocument();
  });

  it("uses a compact icon rail on tablets", async () => {
    setViewport(900);
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    renderApp("/dashboard");
    const nav = await mainNav();
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-collapsed", "true");
    // Icon-only links keep their accessible names.
    expect(await within(nav).findByRole("link", { name: "Products" })).toBeInTheDocument();
    expect(within(nav).queryByRole("button", { name: /Collapse sidebar|Expand sidebar/ })).not.toBeInTheDocument();
  });

  it("hides the rail on phones and reacts to viewport changes", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    renderApp("/dashboard");
    await mainNav();
    act(() => setViewport(390));
    await waitFor(() => expect(screen.queryByTestId("sidebar")).not.toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Open navigation" })).toBeInTheDocument();
  });
});

describe("sidebar collapse", () => {
  it("collapses and expands on desktop and remembers the choice", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    const first = renderApp("/dashboard");
    const nav = await mainNav();
    await userEvent.click(within(nav).getByRole("button", { name: "Collapse sidebar" }));
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-collapsed", "true");
    const railLink = within(nav).getByRole("link", { name: "Dashboard" });
    expect(railLink).toHaveAttribute("aria-current", "page");
    // Regression: the tooltip wrapper once stringified NavLink's className function, unstyling the rail.
    expect(railLink.className).toContain("aria-[current=page]:bg-sidebar-active");
    expect(railLink.className).not.toContain("=>");
    expect(window.localStorage.getItem("iotiq.sidebar.collapsed")).toBe("true");
    first.unmount();

    renderApp("/dashboard");
    const again = await mainNav();
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-collapsed", "true");
    await userEvent.click(within(again).getByRole("button", { name: "Expand sidebar" }));
    expect(screen.getByTestId("sidebar")).toHaveAttribute("data-collapsed", "false");
    expect(window.localStorage.getItem("iotiq.sidebar.collapsed")).toBe("false");
  });
});

describe("mobile navigation", () => {
  it("opens the drawer, navigates and closes it", async () => {
    setViewport(390);
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    vi.mocked(productService.list).mockResolvedValue(emptyPage);
    const { router } = renderApp("/dashboard");
    const open = await screen.findByRole("button", { name: "Open navigation" });
    expect(screen.queryByRole("navigation", { name: "Main navigation" })).not.toBeInTheDocument();

    await userEvent.click(open);
    const drawer = await screen.findByRole("dialog", { name: "Navigation" });
    await userEvent.click(within(drawer).getByRole("link", { name: "Products" }));

    await waitFor(() => expect(router.state.location.pathname).toBe("/products"));
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Navigation" })).not.toBeInTheDocument());
  });

  it("closes the drawer with Escape", async () => {
    setViewport(360);
    vi.mocked(authService.restore).mockResolvedValue(makeMe("USER"));
    renderApp("/dashboard");
    await userEvent.click(await screen.findByRole("button", { name: "Open navigation" }));
    expect(await screen.findByRole("dialog", { name: "Navigation" })).toBeInTheDocument();
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Navigation" })).not.toBeInTheDocument());
  });
});

describe("permission-aware navigation", () => {
  it("shows the Administration group only with administration permissions", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    renderApp("/dashboard");
    const nav = await mainNav();
    expect(await within(nav).findByText("Administration")).toBeInTheDocument();
    expect(within(nav).getByRole("link", { name: "User Management" })).toBeInTheDocument();
    expect(within(nav).getByRole("link", { name: "Role Management" })).toBeInTheDocument();
    expect(within(nav).getByRole("link", { name: "Audit Logs" })).toBeInTheDocument();
    // Internal B2C is a permission inside Sales documents, never a sidebar module.
    expect(within(nav).queryByRole("link", { name: /Internal B2C/ })).not.toBeInTheDocument();
  });

  it("groups modules like the original application and hides unreadable ones", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("ADMIN"));
    renderApp("/dashboard");
    const nav = await mainNav();
    for (const group of ["Funnel", "Sales", "Master"]) {
      expect(await within(nav).findByText(group)).toBeInTheDocument();
    }
    expect(within(nav).getByRole("link", { name: "Leads" })).toBeInTheDocument();
    expect(within(nav).getByRole("link", { name: "Payouts" })).toBeInTheDocument();
    expect(within(nav).queryByText("Administration")).not.toBeInTheDocument();
    expect(within(nav).queryByRole("link", { name: "Settings" })).not.toBeInTheDocument();
  });

  it("shows Role Management to a user granted only that module", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("ADMIN", { modules: [row("DASHBOARD"), row("ROLE_MANAGEMENT")] }));
    renderApp("/dashboard");
    const nav = await mainNav();
    expect(await within(nav).findByRole("link", { name: "Role Management" })).toBeInTheDocument();
    expect(within(nav).queryByRole("link", { name: "User Management" })).not.toBeInTheDocument();
  });

  it("builds breadcrumbs from the navigation group and module", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    renderApp("/audit-logs");
    const crumbs = await screen.findByRole("navigation", { name: "Breadcrumb" });
    expect(within(crumbs).getByText("Administration")).toBeInTheDocument();
    expect(await within(crumbs).findByText("Audit Logs")).toHaveAttribute("aria-current", "page");
  });
});

describe("top bar", () => {
  it("shows the user and the role display name, and updates it after a rename", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    renderApp("/roles");
    expect(await screen.findByTestId("topbar-role")).toHaveTextContent("Super Admin");

    vi.mocked(roleService.update).mockResolvedValue({ ...roles[0], displayName: "Owner", version: 1 });
    vi.mocked(authService.me).mockResolvedValue({ ...makeMe("SUPER_ADMIN"), role: { id: "role-SUPER_ADMIN", code: "SUPER_ADMIN", displayName: "Owner" } });
    await userEvent.click(await screen.findByRole("button", { name: "Edit Super Admin" }));
    const input = screen.getByLabelText("Role name");
    await userEvent.clear(input);
    await userEvent.type(input, "Owner");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(screen.getByTestId("topbar-role")).toHaveTextContent("Owner"));
  });

  it("offers change password and sign out in the account menu", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("USER"));
    vi.mocked(authService.logout).mockResolvedValue(undefined);
    renderApp("/dashboard");
    await userEvent.click(await screen.findByRole("button", { name: "Account menu for Test User" }));
    const menu = await screen.findByRole("menu");
    expect(within(menu).getByText("user@example.test")).toBeInTheDocument();
    expect(within(menu).getByRole("menuitem", { name: "Change password" })).toBeInTheDocument();
    await userEvent.click(within(menu).getByRole("menuitem", { name: "Sign out" }));
    expect(await screen.findByRole("heading", { name: "Sign in" })).toBeInTheDocument();
    expect(authService.logout).toHaveBeenCalled();
  });
});

describe("session expiry", () => {
  it("returns to the sign-in page with an explanation when the session cannot be renewed", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("USER"));
    renderApp("/products");
    await mainNav();
    act(() => endSession());
    expect(await screen.findByRole("heading", { name: "Sign in" })).toBeInTheDocument();
    expect(screen.getByText("Your session has ended")).toBeInTheDocument();
  });
});
