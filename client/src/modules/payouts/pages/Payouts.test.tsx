import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderApp } from "@/test/renderApp";
import { catalog, makeMe, row } from "@/test/fixtures";
import { authService } from "@/services/auth.service";
import { payoutService } from "../services/payoutService";
import { TdsSettingsCard } from "../components/TdsSettingsCard";
import type { PartnerAdjustment, PartnerPayoutBalance, Payout, PayoutDashboard, PayoutMeta, PayoutPreview, PayoutSummary, PayoutTds, TdsConfiguration } from "../types/payout.types";

vi.mock("@/services/auth.service", () => ({
  authService: { restore: vi.fn(), me: vi.fn(), modules: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() },
}));
vi.mock("../services/payoutService", () => ({
  payoutService: {
    list: vi.fn(), dashboard: vi.fn(), meta: vi.fn(), get: vi.fn(), history: vi.fn(), balances: vi.fn(), balance: vi.fn(), preview: vi.fn(), create: vi.fn(),
    action: vi.fn(), adjustTds: vi.fn(), markPaid: vi.fn(), exportCsv: vi.fn(), adjustments: vi.fn(), createAdjustment: vi.fn(), decideAdjustment: vi.fn(),
    tdsConfiguration: vi.fn(), saveTdsDraft: vi.fn(), confirmTds: vi.fn(),
  },
}));

const meta = (over: Partial<PayoutMeta> = {}): PayoutMeta => ({
  statuses: [
    { value: "DRAFT", label: "Draft" },
    { value: "PENDING_APPROVAL", label: "Pending approval" },
    { value: "PAID", label: "Paid" },
  ],
  adjustmentStatuses: [
    { value: "PENDING_APPROVAL", label: "Pending approval" },
    { value: "APPROVED", label: "Approved" },
  ],
  maxReason: 500,
  maxNotes: 500,
  maxReference: 100,
  tdsConfigured: true,
  tdsMessage: null,
  canWrite: false,
  canApprove: false,
  canMarkPaid: false,
  canExport: false,
  canConfigureTds: false,
  canViewSensitive: false,
  ...over,
});
const dashboard: PayoutDashboard = {
  fyLabel: "FY 2026-27",
  byStatus: [
    { status: "PENDING_APPROVAL", label: "Pending approval", count: 1, commission: "800.00", tds: "80.00", net: "720.00" },
    { status: "PAID", label: "Paid", count: 2, commission: "5000.00", tds: "500.00", net: "4500.00" },
  ],
  eligibleCommission: "1200.00",
  eligibleReceipts: 3,
  carriedAdjustments: "-150.00",
  pendingAdjustments: 1,
  paidFy: "4500.00",
  tdsFy: "500.00",
  partnersWithBalance: 2,
  tdsConfigured: true,
};
const summary: PayoutSummary = {
  id: "po1", payoutNumber: "PO-2026-000001", partner: { id: "p1", name: "Alpha Interiors" }, status: "PENDING_APPROVAL", statusLabel: "Pending approval",
  legacy: false, asOfDate: "2026-10-25", fyLabel: "FY 2026-27", commissionTotal: "800.00", tdsStatus: "CALCULATED", tdsAmount: "80.00", netAmount: "720.00",
  paidDate: null, blocked: false, createdAt: "2026-10-25T05:00:00Z",
};
const tds: PayoutTds = {
  status: "CALCULATED", message: null, configurationId: "t1", sectionCode: "TEST-SEC", ratePercent: "10.000", fyThreshold: "500.00", panVerified: true,
  priorFyCommission: "0.00", priorFyTdsBase: "0.00", tdsBase: "800.00", computedTds: "80.00", tdsAmount: "80.00", adjusted: false,
};
const noActions = { recalculate: false, adjustTds: false, submit: false, approve: false, reject: false, cancel: false, startProcessing: false, markPaid: false, fail: false };
const payout = (over: Partial<Payout> = {}): Payout => ({
  id: "po1", payoutNumber: "PO-2026-000001", status: "PENDING_APPROVAL", statusLabel: "Pending approval", legacy: false,
  partner: { id: "p1", name: "Alpha Interiors", partnerCode: "P-1", panOnFile: true, pan: "••234F", panVerified: true, bankName: "••", bankAccount: "••••4321", ifsc: null, bankOnFile: true, sensitiveVisible: false },
  asOfDate: "2026-10-25", fyStart: "2026-04-01", fyLabel: "FY 2026-27", receiptsTotal: "800.00", adjustmentsTotal: "0.00", commissionTotal: "800.00", tds, netAmount: "720.00",
  notes: null,
  lines: [
    {
      id: "l1", lineType: "COMMISSION", paymentId: "pay1", paymentNumber: "PAY-2026-000001", invoiceId: "i1", invoiceNumber: "INV-2026-000001", paymentDate: "2026-10-20",
      baseAmount: "10000.00", commissionPercent: "8.000", amount: "800.00", adjustmentId: null, adjustmentNumber: null, adjustmentKind: null, adjustmentReason: null,
      sourceReversed: false, releasedAt: null, releaseReason: null,
    },
  ],
  releasedLines: [],
  calculations: [],
  workflow: {
    createdAt: "2026-10-25T05:00:00Z", createdByName: "Maker", submittedAt: "2026-10-25T06:00:00Z", submittedByName: "Maker", approvedAt: null, approvedByName: null, approvalNote: null,
    processingAt: null, processingByName: null, bankAccountLast4: null, paidDate: null, paymentReference: null, paidAt: null, paidByName: null, closedAt: null, closedByName: null, statusReason: null,
  },
  reversedSources: [],
  blockers: [],
  actions: noActions,
  version: 3,
  ...over,
});
const balance: PartnerPayoutBalance = {
  partnerId: "p2", partnerName: "Beta Builders", partnerCode: "P-2", panOnFile: true, panVerified: false, bankOnFile: true, eligibleReceipts: 2, eligibleCommission: "400.00",
  carriedAdjustments: "0.00", pendingAdjustments: 0, pendingAdjustmentAmount: "0.00", inProgressPayouts: 0, inProgress: "0.00", paidFy: "0.00", tdsFy: "0.00", paidTotal: "0.00", lastPaidDate: null,
};
const preview: PayoutPreview = {
  partner: { id: "p2", name: "Beta Builders" }, asOfDate: "2026-10-26", fyLabel: "FY 2026-27", lines: payout().lines, receiptsTotal: "400.00", adjustmentsTotal: "0.00",
  commissionTotal: "400.00", tds: { ...tds, status: "PAN_NOT_VERIFIED", message: "The partner's PAN isn't verified.", tdsAmount: null, computedTds: null, tdsBase: null }, netAmount: null,
  canCreate: true, blockers: ["The partner's PAN isn't verified. Approval is blocked until it is."], openPayout: null,
};
const adjustment: PartnerAdjustment = {
  id: "a1", adjustmentNumber: "ADJ-2026-000001", partner: { id: "p1", name: "Alpha Interiors" }, kind: "MANUAL", kindLabel: "Manual", amount: "-150.00",
  reason: "Duplicate commission in September", status: "PENDING_APPROVAL", statusLabel: "Pending approval", sourcePaymentId: null, sourcePaymentNumber: null,
  createdAt: "2026-10-25T05:00:00Z", createdByName: "Maker", decidedAt: null, decidedByName: null, decisionNote: null, allocatedTo: null, canDecide: true,
};

