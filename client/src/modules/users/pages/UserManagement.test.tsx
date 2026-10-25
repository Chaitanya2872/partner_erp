import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderApp } from "@/test/renderApp";
import { catalog, makeMe, roles, row } from "@/test/fixtures";
import { authService } from "@/services/auth.service";
import { userService } from "../services/userService";
import { roleService } from "@/modules/settings/services/roleService";
import type { User, UserPermissions } from "../types/user.types";

vi.mock("@/services/auth.service", () => ({
  authService: { restore: vi.fn(), me: vi.fn(), modules: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() },
}));
vi.mock("../services/userService", () => ({
  userService: { list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), setStatus: vi.fn(), permissions: vi.fn(), replacePermissions: vi.fn() },
}));
vi.mock("@/modules/settings/services/roleService", () => ({ roleService: { list: vi.fn(), update: vi.fn() } }));

const target: User = {
  id: "u1",
  fullName: "Asha Rao",
  email: "asha@example.test",
  role: { id: "role-ADMIN", code: "ADMIN", displayName: "Admin" },
  status: "ACTIVE",
  mustChangePassword: false,
  lastLoginAt: null,
  lockedUntil: null,
  createdAt: "2026-10-25T00:00:00Z",
  updatedAt: "2026-10-25T00:00:00Z",
  version: 3,
};

const permissions: UserPermissions = {
  userId: "u1",
  role: target.role,
  superAdmin: false,
  editable: true,
  modules: [row("DASHBOARD"), row("LEADS", { dataScope: "ALL" })],
  actions: [],
};

beforeEach(() => {
  vi.mocked(authService.modules).mockResolvedValue(catalog);
  vi.mocked(authService.me).mockResolvedValue(makeMe("SUPER_ADMIN"));
  vi.mocked(roleService.list).mockResolvedValue(roles);
  vi.mocked(userService.list).mockResolvedValue({ content: [target], meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 } });
  vi.mocked(userService.get).mockResolvedValue(target);
  vi.mocked(userService.permissions).mockResolvedValue(permissions);
});

describe("user management", () => {
  it("shows create, edit and status actions to a Super Admin", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    renderApp("/users");
    expect(await screen.findByText("Asha Rao")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /New user/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Edit" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Deactivate" })).toBeInTheDocument();
  });

  it("hides write actions from a read-only viewer", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("ADMIN", { modules: [row("DASHBOARD"), row("USER_MANAGEMENT")] }));
    renderApp("/users");
    expect(await screen.findByText("Asha Rao")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /New user/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Deactivate" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Permissions/ })).toBeInTheDocument();
  });

  it("asks for confirmation before deactivating", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    vi.mocked(userService.setStatus).mockResolvedValue({ ...target, status: "DISABLED", version: 4 });
    renderApp("/users");
    await userEvent.click(await screen.findByRole("button", { name: "Deactivate" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/signed out of every session/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Deactivate" }));
    expect(userService.setStatus).toHaveBeenCalledWith("u1", "DISABLED", 3);
  });

  it("saves the permission matrix with dependencies applied", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    vi.mocked(userService.replacePermissions).mockResolvedValue(permissions);
    renderApp("/users/u1/permissions");
    const write = await screen.findByRole("checkbox", { name: "Partners Write" });
    await userEvent.click(write);
    expect(screen.getByRole("checkbox", { name: "Partners Read" })).toBeChecked();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Partners scope" }), "TEAM");
    await userEvent.click(screen.getByRole("button", { name: "Save permissions" }));
    expect(userService.replacePermissions).toHaveBeenCalledWith("u1", {
      modules: [
        row("DASHBOARD"),
        row("PARTNERS", { write: true, dataScope: "TEAM" }),
        row("LEADS", { dataScope: "ALL" }),
      ],
      actions: [],
    });
  });

  it("requires a scope choice and confirms sensitive grants", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    vi.mocked(userService.replacePermissions).mockResolvedValue(permissions);
    renderApp("/users/u1/permissions");
    await userEvent.click(await screen.findByRole("checkbox", { name: "Payouts Read" }));
    await userEvent.click(screen.getByRole("button", { name: "Save permissions" }));
    expect(await screen.findByText("Payouts: choose a data scope.")).toBeInTheDocument();
    expect(userService.replacePermissions).not.toHaveBeenCalled();

    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Payouts scope" }), "ALL");
    await userEvent.click(screen.getByRole("checkbox", { name: "Mark payout paid" }));
    await userEvent.click(screen.getByRole("button", { name: "Save permissions" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Mark payout paid")).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Save permissions" }));
    expect(userService.replacePermissions).toHaveBeenCalledTimes(1);
  });

  it("renders a read-only matrix when the server marks it not editable", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("ADMIN", { modules: [row("DASHBOARD"), row("USER_MANAGEMENT")] }));
    vi.mocked(userService.permissions).mockResolvedValue({ ...permissions, editable: false });
    renderApp("/users/u1/permissions");
    expect(await screen.findByText(/Read-only view/)).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Leads Read" })).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Save permissions" })).not.toBeInTheDocument();
  });

  it("never offers role-reserved modules or actions", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    renderApp("/users/u1/permissions");
    expect(await screen.findByRole("checkbox", { name: "Role Management Read" })).toBeDisabled();
    expect(screen.getByRole("checkbox", { name: "Manage permissions" })).toBeDisabled();
  });
});
