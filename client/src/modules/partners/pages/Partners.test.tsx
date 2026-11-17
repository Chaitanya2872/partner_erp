import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderApp } from "@/test/renderApp";
import { catalog, makeMe, row } from "@/test/fixtures";
import { authService } from "@/services/auth.service";
import { ApiRequestError } from "@/services/api";
import { partnerService } from "../services/partnerService";
import { leadService } from "@/modules/leads/services/leadService";
import type { Partner, PartnerMeta, PartnerPerformance, PartnerSummary, TierReviewItem } from "../types/partner.types";

vi.mock("@/services/auth.service", () => ({
  authService: { restore: vi.fn(), me: vi.fn(), modules: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() },
}));
vi.mock("../services/partnerService", () => ({
  partnerService: {
    list: vi.fn(),
    meta: vi.fn(),
    get: vi.fn(),
    performance: vi.fn(),
    tierHistory: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    setStatus: vi.fn(),
    previewTierReview: vi.fn(),
    applyTierReview: vi.fn(),
  },
}));

// The detail page lists the partner's leads.
vi.mock("@/modules/leads/services/leadService", () => ({ leadService: { list: vi.fn(), meta: vi.fn() } }));

const tier = (level: number) => ({ level, name: ["", "Registered", "Preferred", "Elite", "Strategic"][level], commissionPercent: [0, 8, 10, 12, 15][level] });

const meta: PartnerMeta = {
  categories: [
    { code: "INTERIOR_DESIGNER", label: "Interior designer" },
    { code: "ARCHITECT", label: "Architect" },
  ],
  statuses: [
    { code: "PROSPECT", label: "Prospect" },
    { code: "ONBOARDING", label: "Onboarding" },
    { code: "ACTIVE", label: "Active" },
    { code: "INACTIVE", label: "Inactive" },
  ],
  agreementStatuses: [
    { code: "NOT_SENT", label: "Not sent" },
    { code: "SENT", label: "Sent" },
    { code: "SIGNED", label: "Signed" },
  ],
  tiers: [1, 2, 3, 4].map((l) => ({ ...tier(l), minimumRevenue: [0, 0, 1000000, 3000000, 10000000][l], payoutBusinessDays: [0, 15, 12, 10, 7][l] })),
  defaultAgreementVersion: "v2.1",
};

const summary: PartnerSummary = {
  id: "p1",
  name: "Studio Nila",
  category: "INTERIOR_DESIGNER",
  categoryLabel: "Interior designer",
  city: "Hyderabad",
  contactName: "Nila Rao",
  phone: "9876543210",
  status: "ACTIVE",
  statusLabel: "Active",
  tier: tier(1),
  calculatedTier: tier(2),
  revenue12m: "1100000.00",
  agreementStatus: "SENT",
  agreementStatusLabel: "Sent",
  leadCount: 0,
  strategicProgramme: false,
  downgradeGrace: false,
};

const partner: Partner = {
  ...summary,
  email: "nila@example.test",
  lastTierReviewOn: null,
  agreementVersion: "v2.1",
  agreementDate: "2026-10-01",
  pan: "••234F",
  panOnFile: true,
  panVerified: false,
  panVerifiedAt: null,
  gstin: "••1Z5",
  gstinOnFile: true,
  licenceNo: null,
  bankName: null,
  bankAccount: null,
  bankAccountOnFile: false,
  ifsc: null,
  notes: null,
  sensitiveVisible: false,
  createdAt: "2026-10-20T00:00:00Z",
  updatedAt: "2026-10-20T00:00:00Z",
  version: 4,
};

const performance: PartnerPerformance = {
  tier: tier(1),
  calculatedTier: tier(2),
  revenue12m: "1100000.00",
  revenueSince: "2025-10-25",
  nextTier: tier(2),
  nextTierMinimum: 1000000,
  progressPercent: 100,
  remainingToNextTier: 0,
  feesUnpaid: 0,
  feesPaid: 0,
  leadCount: 0,
  agreementStatus: "SENT",
  payoutReady: false,
};

const upgrade: TierReviewItem = {
  partnerId: "p1",
  partnerName: "Studio Nila",
  status: "ACTIVE",
  action: "UPGRADE",
  actionLabel: "Upgrade",
  fromLevel: 1,
  fromTierName: "Registered",
  toLevel: 2,
  toTierName: "Preferred",
  targetLevel: 2,
  revenue12m: "1100000.00",
  programme: false,
  graceBefore: false,
  graceAfter: false,
};