const signIn = (me: ReturnType<typeof makeMe>) => {
  vi.mocked(authService.restore).mockResolvedValue(me);
  vi.mocked(authService.me).mockResolvedValue(me);
};
const reader = () => makeMe("ADMIN", { modules: [row("DASHBOARD"), row("PAYOUTS", { dataScope: "ALL" })] });
const writer = () => makeMe("ADMIN", { modules: [row("DASHBOARD"), row("PAYOUTS", { write: true, export: true, dataScope: "ALL" })] });
const approver = () => makeMe("ADMIN", { modules: [row("DASHBOARD"), row("PAYOUTS", { approve: true, dataScope: "ALL" })] });
const page = <T,>(content: T[]) => ({ content, meta: { page: 0, size: 20, totalElements: content.length, totalPages: 1 } });

beforeEach(() => {
  vi.mocked(authService.modules).mockResolvedValue(catalog);
  vi.mocked(payoutService.meta).mockResolvedValue(meta());
  vi.mocked(payoutService.dashboard).mockResolvedValue(dashboard);
  vi.mocked(payoutService.list).mockResolvedValue(page([summary]));
  vi.mocked(payoutService.balances).mockResolvedValue([balance]);
  vi.mocked(payoutService.adjustments).mockResolvedValue(page([adjustment]));
  vi.mocked(payoutService.get).mockResolvedValue(payout());
  vi.mocked(payoutService.history).mockResolvedValue([
    { id: "h1", at: "2026-10-25T05:00:00Z", actorName: "Maker", action: "PAYOUT_CREATED", changes: [{ field: "commissionTotal", from: null, to: "800.00" }, { field: "receipts", from: null, to: 2 }] },
  ]);
});

