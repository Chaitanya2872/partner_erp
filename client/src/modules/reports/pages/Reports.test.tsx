import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderApp } from "@/test/renderApp";
import { catalog, makeMe, row } from "@/test/fixtures";
import { authService } from "@/services/auth.service";
import { ApiRequestError } from "@/services/api";
import * as download from "@/utils/download";
import { reportService } from "../services/reportService";
import type { ReportInfo, ReportResult } from "../types/report.types";

vi.mock("@/services/auth.service", () => ({
  authService: { restore: vi.fn(), me: vi.fn(), modules: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() },
}));
vi.mock("../services/reportService", () => ({
  reportService: { catalog: vi.fn(), run: vi.fn(), exportCsv: vi.fn(), partnerOptions: vi.fn() },
}));

const invoices: ReportInfo = {
  code: "invoices", title: "Invoices", description: "Issued and cancelled invoices dated in the period.", module: "INVOICES",
  available: true, reason: null, canExport: false, dateBasis: "Invoice date",
  columns: [
    { key: "invoice_number", label: "Invoice", type: "TEXT", sortable: true, total: false },
    { key: "invoice_date", label: "Date", type: "DATE", sortable: true, total: false },
    { key: "payment_status", label: "Payment", type: "CODE", sortable: true, total: false },
    { key: "grand_total", label: "Total", type: "MONEY", sortable: true, total: true },
    { key: "balance", label: "Balance", type: "MONEY", sortable: true, total: true },
  ],
  statusColumn: "payment_status",
  statuses: [{ value: "PAID", label: "Paid" }, { value: "PARTLY_PAID", label: "Partly paid" }],
  partnerFilter: true, searchColumns: ["invoice_number"], defaultSort: "invoice_date", defaultDescending: true,
};
const tds: ReportInfo = {
  ...invoices, code: "tds-adjustments", title: "TDS and adjustments", module: "PAYOUTS", available: false, reason: "SCOPE_SELF",
  statusColumn: null, statuses: [], partnerFilter: false, searchColumns: [],
};

const result: ReportResult = {
  code: "invoices", title: "Invoices",
  period: { preset: "THIS_FY", from: "2026-04-01", to: "2026-10-27", previousFrom: "2025-09-05", previousTo: "2026-03-31", granularity: "MONTH", today: "2026-10-27", timezone: "Asia/Kolkata" },
  columns: invoices.columns,
  rows: [
    { id: "i1", link: "/invoices/i1", invoice_number: "INV-2026-000001", invoice_date: "2026-10-01", payment_status: "PARTLY_PAID", grand_total: "2360.00", balance: "1360.00" },
    { id: "i2", link: null, invoice_number: "INV-2026-000002", invoice_date: "2026-10-02", payment_status: "PAID", grand_total: "118.00", balance: "0.30" },
  ],
  totals: { grand_total: "2478.00", balance: "1360.30" },
  page: 0, size: 25, totalElements: 2, totalPages: 1, sort: "invoice_date,desc",
};

const signIn = (me: ReturnType<typeof makeMe>) => {
  vi.mocked(authService.restore).mockResolvedValue(me);
  vi.mocked(authService.me).mockResolvedValue(me);
};
const reader = () => makeMe("ADMIN", { modules: [row("DASHBOARD"), row("REPORTS"), row("INVOICES", { dataScope: "ALL" }), row("PARTNERS", { dataScope: "ALL" })] });

beforeEach(() => {
  vi.mocked(authService.modules).mockResolvedValue(catalog);
  vi.mocked(reportService.catalog).mockResolvedValue([invoices, tds]);
  vi.mocked(reportService.run).mockResolvedValue(result);
  vi.mocked(reportService.partnerOptions).mockResolvedValue([{ id: "p1", name: "Alpha Interiors", code: "PTR-0001" }]);
});

