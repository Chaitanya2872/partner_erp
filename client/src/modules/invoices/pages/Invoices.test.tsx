import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderApp } from "@/test/renderApp";
import { catalog, makeMe, row } from "@/test/fixtures";
import { authService } from "@/services/auth.service";
import { leadService } from "@/modules/leads/services/leadService";
import { estimateService } from "@/modules/estimates/services/estimateService";
import { settingsService } from "@/modules/settings/services/settingsService";
import type { Estimate, ProductOption } from "@/modules/estimates/types/estimate.types";
import { invoiceService } from "../services/invoiceService";
import type { GstRegistration, Invoice, InvoiceMeta, InvoicePreview, InvoiceSummary, TaxTreatment } from "../types/invoice.types";

vi.mock("@/services/auth.service", () => ({
  authService: { restore: vi.fn(), me: vi.fn(), modules: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() },
}));
vi.mock("@/modules/leads/services/leadService", () => ({
  leadService: { list: vi.fn(), meta: vi.fn(), get: vi.fn(), history: vi.fn(), create: vi.fn(), update: vi.fn(), setStage: vi.fn(), acknowledge: vi.fn(), remove: vi.fn() },
}));
vi.mock("@/modules/estimates/services/estimateService", () => ({
  estimateService: { get: vi.fn(), history: vi.fn(), document: vi.fn(), internalDocument: vi.fn(), productOptions: vi.fn(), meta: vi.fn() },
}));
vi.mock("@/modules/settings/services/settingsService", () => ({
  settingsService: { gstRegistration: vi.fn(), updateGstRegistration: vi.fn() },
}));
vi.mock("../services/invoiceService", () => ({
  invoiceService: {
    list: vi.fn(),
    meta: vi.fn(),
    get: vi.fn(),
    history: vi.fn(),
    preview: vi.fn(),
    create: vi.fn(),
    convert: vi.fn(),
    update: vi.fn(),
    confirmTax: vi.fn(),
    issue: vi.fn(),
    cancel: vi.fn(),
    discard: vi.fn(),
    exportCsv: vi.fn(),
    document: vi.fn(),
    internal: vi.fn(),
    internalDocument: vi.fn(),
    leadOptions: vi.fn(),
  },
}));

const states = [
  { code: "29", name: "Karnataka", utgst: false },
  { code: "36", name: "Telangana", utgst: false },
];

const meta: InvoiceMeta = {
  statuses: [
    { value: "DRAFT", label: "Draft" },
    { value: "ISSUED", label: "Issued" },
    { value: "CANCELLED", label: "Cancelled" },
  ],
  paymentStatuses: [
    { value: "UNPAID", label: "Unpaid" },
    { value: "PARTIALLY_PAID", label: "Partially paid" },
    { value: "PAID", label: "Paid" },
    { value: "OVERDUE", label: "Overdue" },
  ],
  billTo: [
    { value: "CLIENT", label: "Client" },
    { value: "PARTNER", label: "Partner (bulk purchase)" },
  ],
  states,
  placeOfSupplyBases: [
    { value: "RECIPIENT_LOCATION", label: "Recipient's location" },
    { value: "DELIVERY_LOCATION", label: "Delivery location" },
    { value: "INSTALLATION_SITE", label: "Installation site" },
    { value: "OTHER", label: "Other" },
  ],
  dueDays: 15,
  terms: "Payment within 15 days.",
  defaultGstPercent: "18",
  maxLines: 100,
  canOverrideFee: false,
  canApprove: false,
  canCancel: false,
  supplierComplete: true,
  supplierStateCode: "36",
};

const intra: TaxTreatment = {
  taxType: "INTRA",
  taxTypeLabel: "CGST + SGST",
  utgst: false,
  stateTaxLabel: "SGST",
  missing: [],
  reasons: [],
  confirmationRequired: false,
  confirmed: false,
  confirmedAt: null,
  confirmedByName: null,
  confirmationNote: null,
};

const totals = {
  subtotal: "2000.00",
  discountAmount: "0.00",
  taxableAmount: "2000.00",
  cgstAmount: "180.00",
  sgstAmount: "180.00",
  igstAmount: "0.00",
  gstAmount: "360.00",
  grandTotal: "2360.00",
};

