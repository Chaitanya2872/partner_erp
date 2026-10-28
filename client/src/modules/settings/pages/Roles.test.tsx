import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderApp } from "@/test/renderApp";
import { catalog, makeMe, roles, row } from "@/test/fixtures";
import { authService } from "@/services/auth.service";
import { ApiRequestError } from "@/services/api";
import { roleService, type RolePermissions } from "../services/roleService";

vi.mock("@/services/auth.service", () => ({
  authService: { restore: vi.fn(), me: vi.fn(), modules: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() },
}));
vi.mock("../services/roleService", () => ({
  roleService: { list: vi.fn(), update: vi.fn(), permissions: vi.fn(), updatePermissions: vi.fn() },
}));

const userRole: RolePermissions = {
  roleId: "role-USER",
  code: "USER",
  displayName: "User",
  superAdmin: false,
  editable: true,
  modules: [row("DASHBOARD"), row("PRODUCTS")],
  userCount: 5,
  version: 2,
};

/** Role Management read only (no Super Admin). */
const roleReader = () => makeMe("ADMIN", { modules: [row("DASHBOARD"), row("ROLE_MANAGEMENT")] });

beforeEach(() => {
  vi.mocked(authService.modules).mockResolvedValue(catalog);
  vi.mocked(roleService.list).mockResolvedValue(roles);
  vi.mocked(roleService.permissions).mockResolvedValue(userRole);
});

describe("roles and default permissions", () => {
  it("links every role to its default permissions", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    renderApp("/roles");
    expect(await screen.findByRole("link", { name: "Default permissions of User" })).toHaveAttribute("href", "/roles/role-USER/permissions");
    expect(screen.getByRole("button", { name: "Edit User" })).toBeInTheDocument();
  });

  it("edits a role's module defaults without special actions and confirms that existing accounts keep theirs", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    vi.mocked(roleService.updatePermissions).mockResolvedValue({ ...userRole, version: 3 });
    renderApp("/roles/role-USER/permissions");
    expect(await screen.findByRole("checkbox", { name: "Products Read" })).toBeChecked();
    expect(screen.queryByRole("columnheader", { name: "Special actions" })).not.toBeInTheDocument();
    expect(screen.queryByRole("checkbox", { name: "Mark payout paid" })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("checkbox", { name: "Leads Read" }));
    await userEvent.click(screen.getByRole("button", { name: "Save defaults" }));
    expect(await screen.findByText("Leads: choose a data scope.")).toBeInTheDocument();
    expect(roleService.updatePermissions).not.toHaveBeenCalled();

    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Leads scope" }), "ALL");
    await userEvent.click(screen.getByRole("button", { name: "Save defaults" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/5 existing accounts keep their current permissions/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Save defaults" }));
    expect(roleService.updatePermissions).toHaveBeenCalledWith(
      "role-USER",
      expect.arrayContaining([expect.objectContaining({ moduleCode: "LEADS", read: true, dataScope: "ALL" })]),
      2,
    );
  });

  it("reports a concurrent change to the defaults", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    vi.mocked(roleService.updatePermissions).mockRejectedValue(new ApiRequestError(409, "CONFLICT", "Stale"));
    renderApp("/roles/role-USER/permissions");
    await userEvent.click(await screen.findByRole("checkbox", { name: "Partners Read" }));
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Partners scope" }), "ALL");
    await userEvent.click(screen.getByRole("button", { name: "Save defaults" }));
    await userEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Save defaults" }));
    expect(await screen.findByText(/Someone else changed these defaults/)).toBeInTheDocument();
  });

  it("is read-only when the server says the viewer can't edit", async () => {
    vi.mocked(authService.restore).mockResolvedValue(roleReader());
    vi.mocked(roleService.permissions).mockResolvedValue({ ...userRole, editable: false });
    renderApp("/roles/role-USER/permissions");
    expect(await screen.findByRole("checkbox", { name: "Products Read" })).toBeDisabled();
    expect(screen.getByText(/Only a Super Admin can change defaults/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Save defaults" })).not.toBeInTheDocument();
  });

  it("shows no matrix for the Super Admin role", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    vi.mocked(roleService.permissions).mockResolvedValue({ ...userRole, roleId: "role-SUPER_ADMIN", code: "SUPER_ADMIN", displayName: "Super Admin", superAdmin: true, editable: false, modules: [] });
    renderApp("/roles/role-SUPER_ADMIN/permissions");
    expect(await screen.findByText(/always has full access to every module/)).toBeInTheDocument();
    expect(screen.queryByRole("table", { name: "Permission matrix" })).not.toBeInTheDocument();
  });
});