describe("reports", () => {
  it("runs the first available report with formatted cells, links and totals over every row", async () => {
    signIn(reader());
    renderApp("/reports");
    const table = await screen.findByRole("table", { name: "Invoices" });
    expect(await within(table).findByRole("link", { name: "INV-2026-000001" })).toHaveAttribute("href", "/invoices/i1");
    // Rows without a detail page the user may open aren't links.
    expect(within(table).queryByRole("link", { name: "INV-2026-000002" })).not.toBeInTheDocument();
    // Status codes are shown with their labels.
    expect(within(table).getByText("Partly paid")).toBeInTheDocument();
    expect(within(table).getByText(/01 Oct 2026/)).toBeInTheDocument();
    expect(screen.getByText("Totals · 2 rows")).toBeInTheDocument();
    expect(screen.getByText(/1,360\.30/)).toBeInTheDocument();
    expect(screen.getByText(/Period applies to: invoice date/)).toBeInTheDocument();
    expect(reportService.run).toHaveBeenCalledWith("invoices", expect.objectContaining({ preset: "THIS_FY", page: 0, size: 25 }), expect.anything());
    // Export needs Reports export plus the module's export; the catalogue says no.
    expect(screen.queryByRole("button", { name: /Export CSV/ })).not.toBeInTheDocument();
  });

  it("passes search, status, partner, sort and paging to the server", async () => {
    signIn(reader());
    renderApp("/reports");
    await screen.findByRole("table", { name: "Invoices" });
    await userEvent.selectOptions(screen.getByLabelText("Filter by status"), "PAID");
    await vi.waitFor(() => expect(reportService.run).toHaveBeenLastCalledWith("invoices", expect.objectContaining({ status: "PAID", page: 0 }), expect.anything()));
    await userEvent.type(screen.getByLabelText("Search Invoices"), "000002");
    await vi.waitFor(() => expect(reportService.run).toHaveBeenLastCalledWith("invoices", expect.objectContaining({ q: "000002" }), expect.anything()));
    await userEvent.click(screen.getByRole("combobox", { name: "Filter by partner" }));
    await userEvent.click(await screen.findByRole("option", { name: /Alpha Interiors/ }));
    await vi.waitFor(() => expect(reportService.run).toHaveBeenLastCalledWith("invoices", expect.objectContaining({ partnerId: "p1" }), expect.anything()));
    await userEvent.click(screen.getByRole("button", { name: /^Total/ }));
    await vi.waitFor(() => expect(reportService.run).toHaveBeenLastCalledWith("invoices", expect.objectContaining({ sort: expect.stringMatching(/^grand_total,/) }), expect.anything()));
  });

  it("exports the filtered report when the user may export", async () => {
    vi.mocked(reportService.catalog).mockResolvedValue([{ ...invoices, canExport: true }, tds]);
    vi.mocked(reportService.exportCsv).mockResolvedValue({ text: "invoice_number\n", filename: "iotiq-report-invoices-2026-04-01-to-2026-10-27.csv" });
    const save = vi.spyOn(download, "saveTextFile").mockImplementation(() => {});
    signIn(reader());
    renderApp("/reports");
    await screen.findByRole("table", { name: "Invoices" });
    await userEvent.selectOptions(screen.getByLabelText("Period"), "LAST_MONTH");
    await userEvent.click(screen.getByRole("button", { name: /Export CSV/ }));
    await vi.waitFor(() => expect(reportService.exportCsv).toHaveBeenCalledWith("invoices", expect.objectContaining({ preset: "LAST_MONTH" })));
    expect(save).toHaveBeenCalledWith("invoice_number\n", "iotiq-report-invoices-2026-04-01-to-2026-10-27.csv");
  });

  it("explains why a report is unavailable and doesn't run it", async () => {
    signIn(reader());
    renderApp("/reports?report=tds-adjustments");
    expect(await screen.findByText("TDS and adjustments is not available")).toBeInTheDocument();
    expect(screen.getByText(/limited to your own records/)).toBeInTheDocument();
    expect(reportService.run).not.toHaveBeenCalled();
    expect(screen.getByRole("option", { name: "TDS and adjustments (not available)" })).toBeDisabled();
  });

  it("shows empty and error states", async () => {
    vi.mocked(reportService.run).mockResolvedValueOnce({ ...result, rows: [], totals: { grand_total: "0.00", balance: "0.00" }, totalElements: 0, totalPages: 0 });
    signIn(reader());
    renderApp("/reports");
    expect(await screen.findByText("Nothing in this period")).toBeInTheDocument();

    vi.mocked(reportService.run).mockRejectedValue(new ApiRequestError(400, "VALIDATION_FAILED", "Choose a range of at most 1,096 days."));
    await userEvent.selectOptions(screen.getByLabelText("Period"), "TODAY");
    expect(await screen.findByText(/Choose a range of at most 1,096 days|couldn't|could not/i)).toBeInTheDocument();
  });

  it("labels codes and links the first column that has a value", async () => {
    const partners: ReportInfo = {
      ...invoices, code: "partners", title: "Partners", module: "PARTNERS", statusColumn: null, statuses: [],
      columns: [
        { key: "code", label: "Code", type: "TEXT", sortable: true, total: false },
        { key: "name", label: "Partner", type: "TEXT", sortable: true, total: false },
        { key: "category", label: "Category", type: "CODE", sortable: true, total: false },
        { key: "bucket", label: "Ageing", type: "CODE", sortable: true, total: false },
      ],
    };
    vi.mocked(reportService.catalog).mockResolvedValue([partners]);
    vi.mocked(reportService.run).mockResolvedValue({
      ...result, code: "partners", title: "Partners", columns: partners.columns, totals: {},
      rows: [{ id: "p1", link: "/partners/p1", code: null, name: "Alpha Interiors", category: "INTERIOR_DESIGNER", bucket: "D1_30" }],
    });
    signIn(reader());
    renderApp("/reports");
    const table = await screen.findByRole("table", { name: "Partners" });
    expect(await within(table).findByRole("link", { name: "Alpha Interiors" })).toHaveAttribute("href", "/partners/p1");
    expect(within(table).getByText("Interior designer")).toBeInTheDocument();
    expect(within(table).getByText("1–30 days")).toBeInTheDocument();
  });

  it("hides the partner filter without Partners read", async () => {
    signIn(makeMe("ADMIN", { modules: [row("DASHBOARD"), row("REPORTS"), row("INVOICES", { dataScope: "ALL" })] }));
    renderApp("/reports");
    await screen.findByRole("table", { name: "Invoices" });
    expect(screen.queryByRole("combobox", { name: "Filter by partner" })).not.toBeInTheDocument();
    expect(reportService.partnerOptions).not.toHaveBeenCalled();
  });
});
