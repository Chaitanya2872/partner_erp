import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderApp } from "@/test/renderApp";
import { catalog, makeMe, row } from "@/test/fixtures";
import { authService } from "@/services/auth.service";
import { ApiRequestError } from "@/services/api";
import { dashboardService } from "../services/dashboardService";
import type { Alerts, Finance, Overview, PeriodView, Sales } from "../types/dashboard.types";

vi.mock("@/services/auth.service", () => ({
  authService: { restore: vi.fn(), me: vi.fn(), modules: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() },
}));
vi.mock("../services/dashboardService", () => ({
  dashboardService: { overview: vi.fn(), sales: vi.fn(), partners: vi.fn(), products: vi.fn(), finance: vi.fn(), alerts: vi.fn() },
}));

const period: PeriodView = {
  preset: "THIS_FY", from: "2026-04-01", to: "2026-10-27", previousFrom: "2025-09-05", previousTo: "2026-03-31",
  granularity: "MONTH", today: "2026-10-27", timezone: "Asia/Kolkata",
};

const overview: Overview = {
  period,
  restricted: [],
  partners: { total: 12, active: 9, inactive: 1, prospect: 1, onboarding: 1, newInPeriod: 4, newPrevious: 2 },
  leads: { total: 30, newInPeriod: 10, newPrevious: 0, open: 14, pipelineValue: "450000.00", openWithoutBudget: 2, wonInPeriod: 2, lostInPeriod: 1, winRate: "66.67", wonTotal: 5, conversionRate: "16.67" },
  estimates: { total: 7, draft: 1, sent: 2, accepted: 1, rejected: 1, invoiced: 2, value: "120000.00", converted: 2, invoiceConversionRate: "33.33" },
  invoices: { issued: 5, gross: "13979.50", taxable: "11847.03", gst: "2132.47", taxablePrevious: "10000.00", cancelled: 1, cancelledValue: "118.00", outstandingCount: 2, outstanding: "589.50", overdueCount: 1, overdue: "236.00", toleranceCount: 1, toleranceResidual: "0.30" },
  payments: { receipts: 4, collected: "13389.70", collectedExGst: "11347.21", gstCollected: "2042.49", collectedExGstPrevious: "12000.00", reversals: 1, reversedAmount: "500.00", partlyPaidInvoices: 1, partlyPaidOutstanding: "353.50" },
  payouts: { commissionEarned: "850.00", commissionEarnedPrevious: "0.00", unbilledCommission: "300.00", pendingLiability: "200.00", approvedLiability: "100.00", paidCount: 1, paidCommission: "500.00", paidTds: "10.00", paidNet: "490.00", negativeAdjustments: 0, negativeAdjustmentBalance: "0.00", adjustmentsAwaitingApproval: 0, byStatus: [] },
  revenueTrend: { granularity: "MONTH", buckets: ["2026-04-01", "2026-05-01"], series: [{ key: "invoicedTaxable", label: "Invoiced (taxable)", unit: "INR", values: ["5000.00", "6847.03"] }, { key: "collectedExGst", label: "Collected (ex-GST)", unit: "INR", values: ["4000.00", "7347.21"] }] },
  leadFunnel: [{ key: "NEW", label: "New", count: 4, amount: "100000.00" }, { key: "WON", label: "Won", count: 5, amount: "0.00" }],
  tierMix: [{ key: "SILVER", label: "Silver", count: 9, amount: null }],
  paymentMethods: [{ key: "UPI", label: "UPI", count: 4, amount: "13389.70" }],
  receivableAging: [{ key: "NOT_DUE", label: "Not due", count: 1, amount: "353.50" }, { key: "D1_30", label: "1–30 days", count: 1, amount: "236.00" }],
  topPartners: [{ partnerId: "p1", name: "Alpha Interiors", code: "PTR-0001", tier: "Silver", status: "ACTIVE", collected: "13389.70", collectedExGst: "11347.21", share: "100.00", invoicedTaxable: "11847.03", commission: "850.00" }],
};

const alerts: Alerts = {
  restricted: [],
  generatedAt: "2026-10-27T05:00:00Z",
  groups: [
    {
      code: "INVOICES_OVERDUE", severity: "danger", module: "INVOICES", title: "Overdue invoices", detail: "Issued invoices past their due date with more than ₹0.50 outstanding.",
      count: 6, amount: "236.00", href: "/invoices",
      items: [{ id: "i1", label: "INV-2026-000001", detail: "Alpha Interiors · due 01 Oct 2026", href: "/invoices/i1" }],
    },
  ],
};