describe("payouts list", () => {
  it("shows the dashboard and payouts without create or export for a reader", async () => {
    signIn(reader());
    renderApp("/payouts");
    const table = await screen.findByRole("table", { name: "Payouts" });
    expect(await within(table).findByRole("link", { name: "PO-2026-000001" })).toBeInTheDocument();
    expect(within(table).getByText("Pending approval")).toBeInTheDocument();
    expect(await screen.findByText("Ready to pay")).toBeInTheDocument();
    expect(screen.getByText("₹1,200.00")).toBeInTheDocument();
    expect(screen.getByText("−₹150.00")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Create payout/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Export CSV/ })).not.toBeInTheDocument();
  });

  it("warns that approval is blocked while the TDS settings aren't confirmed", async () => {
    signIn(reader());
    vi.mocked(payoutService.meta).mockResolvedValue(meta({ tdsConfigured: false, tdsMessage: "TDS settings are not confirmed. Payouts can't be approved." }));
    renderApp("/payouts");
    expect(await screen.findByText("TDS settings not confirmed")).toBeInTheDocument();
    expect(screen.getByText(/Payouts can't be approved/)).toBeInTheDocument();
  });

  it("filters by status and exports the filtered list", async () => {
    signIn(writer());
    vi.mocked(payoutService.meta).mockResolvedValue(meta({ canWrite: true, canExport: true }));
    vi.mocked(payoutService.exportCsv).mockResolvedValue({ text: "payout_number\n", filename: "iotiq-payouts.csv" });
    renderApp("/payouts");
    await screen.findByRole("table", { name: "Payouts" });
    await screen.findByRole("option", { name: "Paid" });
    await userEvent.selectOptions(screen.getByLabelText("Filter by status"), "PAID");
    await vi.waitFor(() => expect(payoutService.list).toHaveBeenLastCalledWith(expect.objectContaining({ status: "PAID", page: 0 }), expect.anything()));
    await userEvent.click(screen.getByRole("button", { name: /Export CSV/ }));
    await vi.waitFor(() => expect(payoutService.exportCsv).toHaveBeenCalledWith(expect.objectContaining({ status: "PAID" })));
  });
});

describe("creating a payout", () => {
  it("previews the payout with its blockers and creates a draft with an idempotency key", async () => {
    signIn(writer());
    vi.mocked(payoutService.meta).mockResolvedValue(meta({ canWrite: true }));
    vi.mocked(payoutService.preview).mockResolvedValue(preview);
    vi.mocked(payoutService.create).mockResolvedValue(payout({ id: "po2", payoutNumber: "PO-2026-000002", status: "DRAFT", statusLabel: "Draft" }));
    renderApp("/payouts");
    await userEvent.click(await screen.findByRole("button", { name: /Create payout/ }));
    const dialog = await screen.findByRole("dialog", { name: "Create payout" });
    await userEvent.click(within(dialog).getByRole("combobox", { name: /Partner/ }));
    await userEvent.click(await screen.findByRole("option", { name: /Beta Builders/ }));
    expect(await within(dialog).findByText(/Approval is blocked until it is/)).toBeInTheDocument();
    expect(within(dialog).getByText("Not calculated")).toBeInTheDocument();
    expect(payoutService.preview).toHaveBeenCalledWith("p2", expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/));
    await userEvent.click(within(dialog).getByRole("button", { name: "Create draft" }));
    await vi.waitFor(() =>
      expect(payoutService.create).toHaveBeenCalledWith(expect.objectContaining({ partnerId: "p2", idempotencyKey: expect.stringMatching(/^[0-9a-f-]{36}$/) })),
    );
  });
});

describe("receipt counts and repeated messages", () => {
  it('says "1 receipt" and shows a TDS message once when it is also a blocker', async () => {
    signIn(writer());
    vi.mocked(payoutService.meta).mockResolvedValue(meta({ canWrite: true }));
    vi.mocked(payoutService.balances).mockResolvedValue([{ ...balance, eligibleReceipts: 1 }]);
    const message = "TDS settings aren't confirmed for this date.";
    vi.mocked(payoutService.preview).mockResolvedValue({ ...preview, tds: { ...preview.tds, status: "CONFIG_INCOMPLETE", message }, blockers: [message] });
    renderApp("/payouts");
    await userEvent.click(await screen.findByRole("button", { name: /Create payout/ }));
    const dialog = await screen.findByRole("dialog", { name: "Create payout" });
    await userEvent.click(within(dialog).getByRole("combobox", { name: /Partner/ }));
    expect(await screen.findByRole("option", { name: /1 receipt$/ })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("option", { name: /Beta Builders/ }));
    expect(await within(dialog).findAllByText(message)).toHaveLength(1);
  });
});

