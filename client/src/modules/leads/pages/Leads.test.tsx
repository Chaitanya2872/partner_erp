import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderApp } from "@/test/renderApp";
import { catalog, makeMe, row } from "@/test/fixtures";
import { authService } from "@/services/auth.service";
import { leadService } from "../services/leadService";
import type { Lead, LeadHistoryEntry, LeadMeta, LeadSummary } from "../types/lead.types";

vi.mock("@/services/auth.service", () => ({
  authService: { restore: vi.fn(), me: vi.fn(), modules: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() },
}));
vi.mock("../services/leadService", () => ({
  leadService: {
    list: vi.fn(),
    meta: vi.fn(),
    get: vi.fn(),
    history: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    setStage: vi.fn(),
    acknowledge: vi.fn(),
    remove: vi.fn(),
  },
}));

const meta: LeadMeta = {
  stages: [
    { code: "NEW", label: "New" },
    { code: "REGISTERED", label: "Registered" },
    { code: "PRESALES", label: "Presales" },
    { code: "SITE_VISIT", label: "Site visit" },
    { code: "ESTIMATE_SENT", label: "Estimate sent" },
    { code: "NEGOTIATION", label: "Negotiation" },
    { code: "WON", label: "Won" },
    { code: "LOST", label: "Lost" },
  ],
  projectTypes: [
    { code: "VILLA", label: "Villa" },
    { code: "APARTMENT", label: "Apartment" },
  ],
  partners: [
    { id: "p1", name: "Studio Nila", statusLabel: "Active", active: true, tierName: "Registered", feePercent: "8.000" },
    { id: "p2", name: "Beta Builders", statusLabel: "Inactive", active: false, tierName: "Elite", feePercent: "12.000" },
  ],
  ackTargetHours: 48,
  estimateTargetDays: 5,
  protectionDays: 90,
};

const sla = { ackHours: 3, ackBreach: false, estimateDays: null, estimateBreach: false, hasEstimate: false, ackTargetHours: 48, estimateTargetDays: 5, protectionDaysLeft: null };

const fresh: LeadSummary = {
  id: "l1",
  leadNumber: "LEAD-2026-000001",
  clientName: "Rao Residence",
  clientPhone: "9876543210",
  clientEmail: null,
  city: "Hyderabad",
  projectType: "VILLA",
  projectTypeLabel: "Villa",
  units: 3,
  budget: "450000.00",
  stage: "NEW",
  stageLabel: "New",
  partner: { id: "p1", name: "Studio Nila" },
  acknowledgedAt: null,
  lockedFeePercent: null,
  protectionUntil: null,
  protectionActive: false,
  conflict: true,
  sla: { ...sla, ackHours: 50, ackBreach: true },
  createdAt: "2026-10-20T00:00:00Z",
  version: 2,
};

const won: LeadSummary = {
  ...fresh,
  id: "l2",
  clientName: "Iyer Villa",
  stage: "WON",
  stageLabel: "Won",
  conflict: false,
  acknowledgedAt: "2026-10-01T05:00:00Z",
  lockedFeePercent: "10.000",
  protectionUntil: "2026-12-30",
  protectionActive: true,
  sla: { ...sla, estimateDays: 7, estimateBreach: true, hasEstimate: true },
  version: 6,
};

const lead: Lead = {
  ...fresh,
  presalesOwner: "Kiran",
  expectedClose: "2026-12-15",
  source: "Partner referral",
  notes: null,
  acknowledgedByName: null,
  conflictPartnerName: "Beta Builders",
  updatedAt: "2026-10-21T00:00:00Z",
};

const history: LeadHistoryEntry[] = [
  { id: "h1", event: "CREATED", eventLabel: "Created", fromStage: null, fromStageLabel: null, toStage: "NEW", toStageLabel: "New", reason: null, actorId: "u", actorName: "Test Admin", occurredAt: "2026-10-20T00:00:00Z" },
  { id: "h2", event: "STAGE_CHANGED", eventLabel: "Stage changed", fromStage: "WON", fromStageLabel: "Won", toStage: "NEGOTIATION", toStageLabel: "Negotiation", reason: "Budget revised", actorId: "u", actorName: "Test Admin", occurredAt: "2026-10-21T00:00:00Z" },
];