const line = {
  id: "il1",
  lineNo: 1,
  productId: "pr1",
  sku: "SG-10F",
  productActive: true,
  description: "Smart Glide",
  hsn: "8536",
  unit: "nos",
  quantity: "2",
  unitPrice: "1000",
  gstPercent: "18",
  amount: "2000.00",
  taxableValue: "2000.00",
  cgstAmount: "180.00",
  sgstAmount: "180.00",
  igstAmount: "0.00",
  taxAmount: "360.00",
  lineTotal: "2360.00",
};

const draft: Invoice = {
  id: "i1",
  invoiceNumber: null,
  status: "DRAFT",
  statusLabel: "Draft",
  invoiceDate: null,
  dueDate: null,
  dueDays: 15,
  paymentStatus: null,
  paymentStatusLabel: null,
  paidAmount: null,
  balance: null,
  lead: { id: "l1", leadNumber: "LEAD-2026-000001", clientName: "Rao Residence", clientPhone: null, city: "Hyderabad", projectTypeLabel: "Villa", stage: "ESTIMATE_SENT", stageLabel: "Estimate sent", deleted: false },
  partner: { id: "p1", name: "Alpha Interiors" },
  estimate: null,
  billTo: "CLIENT",
  billToLabel: "Client",
  discountPercent: "0",
  feePercent: "8",
  notes: "Payment within 15 days.",
  supplier: { name: null, address: null, stateCode: null, stateName: null, gstin: null },
  billing: { name: "Rao Residence", address: "Road 12, Hyderabad", stateCode: "36", stateName: "Telangana", gstin: null },
  placeOfSupplyCode: "36",
  placeOfSupplyName: "Telangana",
  placeOfSupplyBasis: "INSTALLATION_SITE",
  placeOfSupplyBasisLabel: "Installation site",
  tax: intra,
  lines: [line],
  totals,
  comparison: null,
  issuedAt: null,
  issuedByName: null,
  cancelledAt: null,
  cancelledByName: null,
  cancelReason: null,
  createdAt: "2026-10-26T00:00:00Z",
  createdByName: "Test Admin",
  updatedAt: "2026-10-26T00:00:00Z",
  version: 2,
};

const issued: Invoice = {
  ...draft,
  invoiceNumber: "INV-2026-000001",
  status: "ISSUED",
  statusLabel: "Issued",
  invoiceDate: "2026-10-26",
  dueDate: "2026-11-10",
  paymentStatus: "UNPAID",
  paymentStatusLabel: "Unpaid",
  paidAmount: "0.00",
  balance: "2360.00",
  supplier: { name: "IOTIQ", address: "Plot 7, Hyderabad", stateCode: "36", stateName: "Telangana", gstin: "36AABCI1234A1ZC" },
  issuedAt: "2026-10-26T05:00:00Z",
  issuedByName: "Test Admin",
  version: 3,
};

const summaries: InvoiceSummary[] = [
  {
    id: "i1", invoiceNumber: null, status: "DRAFT", statusLabel: "Draft", invoiceDate: null, dueDate: null, paymentStatus: null, paymentStatusLabel: null,
    leadId: "l1", leadNumber: "LEAD-2026-000001", clientName: "Rao Residence", billingName: "Rao Residence", partner: { id: "p1", name: "Alpha Interiors" },
    estimateNumber: null, taxType: "INTRA", taxableAmount: "2000.00", grandTotal: "2360.00", paidAmount: null, balance: null, createdAt: "2026-10-26T00:00:00Z", version: 2,
  },
  {
    id: "i2", invoiceNumber: "INV-2026-000002", status: "ISSUED", statusLabel: "Issued", invoiceDate: "2026-10-20", dueDate: "2026-11-04", paymentStatus: "PARTLY_PAID",
    paymentStatusLabel: "Partially paid", leadId: "l2", leadNumber: "LEAD-2026-000002", clientName: "Iyer Villa", billingName: "Iyer Villa", partner: { id: "p1", name: "Alpha Interiors" },
    estimateNumber: "EST-2026-000004", taxType: "INTER", taxableAmount: "1000.00", grandTotal: "1180.00", paidAmount: "500.00", balance: "680.00", createdAt: "2026-10-20T00:00:00Z", version: 1,
  },
];

