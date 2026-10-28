import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderApp } from "@/test/renderApp";
import { makeMe, row } from "@/test/fixtures";
import { resetViewport, setViewport } from "@/test/viewport";
import { authService } from "@/services/auth.service";
import { ApiRequestError } from "@/services/api";
import { payoutService } from "@/modules/payouts/services/payoutService";
import { settingsService } from "../services/settingsService";
import type { CompanySettings, PartnerTier, PartnerTiers, SecurityPolicy } from "../types/settings.types";

vi.mock("@/services/auth.service", () => ({
  authService: { restore: vi.fn(), me: vi.fn(), modules: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() },
}));
vi.mock("../services/settingsService", () => ({
  settingsService: {
    gstRegistration: vi.fn(),
    updateGstRegistration: vi.fn(),
    company: vi.fn(),
    updateCompany: vi.fn(),
    updateCommercial: vi.fn(),
    tiers: vi.fn(),
    updateTiers: vi.fn(),
    numbering: vi.fn(),
    security: vi.fn(),
    controls: vi.fn(),
  },
}));
vi.mock("@/modules/payouts/services/payoutService", () => ({
  payoutService: { tdsConfiguration: vi.fn(), saveTdsDraft: vi.fn(), confirmTds: vi.fn() },
}));

const company: CompanySettings = {
  legalName: "IOTIQ Test Pvt Ltd",
  address: null,
  stateCode: "29",
  stateName: "Karnataka",
  gstin: null,
  phone: "+91 80 1234 5678",
  email: "accounts@example.test",
  terms: "Payment within 30 days.",
  estimateValidityDays: 30,
  invoiceDueDays: 30,
  defaultGstPercent: "18",
  acknowledgementHours: 24,
  estimateSlaDays: 3,
  leadProtectionDays: 90,
  clawbackDays: 90,
  updatedAt: "2026-10-20T05:00:00Z",
  updatedBy: "Test Super Admin",
  version: 7,
  canEdit: true,
  canManageCommercial: true,
  limits: { maxTerms: 5000, maxValidityDays: 365, maxDueDays: 365, maxAcknowledgementHours: 720, maxSlaDays: 365, maxProtectionDays: 730 },
};

const tier = (level: number, name: string, fee: string, min: string, days: number): PartnerTier => ({
  id: `tier-${level}`,
  level,
  name,
  commissionPercent: fee,
  minimumRevenue: min,
  payoutBusinessDays: days,
  partners: level,
  updatedAt: null,
  updatedBy: null,
  version: 1,
});
const tiers: PartnerTiers = {
  tiers: [tier(1, "Registered", "8", "0", 15), tier(2, "Preferred", "10", "1000000", 12), tier(3, "Elite", "12", "3000000", 10), tier(4, "Strategic", "15", "10000000", 7)],
  canEdit: true,
};

const security: SecurityPolicy = {
  password: { minLength: 12, maxLength: 128, rules: ["12 to 128 characters", "At least one letter and one digit"] },
  lockout: { maxFailedAttempts: 5, lockoutMinutes: 15, unlock: "The lock ends by itself, or an administrator can unlock it." },
  sessions: { accessTokenMinutes: 15, refreshTokenDays: 7, rules: ["Refresh tokens rotate on every use"] },
  lockedAccounts: 2,
  pendingPasswordChanges: 1,
};

/** Settings write without Manage commercial settings. */
const settingsWriter = () => makeMe("ADMIN", { modules: [row("DASHBOARD"), row("SETTINGS", { write: true })] });
/** Settings read only. */
const settingsReader = () => makeMe("USER", { modules: [row("DASHBOARD"), row("SETTINGS")] });

function signIn(me: ReturnType<typeof makeMe>) {
  vi.mocked(authService.restore).mockResolvedValue(me);
  vi.mocked(authService.me).mockResolvedValue(me);
}