describe("payout details", () => {
  it("shows the commission lines, TDS breakdown and masked bank details, with no actions the server didn't offer", async () => {
    signIn(reader());
    renderApp("/payouts/po1");
    expect(await screen.findByRole("heading", { name: "PO-2026-000001" })).toBeInTheDocument();
    expect(screen.getByText("PAY-2026-000001")).toBeInTheDocument();
    expect(screen.getByText(/TEST-SEC ·/)).toBeInTheDocument();
    expect(screen.getByText(/••••4321/)).toBeInTheDocument();
    expect(screen.getByText(/masked for your role/)).toBeInTheDocument();
    expect(await screen.findByText("Draft created")).toBeInTheDocument();
    // Line counts in the history are counts, not money.
    expect(screen.getByText(/Receipt lines: 2/)).toBeInTheDocument();
    expect(screen.queryByText(/Receipt lines: ₹/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Mark as paid" })).not.toBeInTheDocument();
  });

  it("approves with the version the user saw and requires a reason to reject", async () => {
    signIn(approver());
    vi.mocked(payoutService.get).mockResolvedValue(payout({ actions: { ...noActions, approve: true, reject: true } }));
    vi.mocked(payoutService.action).mockResolvedValue(payout({ status: "APPROVED", statusLabel: "Approved" }));
    renderApp("/payouts/po1");
    await userEvent.click(await screen.findByRole("button", { name: "Reject" }));
    let dialog = await screen.findByRole("dialog", { name: "Reject PO-2026-000001?" });
    await userEvent.click(within(dialog).getByRole("button", { name: "Reject payout" }));
    expect(await within(dialog).findByText("Give the reason.")).toBeInTheDocument();
    expect(payoutService.action).not.toHaveBeenCalled();
    await userEvent.click(within(dialog).getByRole("button", { name: "Back" }));

    await userEvent.click(screen.getByRole("button", { name: "Approve" }));
    dialog = await screen.findByRole("dialog", { name: "Approve PO-2026-000001?" });
    await userEvent.type(within(dialog).getByLabelText(/Approval note/), "Checked against statements");
    await userEvent.click(within(dialog).getByRole("button", { name: "Approve payout" }));
    await vi.waitFor(() => expect(payoutService.action).toHaveBeenCalledWith("po1", "approve", 3, { note: "Checked against statements" }));
  });

  it("shows the blockers the server reports", async () => {
    signIn(approver());
    vi.mocked(payoutService.get).mockResolvedValue(payout({ blockers: ["Payment PAY-2026-000001 was reversed. Recalculate the payout to remove it."] }));
    renderApp("/payouts/po1");
    expect(await screen.findByText(/was reversed. Recalculate the payout to remove it/)).toBeInTheDocument();
  });

  it("marks a payout paid only with a reference, and asks to acknowledge receipts reversed during processing", async () => {
    signIn(makeMe("ADMIN", { modules: [row("DASHBOARD"), row("PAYOUTS", { dataScope: "ALL" })] }, ["PAYOUTS:MARK_PAYOUT_PAID"]));
    vi.mocked(payoutService.get).mockResolvedValue(
      payout({ status: "PROCESSING", statusLabel: "Processing", reversedSources: ["PAY-2026-000001"], actions: { ...noActions, markPaid: true, fail: true } }),
    );
    vi.mocked(payoutService.markPaid).mockResolvedValue(payout({ status: "PAID", statusLabel: "Paid" }));
    renderApp("/payouts/po1");
    await userEvent.click(await screen.findByRole("button", { name: "Mark as paid" }));
    const dialog = await screen.findByRole("dialog", { name: "Mark PO-2026-000001 as paid?" });
    expect(within(dialog).getByText(/Receipts reversed during processing: PAY-2026-000001/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Mark as paid" }));
    expect(await within(dialog).findByText("Enter the bank's transaction reference.")).toBeInTheDocument();
    expect(within(dialog).getByText("Confirm you've seen the reversed receipts.")).toBeInTheDocument();
    expect(payoutService.markPaid).not.toHaveBeenCalled();

    await userEvent.type(within(dialog).getByLabelText(/Bank reference/), "UTR5550001");
    await userEvent.click(within(dialog).getByRole("checkbox"));
    await userEvent.click(within(dialog).getByRole("button", { name: "Mark as paid" }));
    await vi.waitFor(() =>
      expect(payoutService.markPaid).toHaveBeenCalledWith("po1", 3, expect.objectContaining({ reference: "UTR5550001", acknowledgeReversedSources: true })),
    );
  });
});

describe("adjustments", () => {
  it("lets an approver decide a pending adjustment and requires a reason to reject", async () => {
    signIn(approver());
    vi.mocked(payoutService.meta).mockResolvedValue(meta({ canApprove: true }));
    vi.mocked(payoutService.decideAdjustment).mockResolvedValue({ ...adjustment, status: "REJECTED", statusLabel: "Rejected", canDecide: false });
    renderApp("/payouts?tab=adjustments");
    const table = await screen.findByRole("table", { name: "Adjustments" });
    expect(await within(table).findByText("ADJ-2026-000001")).toBeInTheDocument();
    expect(within(table).getByText("−₹150.00")).toBeInTheDocument();
    await userEvent.click(within(table).getByRole("button", { name: "Reject" }));
    const dialog = await screen.findByRole("dialog", { name: "Reject ADJ-2026-000001?" });
    await userEvent.click(within(dialog).getByRole("button", { name: "Reject adjustment" }));
    expect(await within(dialog).findByText("Give the reason for rejecting this adjustment.")).toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText(/Reason for rejecting/), "Not a duplicate");
    await userEvent.click(within(dialog).getByRole("button", { name: "Reject adjustment" }));
    await vi.waitFor(() => expect(payoutService.decideAdjustment).toHaveBeenCalledWith("a1", "reject", "Not a duplicate"));
  });

  it("hides decisions on adjustments the user may not decide", async () => {
    signIn(approver());
    vi.mocked(payoutService.adjustments).mockResolvedValue(page([{ ...adjustment, canDecide: false }]));
    renderApp("/payouts?tab=adjustments");
    const table = await screen.findByRole("table", { name: "Adjustments" });
    expect(await within(table).findByText("ADJ-2026-000001")).toBeInTheDocument();
    expect(within(table).queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
  });
});

describe("TDS settings", () => {
  const renderCard = () =>
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <TdsSettingsCard />
      </QueryClientProvider>,
    );
  const draft = {
    id: "t2", status: "DRAFT" as const, sectionCode: "TEST-SEC", description: null, ratePercent: "10.000", rateWithoutPanPercent: null, fyThreshold: "500.00",
    effectiveFrom: "2026-04-01", editedByName: "Finance Maker", updatedAt: "2026-10-25T05:00:00Z", confirmedAt: null, confirmedByName: null, confirmationNote: null, complete: true, version: 1,
  };

  it("says nothing is confirmed and pre-fills no rate", async () => {
    vi.mocked(payoutService.tdsConfiguration).mockResolvedValue({ configured: false, inForce: null, draft: null, history: [], canEdit: true, canConfirm: false });
    renderCard();
    expect(await screen.findByText("Not confirmed")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "New version" }));
    expect(screen.getByLabelText(/Rate with a verified PAN/)).toHaveValue("");
    expect(screen.getByLabelText(/Financial-year threshold/)).toHaveValue("");
  });

  it("confirms a complete draft only with a note", async () => {
    const config: TdsConfiguration = { configured: false, inForce: null, draft, history: [], canEdit: false, canConfirm: true };
    vi.mocked(payoutService.tdsConfiguration).mockResolvedValue(config);
    vi.mocked(payoutService.confirmTds).mockResolvedValue({ ...config, configured: true });
    renderCard();
    await userEvent.click(await screen.findByRole("button", { name: "Confirm TDS settings" }));
    expect(await screen.findByText(/Record Finance's confirmation/)).toBeInTheDocument();
    expect(payoutService.confirmTds).not.toHaveBeenCalled();
    await userEvent.type(screen.getByLabelText(/Confirmation note/), "Confirmed by the finance controller");
    await userEvent.click(screen.getByRole("button", { name: "Confirm TDS settings" }));
    await vi.waitFor(() => expect(payoutService.confirmTds).toHaveBeenCalledWith(1, "Confirmed by the finance controller"));
  });
});