const product: ProductOption = { id: "pr1", sku: "SG-10F", category: "SWITCHES", categoryLabel: "Switches", name: "Smart Glide", hsn: "8536", unit: "nos", rate: "1000", gstPercent: "18" };

const preview = (lineCount: number, tax: TaxTreatment = intra): InvoicePreview => ({
  lines: Array.from({ length: lineCount }, (_, i) => ({ ...line, id: null, lineNo: i + 1, quantity: "1", amount: "1000.00", taxableValue: "1000.00", cgstAmount: "90.00", sgstAmount: "90.00", taxAmount: "180.00", lineTotal: "1180.00" })),
  totals: { ...totals, subtotal: "1000.00", taxableAmount: "1000.00", cgstAmount: "90.00", sgstAmount: "90.00", gstAmount: "180.00", grandTotal: "1180.00" },
  tax,
  comparison: null,
  defaultFeePercent: "8",
  feePercent: "8",
  feeOverride: false,
});

const registration: GstRegistration = { legalName: "IOTIQ", address: null, stateCode: null, stateName: null, gstin: null, complete: false, version: 4, states };

/** Invoices read + write (ADMIN template); no delete, export, approve, internal copy or B2C prices. */
const writer = () => makeMe("ADMIN");
const reader = () => makeMe("ADMIN", { modules: [row("DASHBOARD"), row("INVOICES", { dataScope: "ALL" })] });
/** Invoices write only: leads and products come from restricted pickers. */
const invoicesOnly = () => makeMe("ADMIN", { modules: [row("DASHBOARD"), row("INVOICES", { write: true, dataScope: "ALL" })] });
const manager = () =>
  makeMe("ADMIN", {
    modules: [row("DASHBOARD"), row("ESTIMATES", { write: true, dataScope: "ALL" }), row("INVOICES", { write: true, delete: true, export: true, approve: true, dataScope: "ALL" }), row("INTERNAL_B2C")],
  });

const signIn = (me: ReturnType<typeof makeMe>) => {
  vi.mocked(authService.restore).mockResolvedValue(me);
  vi.mocked(authService.me).mockResolvedValue(me);
};

beforeEach(() => {
  vi.mocked(authService.modules).mockResolvedValue(catalog);
  vi.mocked(invoiceService.meta).mockResolvedValue(meta);
  vi.mocked(invoiceService.list).mockResolvedValue({ content: summaries, meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 } });
  vi.mocked(invoiceService.get).mockResolvedValue(draft);
  vi.mocked(invoiceService.history).mockResolvedValue([
    { id: "h1", at: "2026-10-26T00:00:00Z", actorName: "Test Admin", action: "INVOICE_CREATED", changes: [{ field: "grandTotal", from: null, to: "2360.00" }] },
  ]);
  vi.mocked(invoiceService.document).mockResolvedValue({ text: "<html><body>DRAFT INVOICE</body></html>", filename: "draft-invoice.html" });
  vi.mocked(invoiceService.internalDocument).mockResolvedValue({ text: "<html><body>INTERNAL ONLY</body></html>", filename: "INTERNAL.html" });
  vi.mocked(invoiceService.leadOptions).mockResolvedValue([{ id: "l1", leadNumber: "LEAD-2026-000001", clientName: "Rao Residence", partner: { id: "p1", name: "Alpha Interiors" } }]);
  vi.mocked(invoiceService.preview).mockImplementation(async (input) => preview(input.lines?.length ?? 1));
  vi.mocked(estimateService.productOptions).mockResolvedValue([product]);
  vi.mocked(settingsService.gstRegistration).mockResolvedValue(registration);
});