const sales: Sales = {
  period, restricted: [], kpis: overview.leads, funnel: overview.leadFunnel,
  trend: { granularity: "MONTH", buckets: ["2026-04-01"], series: [{ key: "created", label: "Leads created", unit: "COUNT", values: ["10"] }] },
  aging: [{ key: "D0_7", label: "0–7 days", count: 3, amount: null }],
  followUps: { pastExpectedClose: 2, unacknowledged: 1, awaitingEstimate: 3, slaViolationsInPeriod: 4, ackHours: 24, estimateDays: 5 },
  byPartner: [{ partnerId: "p1", name: "Alpha Interiors", created: 10, open: 6, won: 2, lost: 1, winRate: "66.67", pipeline: "450000.00" }],
  conversion: { leadsCreated: 10, leadsWithEstimate: 4, leadToEstimateRate: "40.00", estimatesIssued: 6, estimatesInvoiced: 2, estimateToInvoiceRate: "33.33", won: 2, lost: 1, winRate: "66.67" },
  estimates: overview.estimates,
};

const finance: Finance = {
  period, restricted: [], invoicing: overview.invoices, receivableAging: overview.receivableAging, collections: overview.payments,
  paymentMethods: overview.paymentMethods, trend: overview.revenueTrend, collectionEfficiency: "95.78", payouts: overview.payouts, balances: [],
};

const signIn = (me: ReturnType<typeof makeMe>) => {
  vi.mocked(authService.restore).mockResolvedValue(me);
  vi.mocked(authService.me).mockResolvedValue(me);
};

beforeEach(() => {
  vi.mocked(authService.modules).mockResolvedValue(catalog);
  vi.mocked(dashboardService.overview).mockResolvedValue(overview);
  vi.mocked(dashboardService.sales).mockResolvedValue(sales);
  vi.mocked(dashboardService.finance).mockResolvedValue(finance);
  vi.mocked(dashboardService.alerts).mockResolvedValue(alerts);
});

describe("executive dashboard", () => {
  it("shows server KPIs with period comparisons, charts and top partners", async () => {
    signIn(makeMe("SUPER_ADMIN"));
    renderApp("/dashboard");
    // The first test loads the dashboard chunk (with Recharts) cold, so allow it longer.
    const collected = await screen.findByRole("link", { name: /^Collected \(ex-GST\)/ }, { timeout: 10_000 });
    expect(await within(collected).findByText(/11,347\.21/)).toBeInTheDocument();
    // One column on phones, two on tablets, four on wide screens.
    expect(collected.parentElement).toHaveClass("grid", "sm:grid-cols-2", "xl:grid-cols-4");
    // 11,347.21 against 12,000.00 previously.
    expect(within(collected).getByText(/Down/)).toBeInTheDocument();
    expect(within(collected).getByText("5.4%")).toBeInTheDocument();
    // New leads had no previous-period figure, so no ratio is invented.
    const leads = screen.getByRole("link", { name: /^New leads/ });
    expect(within(leads).getByText("No comparable previous period")).toBeInTheDocument();
    expect(screen.getByText("66.67%")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Invoiced vs collected" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Receivables by age" })).toBeInTheDocument();
    const top = screen.getByRole("table", { name: "Top partners by collections" });
    expect(within(top).getByRole("link", { name: "Alpha Interiors" })).toHaveAttribute("href", "/partners/p1");
    expect(within(top).getByRole("columnheader", { name: "Commission" })).toBeInTheDocument();
    expect(await screen.findByText(/1 Apr 2026 – 27 Oct 2026 · IST/)).toBeInTheDocument();
    expect(dashboardService.overview).toHaveBeenCalledWith({ preset: "THIS_FY", from: "", to: "" }, expect.anything());
  });

  it("lists backend alerts with record links and how many more there are", async () => {
    signIn(makeMe("SUPER_ADMIN"));
    renderApp("/dashboard?view=attention");
    const list = await screen.findByRole("list", { name: "Alerts" });
    expect(within(list).getByText("Overdue invoices · 6")).toBeInTheDocument();
    expect(within(list).getByRole("link", { name: "INV-2026-000001" })).toHaveAttribute("href", "/invoices/i1");
    expect(within(list).getByText("and 5 more")).toBeInTheDocument();
    expect(within(list).getByText(/Total ₹\s?236\.00/)).toBeInTheDocument();
  });

  it("says when nothing needs attention", async () => {
    vi.mocked(dashboardService.alerts).mockResolvedValue({ ...alerts, groups: [] });
    signIn(makeMe("SUPER_ADMIN"));
    renderApp("/dashboard?view=attention");
    expect(await screen.findByText("Nothing needs attention right now.")).toBeInTheDocument();
  });

  it("refetches for a preset and only sends a custom range once it is complete and in order", async () => {
    signIn(makeMe("SUPER_ADMIN"));
    renderApp("/dashboard");
    await screen.findByRole("link", { name: /^Collected \(ex-GST\)/ });
    await userEvent.selectOptions(screen.getByLabelText("Period"), "LAST_MONTH");
    await vi.waitFor(() => expect(dashboardService.overview).toHaveBeenLastCalledWith(expect.objectContaining({ preset: "LAST_MONTH" }), expect.anything()));

    await userEvent.selectOptions(screen.getByLabelText("Period"), "CUSTOM");
    expect(screen.getByText("Choose both dates (up to three years).")).toBeInTheDocument();
    const calls = vi.mocked(dashboardService.overview).mock.calls.length;
    const from = screen.getByLabelText("From date");
    const to = screen.getByLabelText("To date");
    await userEvent.type(from, "2026-06-30");
    await userEvent.type(to, "2026-06-01");
    expect(screen.getByText("The start date must be on or before the end date.")).toBeInTheDocument();
    expect(dashboardService.overview).toHaveBeenCalledTimes(calls);
    await userEvent.clear(from);
    await userEvent.type(from, "2026-05-01");
    await vi.waitFor(() =>
      expect(dashboardService.overview).toHaveBeenLastCalledWith({ preset: "CUSTOM", from: "2026-05-01", to: "2026-06-01" }, expect.anything()),
    );
  });

  it("shows an error with retry when the overview fails", async () => {
    vi.mocked(dashboardService.overview).mockRejectedValueOnce(new ApiRequestError(500, "INTERNAL_ERROR", "Something went wrong."));
    signIn(makeMe("SUPER_ADMIN"));
    renderApp("/dashboard");
    await userEvent.click(await screen.findByRole("button", { name: /Try again|Retry/ }));
    expect(await screen.findByRole("link", { name: /^Collected \(ex-GST\)/ })).toBeInTheDocument();
  });

  it("opens the sales tab with conversion and follow-ups", async () => {
    signIn(makeMe("SUPER_ADMIN"));
    renderApp("/dashboard");
    await userEvent.click(await screen.findByRole("tab", { name: "Sales" }));
    expect(await screen.findByText("Not acknowledged within 24 h")).toBeInTheDocument();
    expect(screen.getByText("40.00%")).toBeInTheDocument();
    expect(screen.getByRole("table", { name: "Leads by partner" })).toBeInTheDocument();
    expect(dashboardService.sales).toHaveBeenCalled();
    // Other tabs are not fetched until opened.
    expect(dashboardService.partners).not.toHaveBeenCalled();
  });

  it("opens the finance tab with the tolerance residual reported separately", async () => {
    signIn(makeMe("SUPER_ADMIN"));
    renderApp("/dashboard?tab=finance");
    expect(await screen.findByText("Closed within ₹0.50 tolerance (1)")).toBeInTheDocument();
    expect(screen.getByText("95.78%")).toBeInTheDocument();
  });
});

