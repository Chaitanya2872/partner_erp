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
  userService: { list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), setStatus: vi.fn(), unlock: vi.fn(), resetPassword: vi.fn(), activity: vi.fn(), permissions: vi.fn(), previewPermissions: vi.fn(), replacePermissions: vi.fn() },
}));
vi.mock("@/modules/settings/services/roleService", () => ({
  roleService: { list: vi.fn(), update: vi.fn(), permissions: vi.fn(), updatePermissions: vi.fn() },
}));

const target: User = {
  id: "u1",
  fullName: "Asha Rao",
  email: "asha@example.test",
  role: { id: "role-ADMIN", code: "ADMIN", displayName: "Admin" },
  status: "ACTIVE",
  mustChangePassword: false,
  lastLoginAt: null,
  lockedUntil: null,
  locked: false,
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
  differsFromRole: [],
};

beforeEach(() => {
  vi.mocked(authService.modules).mockResolvedValue(catalog);
  vi.mocked(authService.me).mockResolvedValue(makeMe("SUPER_ADMIN"));
  vi.mocked(roleService.list).mockResolvedValue(roles);
  vi.mocked(userService.list).mockResolvedValue({ content: [target], meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 } });
  vi.mocked(userService.get).mockResolvedValue(target);
  vi.mocked(userService.permissions).mockResolvedValue(permissions);
  vi.mocked(userService.previewPermissions).mockResolvedValue({
    userId: "u1",
    changed: true,
    granted: ["PARTNERS:READ", "PARTNERS:WRITE"],
    revoked: [],
    scopeChanges: [{ moduleCode: "PARTNERS", from: null, to: "TEAM" }],
    effective: [],
    differsFromRole: ["PARTNERS"],
  });
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

  it("lets an Admin manage Admin accounts but not Super Admin accounts", async () => {
    const superTarget: User = { ...target, id: "u2", fullName: "Sue Super", email: "sue@example.test", role: { id: "role-SUPER_ADMIN", code: "SUPER_ADMIN", displayName: "Super Admin" } };
    vi.mocked(userService.list).mockResolvedValue({ content: [target, superTarget], meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 } });
    vi.mocked(authService.restore).mockResolvedValue(makeMe("ADMIN", { modules: makeMe("SUPER_ADMIN").modules }, ["USER_MANAGEMENT:MANAGE_USERS"]));
    renderApp("/users");
    expect(await screen.findByText("Sue Super")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Edit" })).toHaveLength(1);
    expect(screen.getAllByRole("button", { name: "Deactivate" })).toHaveLength(1);
  });

  it("asks for confirmation before deactivating", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    vi.mocked(userService.setStatus).mockResolvedValue({ ...target, status: "DISABLED", version: 4 });
    renderApp("/users");
    await userEvent.click(await screen.findByRole("button", { name: "Deactivate" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/signed out of every session/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Deactivate user" }));
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
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Partners: Write")).toBeInTheDocument();
    expect(within(dialog).getByText(/Partners: none → Team/)).toBeInTheDocument();
    expect(userService.replacePermissions).not.toHaveBeenCalled();
    await userEvent.click(within(dialog).getByRole("button", { name: "Save permissions" }));
    expect(userService.previewPermissions).toHaveBeenCalledWith("u1", expect.objectContaining({ actions: [] }));
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
    const dialog = await screen.findByRole("dialog", { name: "Grant sensitive permissions?" });
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

  it("offers Role Management and Manage permissions to an Admin editor (no Super-Admin-only items any more)", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("ADMIN", { modules: makeMe("SUPER_ADMIN").modules }, ["USER_MANAGEMENT:MANAGE_PERMISSIONS"]));
    renderApp("/users/u1/permissions");
    expect(await screen.findByRole("checkbox", { name: "Role Management Read" })).toBeEnabled();
    expect(screen.getByRole("checkbox", { name: "Manage permissions" })).toBeEnabled();
  });

  it("does not save when the preview shows nothing would change", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    vi.mocked(userService.previewPermissions).mockResolvedValue({
      userId: "u1", changed: false, granted: [], revoked: [], scopeChanges: [], effective: [], differsFromRole: [],
    });
    renderApp("/users/u1/permissions");
    await userEvent.click(await screen.findByRole("checkbox", { name: "Partners Read" }));
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Partners scope" }), "ALL");
    await userEvent.click(screen.getByRole("button", { name: "Save permissions" }));
    expect(await screen.findByText(/Nothing changes/)).toBeInTheDocument();
    expect(userService.replacePermissions).not.toHaveBeenCalled();
  });

  it("marks differences from the role and loads the role defaults on request", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    vi.mocked(userService.permissions).mockResolvedValue({ ...permissions, differsFromRole: ["LEADS", "PAYOUTS:MARK_PAYOUT_PAID"] });
    vi.mocked(roleService.permissions).mockResolvedValue({
      roleId: "role-ADMIN", code: "ADMIN", displayName: "Admin", superAdmin: false, editable: true,
      modules: [row("DASHBOARD"), row("PARTNERS", { write: true, dataScope: "ALL" })], userCount: 2, version: 1,
    });
    renderApp("/users/u1/permissions");
    expect(await screen.findByText("2 differences from the Admin defaults")).toBeInTheDocument();
    expect(screen.getByText(/Special actions granted: Payouts: Mark payout paid/)).toBeInTheDocument();
    expect(screen.getByText("Differs from role")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Reset to role defaults" }));
    expect(roleService.permissions).toHaveBeenCalledWith("role-ADMIN");
    expect(await screen.findByRole("checkbox", { name: "Partners Write" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Leads Read" })).not.toBeChecked();
    expect(userService.replacePermissions).not.toHaveBeenCalled();
  });

  it("filters locked accounts from the URL and unlocks after confirmation", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    const locked = { ...target, locked: true, lockedUntil: "2026-10-27T10:00:00Z" };
    vi.mocked(userService.list).mockResolvedValue({ content: [locked], meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 } });
    vi.mocked(userService.unlock).mockResolvedValue({ ...target, version: 4 });
    renderApp("/users?locked=true");
    expect(await screen.findByText("Asha Rao")).toBeInTheDocument();
    expect(userService.list).toHaveBeenCalledWith(expect.objectContaining({ locked: true, status: "" }), expect.anything());
    expect(screen.getByRole("combobox", { name: "Filter by status" })).toHaveValue("LOCKED");
    expect(screen.getByText(/^Locked until/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Unlock" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "Unlock account" }));
    expect(userService.unlock).toHaveBeenCalledWith("u1", 3);
  });

  it("resets a password only with a temporary password that meets the policy", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    vi.mocked(userService.resetPassword).mockResolvedValue({ ...target, mustChangePassword: true, version: 4 });
    renderApp("/users");
    await userEvent.click(await screen.findByRole("button", { name: "Reset password" }));
    const dialog = await screen.findByRole("dialog", { name: "Reset password" });
    expect(within(dialog).getByText(/signed out everywhere/)).toBeInTheDocument();
    const input = within(dialog).getByLabelText(/Temporary password/);
    await userEvent.type(input, "short1");
    await userEvent.click(within(dialog).getByRole("button", { name: "Reset password" }));
    expect(await within(dialog).findByText("Use at least 12 characters.")).toBeInTheDocument();
    expect(userService.resetPassword).not.toHaveBeenCalled();
    await userEvent.clear(input);
    await userEvent.type(input, "Temporary-Pass-2026");
    await userEvent.click(within(dialog).getByRole("button", { name: "Reset password" }));
    expect(userService.resetPassword).toHaveBeenCalledWith("u1", "Temporary-Pass-2026", 3);
  });

  it("offers no unlock or reset to a read-only viewer but shows account activity", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("ADMIN", { modules: [row("DASHBOARD"), row("USER_MANAGEMENT")] }));
    vi.mocked(userService.list).mockResolvedValue({
      content: [{ ...target, locked: true, lockedUntil: "2026-10-27T10:00:00Z" }],
      meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
    });
    vi.mocked(userService.activity).mockResolvedValue({
      content: [
        {
          id: "a1", createdAt: "2026-10-26T09:00:00Z", userId: null, actorName: null, actorEmail: null, action: "ACCOUNT_LOCKED",
          entityType: "USER", entityId: "u1", oldValues: null, newValues: null, ipAddress: null, requestId: null,
        },
      ],
      meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
    });
    renderApp("/users");
    expect(await screen.findByText("Asha Rao")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Unlock" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reset password" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Activity of Asha Rao" }));
    const dialog = await screen.findByRole("dialog", { name: "Account activity" });
    expect(await within(dialog).findByText("Account locked")).toBeInTheDocument();
    expect(userService.activity).toHaveBeenCalledWith("u1", 0, 20, expect.anything());
  });
});
