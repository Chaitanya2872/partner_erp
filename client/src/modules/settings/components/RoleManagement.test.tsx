import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderApp } from "@/test/renderApp";
import { catalog, makeMe, roles, row } from "@/test/fixtures";
import { authService } from "@/services/auth.service";
import { roleService } from "../services/roleService";
import { ApiRequestError } from "@/services/api";

vi.mock("@/services/auth.service", () => ({
  authService: { restore: vi.fn(), me: vi.fn(), modules: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() },
}));
vi.mock("@/modules/settings/services/roleService", () => ({ roleService: { list: vi.fn(), update: vi.fn() } }));

beforeEach(() => {
  vi.mocked(authService.modules).mockResolvedValue(catalog);
  vi.mocked(roleService.list).mockResolvedValue(roles);
});

describe("role management", () => {
  it("lists roles with name, internal code and user count", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    renderApp("/roles");
    await screen.findByText("SUPER_ADMIN");
    const table = screen.getByRole("table");
    expect(within(table).getByRole("columnheader", { name: "Role name" })).toBeInTheDocument();
    expect(within(table).getByRole("columnheader", { name: "Internal code" })).toBeInTheDocument();
    expect(within(table).getByText("SUPER_ADMIN")).toBeInTheDocument();
    expect(within(table).getByText("5")).toBeInTheDocument();
  });

  it("renames a role and shows the new name from the server", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    vi.mocked(authService.me).mockResolvedValue(makeMe("SUPER_ADMIN"));
    vi.mocked(roleService.update).mockResolvedValue({ ...roles[1], displayName: "Operations Manager", version: 1 });
    renderApp("/roles");
    await userEvent.click(await screen.findByRole("button", { name: "Edit Admin" }));
    const input = screen.getByLabelText("Role name");
    await userEvent.clear(input);
    await userEvent.type(input, "  Operations   Manager ");
    vi.mocked(roleService.list).mockResolvedValue(roles.map((r) => (r.code === "ADMIN" ? { ...r, displayName: "Operations Manager" } : r)));
    await userEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(roleService.update).toHaveBeenCalledWith("role-ADMIN", { displayName: "Operations Manager", description: null, version: 0 });
    expect(await screen.findByText("Operations Manager")).toBeInTheDocument();
  });

  it("reports duplicate names", async () => {
    vi.mocked(authService.restore).mockResolvedValue(makeMe("SUPER_ADMIN"));
    vi.mocked(roleService.update).mockRejectedValue(new ApiRequestError(409, "DUPLICATE_ROLE_NAME", "dup"));
    renderApp("/roles");
    await userEvent.click(await screen.findByRole("button", { name: "Edit User" }));
    const input = screen.getByLabelText("Role name");
    await userEvent.clear(input);
    await userEvent.type(input, "admin");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(await screen.findByText("Another role already uses this name.")).toBeInTheDocument();
  });

  it("is read-only without Role Management write", async () => {
    vi.mocked(authService.restore).mockResolvedValue(
      makeMe("ADMIN", { modules: [row("DASHBOARD"), row("ROLE_MANAGEMENT")] }),
    );
    renderApp("/roles");
    expect(await screen.findByText("SUPER_ADMIN")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Edit/ })).not.toBeInTheDocument();
  });
});