beforeEach(() => {
  vi.mocked(authService.modules).mockResolvedValue([]);
  vi.mocked(settingsService.gstRegistration).mockResolvedValue({
    legalName: "IOTIQ Test Pvt Ltd",
    address: null,
    stateCode: "29",
    stateName: "Karnataka",
    gstin: null,
    einvoicingApplicable: null,
    complete: false,
    version: 2,
    states: [],
  } as never);
  vi.mocked(settingsService.company).mockResolvedValue(company);
  vi.mocked(settingsService.tiers).mockResolvedValue(tiers);
  vi.mocked(settingsService.numbering).mockResolvedValue([
    { prefix: "INV", document: "Invoice", format: "INV-YYYY-NNNNNN", example: "INV-2026-000001", allocatedWhen: "When issued", year: 2026, lastValue: 12, capacity: 999999 },
  ]);
  vi.mocked(settingsService.security).mockResolvedValue(security);
  vi.mocked(settingsService.controls).mockResolvedValue([
    { area: "Payouts", control: "Maker-checker: the approver can't be the creator", permission: "PAYOUTS:APPROVE", enforcedBy: "Server", reference: "L-8" },
  ]);
  vi.mocked(payoutService.tdsConfiguration).mockResolvedValue({ configured: false, inForce: null, draft: null, history: [], canEdit: false, canConfirm: false });
});

afterEach(() => resetViewport());