const editor = () => makeMe("ADMIN"); // PARTNERS read + write, no approve
const approver = () => makeMe("ADMIN", { modules: [row("DASHBOARD"), row("PARTNERS", { write: true, approve: true, dataScope: "ALL" })] });
const viewer = () => makeMe("USER", { modules: [row("DASHBOARD"), row("PARTNERS", { dataScope: "ALL" })] });

beforeEach(() => {
  vi.mocked(authService.modules).mockResolvedValue(catalog);
  vi.mocked(partnerService.meta).mockResolvedValue(meta);
  vi.mocked(partnerService.list).mockResolvedValue({ content: [summary], meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 } });
  vi.mocked(partnerService.get).mockResolvedValue(partner);
  vi.mocked(partnerService.performance).mockResolvedValue(performance);
  vi.mocked(partnerService.tierHistory).mockResolvedValue([]);
  vi.mocked(leadService.list).mockResolvedValue({ content: [], meta: { page: 0, size: 20, totalElements: 0, totalPages: 0 } });
  vi.mocked(leadService.meta).mockResolvedValue({ stages: [], projectTypes: [], partners: [], ackTargetHours: 48, estimateTargetDays: 5, protectionDays: 90 });
});

const signIn = (me: ReturnType<typeof makeMe>) => {
  vi.mocked(authService.restore).mockResolvedValue(me);
  vi.mocked(authService.me).mockResolvedValue(me);
};