const editor = () => makeMe("ADMIN"); // LEADS read + write, no approve or delete
const approver = () => makeMe("ADMIN", { modules: [row("DASHBOARD"), row("PARTNERS", { dataScope: "ALL" }), row("LEADS", { write: true, approve: true, delete: true, dataScope: "ALL" })] });

beforeEach(() => {
  vi.mocked(authService.modules).mockResolvedValue(catalog);
  vi.mocked(leadService.meta).mockResolvedValue(meta);
  vi.mocked(leadService.list).mockResolvedValue({ content: [fresh, won], meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 } });
  vi.mocked(leadService.get).mockResolvedValue(lead);
  vi.mocked(leadService.history).mockResolvedValue(history);
});

const signIn = (me: ReturnType<typeof makeMe>) => {
  vi.mocked(authService.restore).mockResolvedValue(me);
  vi.mocked(authService.me).mockResolvedValue(me);
};

const rowOf = async (name: string) => (await screen.findByRole("link", { name })).closest("tr")!;

describe("lead list", () => {
  it("shows the source columns: duplicate pill, registration and SLA pills", async () => {
    signIn(editor());
    renderApp("/leads");
    const freshRow = await rowOf("Rao Residence");
    expect(within(freshRow).getByText("LEAD-2026-000001 · Villa · 3 units · Hyderabad")).toBeInTheDocument();
    expect(within(freshRow).getByText("Possible duplicate")).toBeInTheDocument();
    expect(within(freshRow).getByText("Ack late")).toBeInTheDocument();
    // Without Leads approve there is no acknowledge button, and without delete no delete button.
    expect(within(freshRow).getByText("Not acknowledged")).toBeInTheDocument();
    expect(within(freshRow).queryByRole("button", { name: /Acknowledge/ })).not.toBeInTheDocument();
    expect(within(freshRow).queryByRole("button", { name: /Delete/ })).not.toBeInTheDocument();
    expect(within(freshRow).getByRole("link", { name: "Edit Rao Residence" })).toHaveAttribute("href", "/leads/l1/edit");

    const wonRow = await rowOf("Iyer Villa");
    expect(within(wonRow).getByText("Acknowledged")).toBeInTheDocument();
    expect(within(wonRow).getByText("10.00% locked · to 30 Dec 2026")).toBeInTheDocument();
    expect(within(wonRow).getByText("Ack ok")).toBeInTheDocument();
    expect(within(wonRow).getByText("Estimate late")).toBeInTheDocument();
  });

  it("changes an open stage inline with the version", async () => {
    signIn(editor());
    vi.mocked(leadService.setStage).mockResolvedValue({ ...lead, stage: "PRESALES", stageLabel: "Presales" });
    renderApp("/leads");
    await userEvent.selectOptions(await screen.findByRole("combobox", { name: "Stage for Rao Residence" }), "PRESALES");
    await vi.waitFor(() => expect(leadService.setStage).toHaveBeenCalledWith("l1", "PRESALES", 2, undefined));
    expect(await screen.findByText("Stage changed to Presales.")).toBeInTheDocument();
  });

  it("locks Won and Lost leads unless the user may reopen them", async () => {
    signIn(editor());
    renderApp("/leads");
    expect(await screen.findByRole("combobox", { name: "Stage for Iyer Villa" })).toBeDisabled();
    expect(screen.getByRole("combobox", { name: "Stage for Rao Residence" })).toBeEnabled();
  });

  it("asks for a reason before reopening a closed lead", async () => {
    signIn(approver());
    vi.mocked(leadService.setStage).mockResolvedValue({ ...lead, stage: "NEGOTIATION", stageLabel: "Negotiation" });
    renderApp("/leads");
    await userEvent.selectOptions(await screen.findByRole("combobox", { name: "Stage for Iyer Villa" }), "NEGOTIATION");
    const dialog = await screen.findByRole("dialog", { name: "Reopen lead?" });
    expect(within(dialog).getByText(/moves from Won to Negotiation/)).toBeInTheDocument();
    expect(leadService.setStage).not.toHaveBeenCalled();
    await userEvent.type(within(dialog).getByLabelText("Reason (optional)"), "Budget revised");
    await userEvent.click(within(dialog).getByRole("button", { name: "Reopen" }));
    await vi.waitFor(() => expect(leadService.setStage).toHaveBeenCalledWith("l2", "NEGOTIATION", 6, "Budget revised"));
  });

  it("confirms acknowledgement with the fee rate and protection period", async () => {
    signIn(approver());
    vi.mocked(leadService.acknowledge).mockResolvedValue({ ...lead, acknowledgedAt: "2026-10-25T00:00:00Z", lockedFeePercent: "8.000", stage: "REGISTERED" });
    renderApp("/leads");
    await userEvent.click(await screen.findByRole("button", { name: "Acknowledge Rao Residence" }));
    const dialog = await screen.findByRole("dialog", { name: "Acknowledge registration?" });
    expect(dialog).toHaveTextContent("Acknowledge “Rao Residence” for Studio Nila? The 8.00% (Registered) fee rate locks now and 90-day protection starts.");
    await userEvent.click(within(dialog).getByRole("button", { name: "Acknowledge" }));
    await vi.waitFor(() => expect(leadService.acknowledge).toHaveBeenCalledWith("l1"));
    expect(await screen.findByText("Acknowledged. 8.00% fee rate locked.")).toBeInTheDocument();
  });

  it("offers no acknowledgement on Won or Lost leads until they are reopened", async () => {
    signIn(approver());
    const lost: LeadSummary = { ...fresh, id: "l3", clientName: "Lost Deal", stage: "LOST", stageLabel: "Lost", conflict: false, version: 1 };
    vi.mocked(leadService.list).mockResolvedValue({ content: [fresh, lost], meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 } });
    vi.mocked(leadService.get).mockResolvedValue({ ...lead, ...lost, conflictPartnerName: null });
    renderApp("/leads");
    const lostRow = await rowOf("Lost Deal");
    expect(within(lostRow).queryByRole("button", { name: /Acknowledge/ })).not.toBeInTheDocument();
    expect(within(lostRow).getByText("Not acknowledged")).toHaveAttribute("title", "Reopen the lead to acknowledge it.");
    // Open leads still offer it.
    expect(within(await rowOf("Rao Residence")).getByRole("button", { name: "Acknowledge Rao Residence" })).toBeInTheDocument();
    expect(leadService.acknowledge).not.toHaveBeenCalled();
  });

  it("hides acknowledgement on the detail page of a closed lead", async () => {
    signIn(approver());
    vi.mocked(leadService.get).mockResolvedValue({ ...lead, stage: "WON", stageLabel: "Won", conflictPartnerName: null });
    renderApp("/leads/l1");
    expect(await screen.findByRole("heading", { name: "Rao Residence" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Acknowledge/ })).not.toBeInTheDocument();
    expect(screen.getByText("Not yet. Reopen the lead to acknowledge it.")).toBeInTheDocument();
    // Approvers can still reopen it through the stage select.
    expect(screen.getByRole("combobox", { name: "Stage" })).toBeEnabled();
  });

  it("confirms before deleting and explains the document rule", async () => {
    signIn(approver());
    vi.mocked(leadService.remove).mockResolvedValue();
    renderApp("/leads");
    await userEvent.click(await screen.findByRole("button", { name: "Delete Rao Residence" }));
    const dialog = await screen.findByRole("dialog", { name: "Delete lead?" });
    expect(within(dialog).getByText(/mark it Lost instead/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Delete" }));
    await vi.waitFor(() => expect(leadService.remove).toHaveBeenCalledWith("l1"));
  });

  it("sends search and filters to the server and resets to the first page", async () => {
    signIn(editor());
    renderApp("/leads");
    await rowOf("Rao Residence");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Filter by stage" }), "PRESALES");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Filter by partner" }), "p1");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Filter by SLA" }), "true");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Filter by duplicates" }), "true");
    await userEvent.type(screen.getByRole("searchbox", { name: "Search leads" }), "rao");
    await vi.waitFor(() =>
      expect(leadService.list).toHaveBeenLastCalledWith(
        expect.objectContaining({ q: "rao", stage: "PRESALES", partnerId: "p1", slaBreach: "true", conflict: "true", page: 0 }),
        expect.anything(),
      ),
    );
  });

  it("is hidden from navigation without Leads read", async () => {
    signIn(makeMe("USER"));
    renderApp("/dashboard");
    expect(await screen.findByRole("navigation", { name: /main/i })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Leads" })).not.toBeInTheDocument();
  });
});