describe("settings and administration", () => {
  it("opens on the company section and switches sections through the URL", async () => {
    signIn(makeMe("SUPER_ADMIN"));
    const { router } = renderApp("/settings");
    expect(await screen.findByRole("heading", { name: "Contact details" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Company" })).toHaveAttribute("aria-selected", "true");
    await userEvent.click(screen.getByRole("tab", { name: "Tiers & commission" }));
    expect(await screen.findByRole("table", { name: "Partner tiers" })).toBeInTheDocument();
    expect(router.state.location.search).toContain("section=commission");
  });

  it("validates company details before saving and sends the whole record with its version", async () => {
    signIn(makeMe("SUPER_ADMIN"));
    vi.mocked(settingsService.updateCompany).mockResolvedValue(company);
    renderApp("/settings");
    await userEvent.click(await screen.findByRole("button", { name: "Edit contact details" }));
    const form = screen.getByRole("form", { name: "Edit contact details" });
    const email = within(form).getByRole("textbox", { name: /Email/ });
    await userEvent.clear(email);
    await userEvent.type(email, "not-an-email");
    await userEvent.click(within(form).getByRole("button", { name: "Save" }));
    expect(await within(form).findByText("Enter a valid email address.")).toBeInTheDocument();
    expect(settingsService.updateCompany).not.toHaveBeenCalled();

    await userEvent.clear(email);
    await userEvent.type(email, "billing@example.test");
    await userEvent.click(within(form).getByRole("button", { name: "Save" }));
    expect(settingsService.updateCompany).toHaveBeenCalledWith(
      expect.objectContaining({ email: "billing@example.test", phone: company.phone, estimateValidityDays: 30, invoiceDueDays: 30, version: 7 }),
    );
  });

  it("explains a concurrent change instead of overwriting it", async () => {
    signIn(makeMe("SUPER_ADMIN"));
    vi.mocked(settingsService.updateCompany).mockRejectedValue(new ApiRequestError(409, "CONFLICT", "Stale", []));
    renderApp("/settings?section=preferences");
    await userEvent.click(await screen.findByRole("button", { name: "Edit lead service levels" }));
    await userEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(await screen.findByText(/Someone else changed these settings/)).toBeInTheDocument();
  });

  it("checks tier values and confirms before saving forward-only rates", async () => {
    signIn(makeMe("SUPER_ADMIN"));
    vi.mocked(settingsService.updateTiers).mockResolvedValue(tiers);
    renderApp("/settings?section=commission");
    await userEvent.click(await screen.findByRole("button", { name: "Edit partner tiers" }));
    const elite = screen.getByRole("textbox", { name: "Elite minimum revenue" });
    await userEvent.clear(elite);
    await userEvent.type(elite, "500000");
    await userEvent.click(screen.getByRole("button", { name: "Save tiers" }));
    expect(await screen.findByText("Must be higher than the tier below.")).toBeInTheDocument();
    expect(settingsService.updateTiers).not.toHaveBeenCalled();

    await userEvent.clear(elite);
    await userEvent.type(elite, "3000000");
    const fee = screen.getByRole("textbox", { name: "Preferred fee %" });
    await userEvent.clear(fee);
    await userEvent.type(fee, "10.5");
    await userEvent.click(screen.getByRole("button", { name: "Save tiers" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/Nothing already stored is recalculated/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Save tiers" }));
    expect(settingsService.updateTiers).toHaveBeenCalledWith(
      expect.arrayContaining([expect.objectContaining({ level: 2, commissionPercent: "10.5", version: 1 })]),
    );
  });

  it("follows the server's permissions: Settings write without Manage commercial settings can't change tiers or protection", async () => {
    signIn(settingsWriter());
    vi.mocked(settingsService.company).mockResolvedValue({ ...company, canManageCommercial: false });
    vi.mocked(settingsService.tiers).mockResolvedValue({ ...tiers, canEdit: false });
    renderApp("/settings?section=commission");
    expect(await screen.findByRole("table", { name: "Partner tiers" })).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "Lead protection" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit partner tiers" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit lead protection" })).not.toBeInTheDocument();
    expect(screen.getByText(/carried forward as a deduction/)).toBeInTheDocument();
  });

  it("shows a read-only view to a Settings reader, with no administration links", async () => {
    signIn(settingsReader());
    vi.mocked(settingsService.company).mockResolvedValue({ ...company, canEdit: false, canManageCommercial: false });
    renderApp("/settings?section=documents");
    expect(await screen.findByRole("heading", { name: "Document defaults" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Edit/ })).not.toBeInTheDocument();
    expect(await screen.findByRole("table", { name: "Number series" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("tab", { name: "Users & access" }));
    expect(await screen.findByText(/don't have access to user, role or audit administration/)).toBeInTheDocument();
  });

  it("lists the GST treatment and keeps unsupported scenarios blocked", async () => {
    signIn(makeMe("SUPER_ADMIN"));
    renderApp("/settings?section=tax");
    expect(await screen.findByRole("heading", { name: "GST treatment" })).toBeInTheDocument();
    for (const blocked of ["Exports and SEZ supplies", "Reverse charge", "Composition scheme", "Credit and debit notes"]) {
      expect(screen.getByText(blocked)).toBeInTheDocument();
    }
    expect(await screen.findByText("18%")).toBeInTheDocument();
  });

  it("shows the TDS configuration from Payouts in its own section", async () => {
    signIn(makeMe("SUPER_ADMIN"));
    renderApp("/settings?section=tds");
    await screen.findByRole("tab", { name: "TDS", selected: true });
    expect(payoutService.tdsConfiguration).toHaveBeenCalled();
  });

  it("shows the security policy and links locked accounts to the user list", async () => {
    signIn(makeMe("SUPER_ADMIN"));
    renderApp("/settings?section=security");
    expect(await screen.findByText("2 accounts are locked")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Show locked accounts" })).toHaveAttribute("href", "/users?locked=true");
    expect(screen.getByText(/Access token 15 minutes; refresh token 7 days/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Edit/ })).not.toBeInTheDocument();
  });

  it("shows approval controls read-only", async () => {
    signIn(makeMe("SUPER_ADMIN"));
    renderApp("/settings?section=controls");
    const table = await screen.findByRole("table", { name: "Approval and financial controls" });
    expect(within(table).getByText("L-8")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Edit/ })).not.toBeInTheDocument();
  });

  it("shows loading, then an error with retry, when the company settings can't load", async () => {
    signIn(makeMe("SUPER_ADMIN"));
    vi.mocked(settingsService.company).mockRejectedValue(new ApiRequestError(500, "INTERNAL_ERROR", "Something went wrong", []));
    renderApp("/settings");
    expect(screen.getByText(/Loading|Restoring/)).toBeInTheDocument();
    expect((await screen.findAllByRole("button", { name: /Retry|Try again/ })).length).toBeGreaterThan(0);
  });

  it("keeps the section tabs and tables scrollable on a phone", async () => {
    act(() => setViewport(375));
    signIn(makeMe("SUPER_ADMIN"));
    renderApp("/settings?section=commission");
    expect(await screen.findByRole("tablist", { name: "Settings sections" })).toHaveClass("overflow-x-auto");
    expect(await screen.findByRole("region", { name: "Partner tiers, scrolls horizontally" })).toHaveClass("overflow-x-auto");
  });
});