describe("partner list", () => {
  it("shows partners with the assigned → calculated tier and write actions for editors", async () => {
    signIn(editor());
    renderApp("/partners");
    const link = await screen.findByRole("link", { name: "Studio Nila" });
    expect(link).toHaveAttribute("href", "/partners/p1");
    const rowEl = link.closest("tr")!;
    expect(within(rowEl).getByText("Registered")).toBeInTheDocument();
    expect(within(rowEl).getByText("Preferred")).toBeInTheDocument();
    expect(within(rowEl).getByText("₹11,00,000.00")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /New partner/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Edit Studio Nila" })).toBeInTheDocument();
    // Tier reviews need Partners approve.
    expect(screen.queryByRole("button", { name: /Run tier review/ })).not.toBeInTheDocument();
  });

  it("hides create and edit from read-only users", async () => {
    signIn(viewer());
    renderApp("/partners");
    expect(await screen.findByRole("link", { name: "Studio Nila" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /New partner/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Edit Studio Nila" })).not.toBeInTheDocument();
  });

  it("sends search and filters to the server and resets to the first page", async () => {
    signIn(editor());
    renderApp("/partners");
    await screen.findByRole("link", { name: "Studio Nila" });
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Filter by status" }), "ACTIVE");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Filter by tier" }), "3");
    await userEvent.type(screen.getByRole("searchbox", { name: "Search partners" }), "nila");
    await vi.waitFor(() =>
      expect(partnerService.list).toHaveBeenLastCalledWith(
        expect.objectContaining({ q: "nila", status: "ACTIVE", tier: "3", page: 0, sort: "name,asc" }),
        expect.anything(),
      ),
    );
  });

  it("is hidden from navigation without Partners read", async () => {
    signIn(makeMe("USER"));
    renderApp("/dashboard");
    expect(await screen.findByRole("navigation", { name: /main/i })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Partners" })).not.toBeInTheDocument();
  });
});

describe("partner details", () => {
  it("shows masked compliance data and the business-rule alerts", async () => {
    signIn(editor());
    vi.mocked(partnerService.get).mockResolvedValue({ ...partner, category: "ARCHITECT", categoryLabel: "Architect" });
    renderApp("/partners/p1");
    expect(await screen.findByRole("heading", { name: "Studio Nila" })).toBeInTheDocument();
    expect(screen.getByText("••234F")).toBeInTheDocument();
    expect(screen.getByText("••1Z5")).toBeInTheDocument();
    expect(screen.getByText(/masked for your account/)).toBeInTheDocument();
    expect(screen.getByText("Agreement not signed")).toBeInTheDocument();
    expect(screen.getByText(/Add the bank account before a payout can be approved/)).toBeInTheDocument();
    expect(screen.getByText(/CoA conduct rules/)).toBeInTheDocument();
    expect(await screen.findByRole("progressbar", { name: "Progress to Preferred" })).toHaveAttribute("aria-valuenow", "100");
    // The partner's leads replace the Phase 6 placeholder.
    expect(await screen.findByText("No leads from this partner yet.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /New lead/ })).toHaveAttribute("href", "/leads/new?partnerId=p1");
    expect(leadService.list).toHaveBeenCalledWith(expect.objectContaining({ partnerId: "p1" }), expect.anything());
  });

  it("confirms before deactivating and sends the version", async () => {
    signIn(editor());
    vi.mocked(partnerService.setStatus).mockResolvedValue({ ...partner, status: "INACTIVE", statusLabel: "Inactive", version: 5 });
    renderApp("/partners/p1");
    await userEvent.click(await screen.findByRole("button", { name: /Change status/ }));
    await userEvent.click(await screen.findByRole("menuitem", { name: "Inactive" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/never deleted/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Deactivate partner" }));
    expect(partnerService.setStatus).toHaveBeenCalledWith("p1", "INACTIVE", 4);
  });

  it("offers no edit, status or review actions to read-only users", async () => {
    signIn(viewer());
    renderApp("/partners/p1");
    expect(await screen.findByRole("heading", { name: "Studio Nila" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Edit/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Change status/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Tier review/ })).not.toBeInTheDocument();
  });
});

describe("partner form", () => {
  it("validates on the client before sending anything", async () => {
    signIn(editor());
    renderApp("/partners/new");
    await userEvent.type(await screen.findByLabelText("PAN"), "ABC");
    await userEvent.type(screen.getByLabelText("Email"), "not-an-email");
    await userEvent.click(screen.getByRole("button", { name: "Create partner" }));
    // Each message shows next to its field and in the error summary.
    expect(await screen.findAllByText("Name is required.")).not.toHaveLength(0);
    expect(screen.getAllByText("Select a category.")).not.toHaveLength(0);
    expect(screen.getAllByText(/Enter a valid PAN/)).not.toHaveLength(0);
    expect(screen.getAllByText("Enter a valid email address.")).not.toHaveLength(0);
    expect(screen.getByLabelText("PAN")).toHaveAttribute("aria-invalid", "true");
    expect(partnerService.create).not.toHaveBeenCalled();
  });

  it("creates a partner with defaults and without tier fields when the user can't approve", async () => {
    signIn(editor());
    vi.mocked(partnerService.create).mockResolvedValue(partner);
    renderApp("/partners/new");
    await userEvent.type(await screen.findByLabelText(/^Name/), "Studio Nila");
    await userEvent.selectOptions(screen.getByLabelText(/^Category/), "INTERIOR_DESIGNER");
    expect(screen.getByLabelText("Tier")).toBeDisabled();
    expect(screen.getByRole("checkbox", { name: /programme/i })).toBeDisabled();
    await userEvent.type(screen.getByLabelText("Bank account"), "1234 5678 9012");
    await userEvent.click(screen.getByRole("button", { name: "Create partner" }));
    await vi.waitFor(() => expect(partnerService.create).toHaveBeenCalled());
    expect(vi.mocked(partnerService.create).mock.calls[0][0]).toMatchObject({
      name: "Studio Nila",
      category: "INTERIOR_DESIGNER",
      status: "PROSPECT",
      agreementStatus: "NOT_SENT",
      agreementVersion: "v2.1",
      tierLevel: null,
      strategicProgramme: null,
      pan: null,
      bankAccount: "123456789012",
    });
    // A successful save goes straight to the detail page; the unsaved-changes guard must not interrupt it.
    expect(await screen.findByRole("heading", { name: "Studio Nila" })).toBeInTheDocument();
    expect(screen.queryByText("Leave without saving?")).not.toBeInTheDocument();
  });

  it("keeps masked values unless replaced, and removes them only when asked", async () => {
    signIn(editor());
    vi.mocked(partnerService.update).mockResolvedValue(partner);
    renderApp("/partners/p1/edit");
    const pan = await screen.findByLabelText("PAN");
    expect(pan).toHaveValue("");
    expect(screen.getByText("On file: ••234F")).toBeInTheDocument();
    expect(screen.getByLabelText("GSTIN")).toHaveValue("");
    expect(screen.getByText("On file: ••1Z5")).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText("City"));
    await userEvent.type(screen.getByLabelText("City"), "Pune");
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));
    await vi.waitFor(() => expect(partnerService.update).toHaveBeenCalledTimes(1));
    expect(vi.mocked(partnerService.update).mock.calls[0]).toEqual(["p1", expect.objectContaining({ city: "Pune", pan: null, gstin: null, bankAccount: null, version: 4 })]);
  });

  it("sends an empty string to remove a stored PAN or GSTIN", async () => {
    signIn(editor());
    vi.mocked(partnerService.update).mockResolvedValue(partner);
    renderApp("/partners/p1/edit");
    await userEvent.click(await screen.findByRole("checkbox", { name: "Remove the stored PAN" }));
    expect(screen.getByLabelText("PAN")).toBeDisabled();
    await userEvent.click(screen.getByRole("checkbox", { name: "Remove the stored GSTIN" }));
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));
    await vi.waitFor(() => expect(partnerService.update).toHaveBeenCalledTimes(1));
    expect(vi.mocked(partnerService.update).mock.calls[0][1]).toMatchObject({ pan: "", gstin: "", bankAccount: null });
  });

  it("shows server field errors next to the field", async () => {
    signIn(editor());
    vi.mocked(partnerService.create).mockRejectedValue(
      new ApiRequestError(422, "VALIDATION_FAILED", "Validation failed", [{ field: "gstin", message: "GSTIN is already used by another partner." }]),
    );
    renderApp("/partners/new");
    await userEvent.type(await screen.findByLabelText(/^Name/), "Studio Nila");
    await userEvent.selectOptions(screen.getByLabelText(/^Category/), "INTERIOR_DESIGNER");
    await userEvent.click(screen.getByRole("button", { name: "Create partner" }));
    expect(await screen.findByText("GSTIN is already used by another partner.")).toBeInTheDocument();
  });

  it("is not available to read-only users", async () => {
    signIn(viewer());
    renderApp("/partners/new");
    expect(await screen.findByText(/don't have (permission|access)/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Create partner" })).not.toBeInTheDocument();
  });
});

describe("tier review", () => {
  it("previews, applies the selected changes and refreshes", async () => {
    signIn(approver());
    vi.mocked(partnerService.previewTierReview).mockResolvedValue({ reviewDate: "2026-10-25", reviewed: 3, changes: [upgrade] });
    vi.mocked(partnerService.applyTierReview).mockResolvedValue({ reviewDate: "2026-10-25", applied: [upgrade] });
    renderApp("/partners");
    await userEvent.click(await screen.findByRole("button", { name: /Run tier review/ }));
    const dialog = await screen.findByRole("dialog");
    expect(await within(dialog).findByText(/3 partners reviewed/)).toBeInTheDocument();
    expect(within(dialog).getByRole("checkbox", { name: "Studio Nila" })).toBeChecked();
    await userEvent.click(within(dialog).getByRole("button", { name: "Apply 1 change" }));
    expect(partnerService.previewTierReview).toHaveBeenCalledWith(undefined);
    expect(partnerService.applyTierReview).toHaveBeenCalledWith([{ partnerId: "p1", action: "UPGRADE", toLevel: 2 }]);
  });

  it("asks for a new preview when the figures changed", async () => {
    signIn(approver());
    vi.mocked(partnerService.previewTierReview).mockResolvedValue({ reviewDate: "2026-10-25", reviewed: 1, changes: [upgrade] });
    vi.mocked(partnerService.applyTierReview).mockRejectedValue(new ApiRequestError(409, "TIER_REVIEW_STALE", "The figures changed."));
    renderApp("/partners/p1");
    await userEvent.click(await screen.findByRole("button", { name: /Tier review/ }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(await within(dialog).findByRole("button", { name: "Apply 1 change" }));
    expect(await within(dialog).findByText("The figures changed since this preview")).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Apply 1 change" })).toBeDisabled();
    expect(partnerService.previewTierReview).toHaveBeenCalledWith(["p1"]);
    await userEvent.click(within(dialog).getByRole("button", { name: /Preview again/ }));
    await vi.waitFor(() => expect(partnerService.previewTierReview).toHaveBeenCalledTimes(2));
  });
});