describe("invoice list", () => {
  it("shows drafts without a number and issued invoices with their payment status", async () => {
    signIn(writer());
    renderApp("/invoices");
    const draftRow = (await screen.findByRole("link", { name: "Draft" })).closest("tr")!;
    expect(within(draftRow).getByText("Rao Residence")).toBeInTheDocument();
    const issuedRow = screen.getByRole("link", { name: "INV-2026-000002" }).closest("tr")!;
    expect(within(issuedRow).getByText("Partially paid")).toBeInTheDocument();
    expect(within(issuedRow).getByText("₹680.00")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Export CSV/ })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /New invoice/ })).toBeInTheDocument();
  });

  it("sends status and payment filters to the server", async () => {
    signIn(writer());
    renderApp("/invoices");
    await screen.findByRole("link", { name: "INV-2026-000002" });
    await userEvent.selectOptions(screen.getByLabelText("Filter by status"), "ISSUED");
    await userEvent.selectOptions(screen.getByLabelText("Filter by payment"), "PARTIALLY_PAID");
    await vi.waitFor(() => expect(vi.mocked(invoiceService.list).mock.lastCall?.[0]).toMatchObject({ status: "ISSUED", payment: "PARTIALLY_PAID", page: 0 }));
  });

  it("offers no create to readers", async () => {
    signIn(reader());
    renderApp("/invoices");
    await screen.findByRole("link", { name: "INV-2026-000002" });
    expect(screen.queryByRole("link", { name: /New invoice/ })).not.toBeInTheDocument();
  });
});

describe("invoice details", () => {
  it("shows the per-line tax breakdown and issues with the version and the expected total", async () => {
    signIn(writer());
    vi.mocked(invoiceService.issue).mockResolvedValue(issued);
    renderApp("/invoices/i1");
    expect(await screen.findByRole("heading", { name: "Draft invoice" })).toBeInTheDocument();
    const breakdown = screen.getByRole("table", { name: "Invoice items" });
    expect(within(breakdown).getByRole("columnheader", { name: "CGST" })).toBeInTheDocument();
    expect(within(breakdown).getByRole("columnheader", { name: "SGST" })).toBeInTheDocument();
    expect(within(breakdown).queryByRole("columnheader", { name: "IGST" })).not.toBeInTheDocument();
    // Without Invoices delete there is no discard; without approve no tax confirmation.
    expect(screen.queryByRole("button", { name: /Discard/ })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Issue invoice/ }));
    const dialog = await screen.findByRole("dialog", { name: "Issue this invoice?" });
    await userEvent.click(within(dialog).getByRole("button", { name: "Issue invoice" }));
    await vi.waitFor(() => expect(invoiceService.issue).toHaveBeenCalledWith("i1", { version: 2, expectedGrandTotal: "2360.00" }));
  });

  it("asks an approver for a note before confirming a doubtful tax treatment", async () => {
    signIn(manager());
    const doubtful: TaxTreatment = { ...intra, taxType: "INTER", taxTypeLabel: "IGST", stateTaxLabel: null, confirmationRequired: true, reasons: [{ code: "PLACE_OF_SUPPLY_DIFFERS", message: "The place of supply differs from the billing state." }] };
    vi.mocked(invoiceService.get).mockResolvedValue({ ...draft, placeOfSupplyCode: "29", placeOfSupplyName: "Karnataka", tax: doubtful });
    vi.mocked(invoiceService.confirmTax).mockResolvedValue(draft);
    renderApp("/invoices/i1");
    expect(await screen.findByText("Tax treatment needs confirmation")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Confirm tax/ }));
    const dialog = await screen.findByRole("dialog", { name: "Confirm the tax treatment" });
    await userEvent.click(within(dialog).getByRole("button", { name: "Confirm tax" }));
    expect(await within(dialog).findByText("Explain why this tax treatment is right.")).toBeInTheDocument();
    expect(invoiceService.confirmTax).not.toHaveBeenCalled();
    await userEvent.type(within(dialog).getByLabelText(/Why is this treatment right/), "Installed at the Bengaluru site");
    await userEvent.click(within(dialog).getByRole("button", { name: "Confirm tax" }));
    await vi.waitFor(() => expect(invoiceService.confirmTax).toHaveBeenCalledWith("i1", { version: 2, note: "Installed at the Bengaluru site" }));
  });

  it("hides the confirmation from users without Invoices approve", async () => {
    signIn(writer());
    vi.mocked(invoiceService.get).mockResolvedValue({ ...draft, tax: { ...intra, confirmationRequired: true, reasons: [{ code: "OTHER_BASIS", message: "Another basis." }] } });
    renderApp("/invoices/i1");
    expect(await screen.findByText("Tax treatment needs confirmation")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Confirm tax/ })).not.toBeInTheDocument();
  });

  it("locks an issued invoice and cancels it only with a reason", async () => {
    signIn(manager());
    vi.mocked(invoiceService.get).mockResolvedValue(issued);
    vi.mocked(invoiceService.cancel).mockResolvedValue({ ...issued, status: "CANCELLED", statusLabel: "Cancelled", paymentStatus: "CANCELLED" });
    renderApp("/invoices/i1");
    expect(await screen.findByRole("heading", { name: "INV-2026-000001" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Edit/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Issue invoice/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Discard/ })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Cancel invoice/ }));
    const dialog = await screen.findByRole("dialog", { name: "Cancel INV-2026-000001?" });
    await userEvent.click(within(dialog).getByRole("button", { name: "Cancel invoice" }));
    expect(await within(dialog).findByText("Give the reason for cancelling.")).toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText(/Reason for cancelling/), "Duplicate billing");
    await userEvent.click(within(dialog).getByRole("button", { name: "Cancel invoice" }));
    await vi.waitFor(() => expect(invoiceService.cancel).toHaveBeenCalledWith("i1", { version: 3, reason: "Duplicate billing" }));
  });

  it("opens the internal copy of an issued invoice only for Internal B2C readers", async () => {
    signIn(manager());
    vi.mocked(invoiceService.get).mockResolvedValue(issued);
    renderApp("/invoices/i1");
    await userEvent.click(await screen.findByRole("tab", { name: "Internal B2C copy" }));
    expect(await screen.findByTitle("Internal B2C copy")).toBeInTheDocument();
    expect(invoiceService.internalDocument).toHaveBeenCalledWith("i1");
  });

  it("never offers the internal copy without Internal B2C read", async () => {
    signIn(writer());
    vi.mocked(invoiceService.get).mockResolvedValue(issued);
    renderApp("/invoices/i1");
    expect(await screen.findByTitle("Invoice copy")).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "Internal B2C copy" })).not.toBeInTheDocument();
    expect(invoiceService.internalDocument).not.toHaveBeenCalled();
  });

  it("shows IGST for an inter-state invoice", async () => {
    signIn(writer());
    const inter: TaxTreatment = { ...intra, taxType: "INTER", taxTypeLabel: "IGST", stateTaxLabel: null };
    vi.mocked(invoiceService.get).mockResolvedValue({ ...issued, tax: inter, lines: [{ ...line, cgstAmount: "0.00", sgstAmount: "0.00", igstAmount: "360.00" }] });
    renderApp("/invoices/i1");
    const breakdown = await screen.findByRole("table", { name: "Invoice items" });
    expect(within(breakdown).getByRole("columnheader", { name: "IGST" })).toBeInTheDocument();
    expect(within(breakdown).queryByRole("columnheader", { name: "CGST" })).not.toBeInTheDocument();
  });
});