describe("lead details", () => {
  it("shows the conflict, SLA alert and stage history", async () => {
    signIn(editor());
    renderApp("/leads/l1");
    expect(await screen.findByRole("heading", { name: "Rao Residence" })).toBeInTheDocument();
    expect(screen.getByText(/protected for Beta Builders/)).toBeInTheDocument();
    expect(screen.getByText("Not acknowledged in time")).toBeInTheDocument();
    expect(screen.getByText("Waiting 50 h; the target is 48 h.")).toBeInTheDocument();
    expect(await screen.findByText("Reopened: Won → Negotiation")).toBeInTheDocument();
    expect(screen.getByText("Budget revised")).toBeInTheDocument();
    expect(screen.getByText("Created as New")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Acknowledge/ })).not.toBeInTheDocument();
  });
});

describe("lead form", () => {
  it("validates on the client before sending anything", async () => {
    signIn(editor());
    renderApp("/leads/new");
    await userEvent.type(await screen.findByLabelText("Phone"), "abc");
    await userEvent.click(screen.getByRole("button", { name: "Create lead" }));
    expect(await screen.findAllByText("Client name is required.")).not.toHaveLength(0);
    expect(screen.getAllByText("Choose the referring partner.")).not.toHaveLength(0);
    expect(screen.getAllByText("Enter a valid phone number.")).not.toHaveLength(0);
    expect(leadService.create).not.toHaveBeenCalled();
  });

  it("creates a lead and warns when the phone is protected for another partner", async () => {
    signIn(editor());
    vi.mocked(leadService.create).mockResolvedValue({ ...lead, conflict: true, conflictPartnerName: "Beta Builders" });
    renderApp("/leads/new?partnerId=p1");
    await userEvent.type(await screen.findByLabelText(/^Client name/), "Rao Residence");
    expect(screen.getByLabelText(/^Referring partner/)).toHaveValue("p1");
    expect(screen.getByText(/8.00% fee rate, locked when the lead is acknowledged/)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Budget"), "450000");
    await userEvent.click(screen.getByRole("button", { name: "Create lead" }));
    await vi.waitFor(() => expect(leadService.create).toHaveBeenCalled());
    expect(vi.mocked(leadService.create).mock.calls[0][0]).toMatchObject({
      clientName: "Rao Residence",
      partnerId: "p1",
      projectType: "VILLA",
      stage: "NEW",
      budget: "450000",
      units: null,
      expectedClose: null,
    });
    expect(await screen.findByText("Saved. Same phone is already protected for Beta Builders.")).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "Rao Residence" })).toBeInTheDocument();
    expect(screen.queryByText("Leave without saving?")).not.toBeInTheDocument();
  });

  it("lets the partner change before acknowledgement", async () => {
    signIn(editor());
    vi.mocked(leadService.update).mockResolvedValue({ ...lead, partner: { id: "p2", name: "Beta Builders" }, conflict: false });
    renderApp("/leads/l1/edit");
    const partnerSelect = await screen.findByRole("combobox", { name: /^Referring partner/ });
    await userEvent.selectOptions(partnerSelect, "p2");
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));
    await vi.waitFor(() => expect(leadService.update).toHaveBeenCalled());
    expect(vi.mocked(leadService.update).mock.calls[0]).toEqual(["l1", expect.objectContaining({ partnerId: "p2", version: 2 })]);
  });

  it("keeps the partner and a closed stage read-only when they can't change", async () => {
    signIn(editor());
    vi.mocked(leadService.get).mockResolvedValue({ ...lead, ...won, conflictPartnerName: null } as Lead);
    vi.mocked(leadService.update).mockResolvedValue({ ...lead, conflict: false });
    renderApp("/leads/l2/edit");
    expect(await screen.findByLabelText(/^Referring partner/)).toHaveValue("Studio Nila");
    expect(screen.getByLabelText(/^Referring partner/)).toHaveAttribute("readonly");
    expect(screen.getByLabelText("Stage")).toHaveValue("Won");
    expect(screen.getByText("Reopening a Won or Lost lead needs Leads approve.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));
    await vi.waitFor(() => expect(leadService.update).toHaveBeenCalled());
    expect(vi.mocked(leadService.update).mock.calls[0]).toEqual(["l2", expect.objectContaining({ partnerId: "p1", stage: "WON", version: 6 })]);
  });
});
