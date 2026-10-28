import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderApp } from "@/test/renderApp";
import { makeMe, row } from "@/test/fixtures";
import { authService } from "@/services/auth.service";
import { auditService } from "../services/auditService";
import type { AuditLog } from "../types/audit.types";

vi.mock("@/services/auth.service", () => ({
  authService: { restore: vi.fn(), me: vi.fn(), modules: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() },
}));
vi.mock("../services/auditService", () => ({
  auditService: { list: vi.fn(), catalog: vi.fn(), actors: vi.fn(), exportCsv: vi.fn() },
}));
vi.mock("@/utils/download", () => ({ saveTextFile: vi.fn() }));

const entry: AuditLog = {
  id: "e1",
  createdAt: "2026-10-26T09:00:00Z",
  userId: "sa",
  actorName: "Test Super Admin",
  actorEmail: "super_admin@example.test",
  action: "PARTNER_TIER_UPDATED",
  entityType: "PARTNER_TIER",
  entityId: "tier-2",
  oldValues: { commissionPercent: "10" },
  newValues: { commissionPercent: "10.5" },
  ipAddress: "127.0.0.1",
  requestId: "req-1",
};

const page = (content: AuditLog[]) => ({ content, meta: { page: 0, size: 50, totalElements: content.length, totalPages: content.length ? 1 : 0 } });

/** Audit logs read only. */
const reader = () => makeMe("ADMIN", { modules: [row("DASHBOARD"), row("AUDIT_LOGS")] });
/** Audit logs read + export. */
const exporter = () => makeMe("ADMIN", { modules: [row("DASHBOARD"), row("AUDIT_LOGS", { export: true })] });

beforeEach(() => {
  vi.mocked(authService.modules).mockResolvedValue([]);
  vi.mocked(auditService.list).mockResolvedValue(page([entry]));
  vi.mocked(auditService.catalog).mockResolvedValue([
    { code: "SETTINGS", label: "Settings", actions: ["COMPANY_SETTINGS_UPDATED", "PARTNER_TIER_UPDATED"] },
    { code: "AUTH", label: "Sign-in", actions: ["LOGIN_FAILED", "ACCOUNT_LOCKED"] },
  ]);
  vi.mocked(auditService.actors).mockResolvedValue([{ id: "sa", fullName: "Test Super Admin", email: "super_admin@example.test" }]);
});

describe("audit log viewer", () => {
  it("filters by module, narrowing the action list to that module", async () => {
    vi.mocked(authService.restore).mockResolvedValue(reader());
    renderApp("/audit-logs");
    expect(await screen.findByText("127.0.0.1")).toBeInTheDocument();
    const module = screen.getByRole("combobox", { name: "Filter by module" });
    await waitFor(() => expect(screen.getByRole("option", { name: "Settings" })).toBeInTheDocument());
    await userEvent.selectOptions(module, "SETTINGS");
    const actions = screen.getByRole("combobox", { name: "Filter by action" });
    expect(Array.from((actions as HTMLSelectElement).options).map((o) => o.value)).toEqual(["", "COMPANY_SETTINGS_UPDATED", "PARTNER_TIER_UPDATED"]);
    await waitFor(() => expect(auditService.list).toHaveBeenLastCalledWith(expect.objectContaining({ module: "SETTINGS", page: 0 }), expect.anything()));
  });

  it("searches metadata with a debounced query", async () => {
    vi.mocked(authService.restore).mockResolvedValue(reader());
    renderApp("/audit-logs");
    await screen.findByText("127.0.0.1");
    await userEvent.type(screen.getByRole("searchbox", { name: "Search audit logs" }), "req-1");
    await waitFor(() => expect(auditService.list).toHaveBeenLastCalledWith(expect.objectContaining({ q: "req-1" }), expect.anything()));
  });

  it("hides export without the export permission", async () => {
    vi.mocked(authService.restore).mockResolvedValue(reader());
    renderApp("/audit-logs");
    await screen.findByText("127.0.0.1");
    expect(screen.queryByRole("button", { name: /Export CSV/ })).not.toBeInTheDocument();
  });

  it("exports the filtered events for a user with export permission", async () => {
    vi.mocked(authService.restore).mockResolvedValue(exporter());
    vi.mocked(auditService.exportCsv).mockResolvedValue({ text: "When,Action\n", filename: "iotiq-audit-log-2026-10-27.csv" });
    const { saveTextFile } = await import("@/utils/download");
    renderApp("/audit-logs");
    await screen.findByText("127.0.0.1");
    await userEvent.click(screen.getByRole("button", { name: /Export CSV/ }));
    await waitFor(() => expect(saveTextFile).toHaveBeenCalledWith("When,Action\n", "iotiq-audit-log-2026-10-27.csv"));
    expect(auditService.exportCsv).toHaveBeenCalledWith(expect.objectContaining({ module: "", q: "" }));
  });

  it("shows an empty state with a way to clear filters", async () => {
    vi.mocked(authService.restore).mockResolvedValue(reader());
    vi.mocked(auditService.list).mockResolvedValue(page([]));
    renderApp("/audit-logs");
    expect(await screen.findByText("No events recorded")).toBeInTheDocument();
  });
});