describe("invoice form", () => {
  it("creates a direct invoice through restricted pickers without Leads or Products read", async () => {
    signIn(invoicesOnly());
    vi.mocked(invoiceService.create).mockResolvedValue(draft);
    renderApp("/invoices/new");
    await userEvent.click(await screen.findByRole("combobox", { name: /Lead/ }));
    await userEvent.type(screen.getByRole("searchbox", { name: "Search leads" }), "Rao");
    await vi.waitFor(() => expect(invoiceService.leadOptions).toHaveBeenCalledWith({ q: "Rao" }, expect.anything()));
    await userEvent.click(await screen.findByRole("option", { name: /Rao Residence/ }));
    await userEvent.click(screen.getByRole("button", { name: /Add item/ }));
    await userEvent.click(await screen.findByRole("button", { name: "Add Smart Glide" }));
    // Without Invoices approve the copied GST rate can't be changed.
    expect(screen.getByLabelText("GST for SG-10F Smart Glide")).toHaveAttribute("readonly");
    await userEvent.selectOptions(screen.getByLabelText(/^Place of supply/), "36");
    await userEvent.selectOptions(screen.getByLabelText(/^Basis/), "INSTALLATION_SITE");
    expect(await screen.findAllByText("₹1,180.00")).not.toHaveLength(0);
    await userEvent.click(screen.getByRole("button", { name: "Create draft" }));
    await vi.waitFor(() => expect(invoiceService.create).toHaveBeenCalled());
    expect(vi.mocked(invoiceService.create).mock.calls[0][0]).toMatchObject({
      leadId: "l1",
      billTo: "CLIENT",
      placeOfSupplyCode: "36",
      placeOfSupplyBasis: "INSTALLATION_SITE",
      lines: [{ productId: "pr1", quantity: "1", unitPrice: "1000", gstPercent: "18" }],
    });
    expect(leadService.list).not.toHaveBeenCalled();
    expect(leadService.get).not.toHaveBeenCalled();
  });

  it("never guesses the tax: no place of supply means no tax type", async () => {
    signIn(writer());
    vi.mocked(invoiceService.preview).mockImplementation(async (input) =>
      preview(input.lines?.length ?? 1, { ...intra, taxType: null, taxTypeLabel: null, stateTaxLabel: null, missing: ["placeOfSupplyCode", "placeOfSupplyBasis"] }),
    );
    renderApp("/invoices/new");
    await userEvent.click(await screen.findByRole("combobox", { name: /Lead/ }));
    await userEvent.click(await screen.findByRole("option", { name: /Rao Residence/ }));
    await userEvent.click(screen.getByRole("button", { name: /Add item/ }));
    await userEvent.click(await screen.findByRole("button", { name: "Add Smart Glide" }));
    expect(screen.getByLabelText(/^Place of supply/)).toHaveValue("");
    expect(await screen.findByText("Tax treatment not decided yet")).toBeInTheDocument();
    expect(screen.getByText("Set the place of supply")).toBeInTheDocument();
  });

  it("keeps a converted draft's commercial values and sends only its billing details", async () => {
    signIn(writer());
    const converted: Invoice = { ...draft, estimate: { id: "e1", estimateNumber: "EST-2026-000001" } };
    vi.mocked(invoiceService.get).mockResolvedValue(converted);
    vi.mocked(invoiceService.update).mockResolvedValue(converted);
    vi.mocked(invoiceService.preview).mockResolvedValue({
      ...preview(1),
      comparison: { estimateId: "e1", estimateNumber: "EST-2026-000001", estimateIgst: false, estimateTaxable: "1000.00", estimateGst: "180.01", estimateGrandTotal: "1180.01", taxableDifference: "0.00", gstDifference: "-0.01", grandTotalDifference: "-0.01", taxTypeChanged: false },
    });
    renderApp("/invoices/i1/edit");
    expect(await screen.findByText("From estimate EST-2026-000001")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Add item/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: /Lead/ })).not.toBeInTheDocument();
    expect(await screen.findByText("Differs from estimate EST-2026-000001")).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText(/Billing name/));
    await userEvent.type(screen.getByLabelText(/Billing name/), "Rao Family Trust");
    await userEvent.click(screen.getByRole("button", { name: "Save draft" }));
    await vi.waitFor(() => expect(invoiceService.update).toHaveBeenCalled());
    expect(vi.mocked(invoiceService.update).mock.calls[0]).toMatchObject([
      "i1",
      { leadId: null, billTo: null, discountPercent: null, feePercent: null, lines: null, billingName: "Rao Family Trust", version: 2 },
    ]);
  });

  it("refuses to edit an issued invoice", async () => {
    signIn(writer());
    vi.mocked(invoiceService.get).mockResolvedValue(issued);
    renderApp("/invoices/i1/edit");
    expect(await screen.findByText("This invoice is issued")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Save draft" })).not.toBeInTheDocument();
  });
});