describe("dashboard permissions", () => {
  it("shows only tabs for readable modules and explains scoped modules", async () => {
    const me = makeMe("ADMIN", { modules: [row("DASHBOARD"), row("LEADS", { dataScope: "SELF" }), row("PRODUCTS")] });
    signIn(me);
    vi.mocked(dashboardService.overview).mockResolvedValue({
      ...overview, restricted: [{ module: "LEADS", reason: "SCOPE_SELF" }, { module: "PAYOUTS", reason: "NO_ACCESS" }],
      partners: null, leads: null, estimates: null, invoices: null, payments: null, payouts: null, revenueTrend: null, leadFunnel: null, tierMix: null, paymentMethods: null, receivableAging: null, topPartners: null,
    });
    vi.mocked(dashboardService.alerts).mockResolvedValue({ ...alerts, groups: [{ ...alerts.groups[0], module: "INVOICES" }] });
    renderApp("/dashboard");
    const tabs = await screen.findByRole("tablist", { name: "Dashboard sections" });
    expect(within(tabs).getAllByRole("tab").map((t) => t.textContent)).toEqual(["Overview", "Sales", "Products"]);
    expect(await screen.findByText(/you can see only your own records/)).toBeInTheDocument();
    // No-access modules are not announced; their KPIs simply aren't there.
    expect(screen.queryByText(/Payouts:/)).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /^Collected \(ex-GST\)/ })).not.toBeInTheDocument();
    expect(screen.getByText(/Needs Leads read access with the All records scope/)).toBeInTheDocument();
    // Alert records are not links when the module can't be opened.
    await userEvent.click(screen.getByRole("tab", { name: /Needs attention/ }));
    const list = await screen.findByRole("list", { name: "Alerts" });
    expect(within(list).queryByRole("link")).not.toBeInTheDocument();
  });

  it("shows no analytics for a user with only the dashboard", async () => {
    signIn(makeMe("USER", { modules: [row("DASHBOARD")] }));
    renderApp("/dashboard");
    expect(await screen.findByRole("heading", { name: /Welcome, Test/ })).toBeInTheDocument();
    expect(screen.queryByRole("tablist", { name: "Dashboard sections" })).not.toBeInTheDocument();
    expect(dashboardService.overview).not.toHaveBeenCalled();
  });
});