describe("estimate conversion", () => {
  const sent = {
    id: "e1",
    estimateNumber: "EST-2026-000001",
    estimateDate: "2026-10-20",
    validUntil: "2026-11-04",
    status: "SENT",
    statusLabel: "Sent",
    expired: false,
    sentOn: "2026-10-21",
    lead: draft.lead,
    partner: draft.partner,
    billTo: "CLIENT",
    billToLabel: "Client",
    discountPercent: "0",
    igst: false,
    feePercent: "8",
    notes: null,
    lines: [],
    totals: { subtotal: "0", discountAmount: "0", taxableAmount: "0", gstAmount: "0", cgstAmount: "0", sgstAmount: "0", grandTotal: "0" },
    createdAt: "2026-10-20T00:00:00Z",
    createdByName: null,
    updatedAt: "2026-10-20T00:00:00Z",
    version: 1,
  } as unknown as Estimate;

  beforeEach(() => {
    vi.mocked(estimateService.get).mockResolvedValue(sent);
    vi.mocked(estimateService.history).mockResolvedValue([]);
    vi.mocked(estimateService.document).mockResolvedValue({ text: "<html></html>", filename: "e.html" });
  });

  it("converts a sent estimate into a draft invoice", async () => {
    signIn(manager());
    vi.mocked(invoiceService.convert).mockResolvedValue({ ...draft, estimate: { id: "e1", estimateNumber: "EST-2026-000001" } });
    renderApp("/estimates/e1");
    await userEvent.click(await screen.findByRole("button", { name: /Convert to invoice/ }));
    const dialog = await screen.findByRole("dialog", { name: "Convert EST-2026-000001 to an invoice?" });
    await userEvent.click(within(dialog).getByRole("button", { name: "Create draft invoice" }));
    await vi.waitFor(() => expect(invoiceService.convert).toHaveBeenCalledWith("e1"));
    expect(await screen.findByRole("heading", { name: "Draft invoice" })).toBeInTheDocument();
  });

  it("offers a new conversion for an invoiced estimate and shows the server's refusal", async () => {
    signIn(manager());
    vi.mocked(estimateService.get).mockResolvedValue({ ...sent, status: "INVOICED", statusLabel: "Invoiced" } as Estimate);
    const { ApiRequestError } = await import("@/services/api");
    vi.mocked(invoiceService.convert).mockRejectedValue(
      new ApiRequestError(409, "ESTIMATE_ALREADY_INVOICED", "This estimate already has an invoice."),
    );
    renderApp("/estimates/e1");
    await userEvent.click(await screen.findByRole("button", { name: /Convert to invoice/ }));
    const dialog = await screen.findByRole("dialog", { name: "Convert EST-2026-000001 to an invoice?" });
    expect(within(dialog).getByText(/only if its earlier invoice was cancelled/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Create draft invoice" }));
    await vi.waitFor(() => expect(invoiceService.convert).toHaveBeenCalledWith("e1"));
    expect(screen.getByRole("heading", { name: "EST-2026-000001" })).toBeInTheDocument();
  });

  it("offers no conversion without Invoices write or for drafts", async () => {
    signIn(makeMe("ADMIN", { modules: [row("DASHBOARD"), row("ESTIMATES", { write: true, dataScope: "ALL" }), row("INVOICES", { dataScope: "ALL" })] }));
    renderApp("/estimates/e1");
    expect(await screen.findByRole("heading", { name: "EST-2026-000001" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Convert to invoice/ })).not.toBeInTheDocument();
  });
});

describe("GST registration settings", () => {
  const settingsWriter = () => makeMe("ADMIN", { modules: [row("DASHBOARD"), row("SETTINGS", { write: true })] });

  it("checks the GSTIN against the registered state and saves with the version", async () => {
    signIn(settingsWriter());
    vi.mocked(settingsService.updateGstRegistration).mockResolvedValue({ ...registration, complete: true });
    renderApp("/settings");
    expect(await screen.findByText("Incomplete")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    await userEvent.type(screen.getByLabelText(/Address/), "Plot 7, Hyderabad");
    await userEvent.selectOptions(screen.getByLabelText(/Registered state/), "29");
    await userEvent.type(screen.getByLabelText(/GSTIN/), "36aabci1234a1zc");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(await screen.findByText("The GSTIN's first two digits must match the registered state.")).toBeInTheDocument();
    expect(settingsService.updateGstRegistration).not.toHaveBeenCalled();
    await userEvent.selectOptions(screen.getByLabelText(/Registered state/), "36");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));
    await vi.waitFor(() =>
      expect(settingsService.updateGstRegistration).toHaveBeenCalledWith({ legalName: "IOTIQ", address: "Plot 7, Hyderabad", stateCode: "36", gstin: "36AABCI1234A1ZC", version: 4 }),
    );
  });

  it("is read-only without Settings write", async () => {
    signIn(makeMe("ADMIN", { modules: [row("DASHBOARD"), row("SETTINGS")] }));
    renderApp("/settings");
    expect(await screen.findByText("Legal name")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
  });
});
