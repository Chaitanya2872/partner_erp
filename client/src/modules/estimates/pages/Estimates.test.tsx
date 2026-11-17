import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderApp } from "@/test/renderApp";
import { catalog, makeMe, row } from "@/test/fixtures";
import { authService } from "@/services/auth.service";
import { leadService } from "@/modules/leads/services/leadService";
import type { Lead, LeadSummary } from "@/modules/leads/types/lead.types";
import { ApiRequestError } from "@/services/api";
import { estimateService } from "../services/estimateService";
import type { Estimate, EstimateLeadOption, EstimateMeta, EstimatePreview, EstimateSummary, ProductOption, Totals } from "../types/estimate.types";

vi.mock("@/services/auth.service", () => ({
  authService: { restore: vi.fn(), me: vi.fn(), modules: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() },
}));
vi.mock("@/modules/leads/services/leadService", () => ({
  leadService: { list: vi.fn(), meta: vi.fn(), get: vi.fn(), history: vi.fn(), create: vi.fn(), update: vi.fn(), setStage: vi.fn(), acknowledge: vi.fn(), remove: vi.fn() },
}));
vi.mock("../services/estimateService", () => ({
  estimateService: {
    list: vi.fn(),
    meta: vi.fn(),
    get: vi.fn(),
    history: vi.fn(),
    preview: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    send: vi.fn(),
    accept: vi.fn(),
    reject: vi.fn(),
    remove: vi.fn(),
    archive: vi.fn(),
    archived: vi.fn(),
    restore: vi.fn(),
    exportCsv: vi.fn(),
    document: vi.fn(),
    internal: vi.fn(),
    internalDocument: vi.fn(),
    leadOptions: vi.fn(),
    productOptions: vi.fn(),
  },
}));

const meta: EstimateMeta = {
  statuses: [
    { value: "DRAFT", label: "Draft" },
    { value: "SENT", label: "Sent" },
    { value: "ACCEPTED", label: "Accepted" },
    { value: "REJECTED", label: "Rejected" },
    { value: "INVOICED", label: "Invoiced" },
  ],
  billTo: [
    { value: "CLIENT", label: "Client" },
    { value: "PARTNER", label: "Partner (bulk purchase)" },
  ],
  validityDays: 15,
  terms: "Prices exclude civil work.",
  defaultGstPercent: "18",
  maxLines: 100,
  canOverrideFee: false,
};

const totals: Totals = {
  subtotal: "2000.00",
  discountAmount: "0.00",
  taxableAmount: "2000.00",
  gstAmount: "360.00",
  cgstAmount: "180.00",
  sgstAmount: "180.00",
  grandTotal: "2360.00",
};

const summary: EstimateSummary = {
  id: "e1",
  estimateNumber: "EST-2026-000001",
  estimateDate: "2026-10-20",
  validUntil: "2026-11-04",
  status: "DRAFT",
  statusLabel: "Draft",
  expired: false,
  leadId: "l1",
  leadNumber: "LEAD-2026-000001",
  clientName: "Rao Residence",
  partner: { id: "p1", name: "Alpha Interiors" },
  billTo: "CLIENT",
  taxableAmount: "2000.00",
  grandTotal: "2360.00",
  createdAt: "2026-10-20T00:00:00Z",
  version: 0,
};

const estimate: Estimate = {
  id: "e1",
  estimateNumber: "EST-2026-000001",
  estimateDate: "2026-10-20",
  validUntil: "2026-11-04",
  status: "DRAFT",
  statusLabel: "Draft",
  expired: false,
  sentOn: null,
  lead: { id: "l1", leadNumber: "LEAD-2026-000001", clientName: "Rao Residence", clientPhone: null, city: "Hyderabad", projectTypeLabel: "Villa", stage: "NEW", stageLabel: "New", deleted: false },
  partner: { id: "p1", name: "Alpha Interiors" },
  billTo: "CLIENT",
  billToLabel: "Client",
  discountPercent: "0",
  igst: false,
  feePercent: "8",
  notes: "Prices exclude civil work.",
  lines: [
    { id: "li1", lineNo: 1, kind: "PRODUCT", productId: "pr1", sku: "SG-10F", productActive: true, description: "Smart Glide", hsn: "8536", unit: "nos", quantity: "2", unitPrice: "1000", gstPercent: "18", amount: "2000.00" },
  ],
  totals,
  createdAt: "2026-10-20T00:00:00Z",
  createdByName: "Test Admin",
  updatedAt: "2026-10-20T00:00:00Z",
  version: 3,
};

const leadSummary = {
  id: "l1",
  leadNumber: "LEAD-2026-000001",
  clientName: "Rao Residence",
  stage: "NEW",
  partner: { id: "p1", name: "Alpha Interiors" },
} as LeadSummary;

const leadOption: EstimateLeadOption = { id: "l1", leadNumber: "LEAD-2026-000001", clientName: "Rao Residence", partner: { id: "p1", name: "Alpha Interiors" } };

/** Estimates write without Leads read: leads come from the restricted picker. */
const estimatesOnly = () => makeMe("ADMIN", { modules: [row("DASHBOARD"), row("ESTIMATES", { write: true, dataScope: "ALL" })] });

const product: ProductOption = { id: "pr1", sku: "SG-10F", category: "SWITCHES", categoryLabel: "Switches", name: "Smart Glide", hsn: "8536", unit: "nos", rate: "1000", gstPercent: "18" };

const preview = (lines: number): EstimatePreview => ({
  lines: Array.from({ length: lines }, (_, i) => ({ lineNo: i + 1, id: null, kind: "PRODUCT" as const, productId: "pr1", sku: "SG-10F", description: "Smart Glide", hsn: "8536", unit: "nos", quantity: "1", unitPrice: "1000", gstPercent: "18", amount: "1000.00" })),
  totals: { ...totals, subtotal: "1000.00", taxableAmount: "1000.00", gstAmount: "180.00", cgstAmount: "90.00", sgstAmount: "90.00", grandTotal: "1180.00" },
  defaultFeePercent: "8",
  feePercent: "8",
  feeOverride: false,
});

/** ESTIMATES read + write; no export, delete, internal copy or B2C prices. */
const writer = () => makeMe("ADMIN");
const reader = () => makeMe("ADMIN", { modules: [row("DASHBOARD"), row("ESTIMATES", { dataScope: "ALL" })] });
const internalUser = () =>
  makeMe(
    "ADMIN",
    { modules: [row("DASHBOARD"), row("LEADS", { dataScope: "ALL" }), row("PRODUCTS"), row("ESTIMATES", { write: true, delete: true, export: true, dataScope: "ALL" }), row("INTERNAL_B2C")] },
    ["PRODUCTS:VIEW_INTERNAL_B2C"],
  );

const signIn = (me: ReturnType<typeof makeMe>) => {
  vi.mocked(authService.restore).mockResolvedValue(me);
  vi.mocked(authService.me).mockResolvedValue(me);
};

beforeEach(() => {
  vi.mocked(authService.modules).mockResolvedValue(catalog);
  vi.mocked(estimateService.meta).mockResolvedValue(meta);
  vi.mocked(estimateService.list).mockResolvedValue({ content: [summary, { ...summary, id: "e2", estimateNumber: "EST-2026-000002", status: "SENT", statusLabel: "Sent", expired: true }], meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 } });
  vi.mocked(estimateService.get).mockResolvedValue(estimate);
  vi.mocked(estimateService.history).mockResolvedValue([
    { id: "h1", at: "2026-10-20T00:00:00Z", actorName: "Test Admin", action: "ESTIMATE_CREATED", changes: [{ field: "estimateNumber", from: null, to: "EST-2026-000001" }, { field: "status", from: null, to: "DRAFT" }] },
  ]);
  vi.mocked(estimateService.document).mockResolvedValue({ text: "<html><body>Partner copy EST-2026-000001</body></html>", filename: "EST-2026-000001.html" });
  vi.mocked(estimateService.internalDocument).mockResolvedValue({ text: "<html><body>INTERNAL ONLY</body></html>", filename: "INTERNAL-INT-EST-2026-000001.html" });
  vi.mocked(estimateService.productOptions).mockResolvedValue([product]);
  vi.mocked(estimateService.leadOptions).mockImplementation(async (params) => (params.leadId && params.leadId !== "l1" ? [] : [leadOption]));
  vi.mocked(estimateService.preview).mockImplementation(async (input) => preview(input.lines.length));
  vi.mocked(leadService.list).mockResolvedValue({ content: [leadSummary, { ...leadSummary, id: "l9", leadNumber: "LEAD-2026-000009", clientName: "Lost Client", stage: "LOST" }], meta: { page: 0, size: 100, totalElements: 2, totalPages: 1 } });
  vi.mocked(leadService.get).mockResolvedValue({ ...leadSummary, stage: "NEW" } as unknown as Lead);
});

describe("estimate list", () => {
  it("shows the register with the derived Expired status and no export without the permission", async () => {
    signIn(writer());
    renderApp("/estimates");
    const first = (await screen.findByRole("link", { name: "EST-2026-000001" })).closest("tr")!;
    expect(within(first).getByText("Rao Residence")).toBeInTheDocument();
    expect(within(first).getByText("Draft")).toBeInTheDocument();
    expect(within(first).getByText("₹2,360.00")).toBeInTheDocument();
    const second = screen.getByRole("link", { name: "EST-2026-000002" }).closest("tr")!;
    expect(within(second).getByText("Expired")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Export CSV/ })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /New estimate/ })).toBeInTheDocument();
  });

  it("sends the status filter to the server", async () => {
    signIn(writer());
    renderApp("/estimates");
    await screen.findByRole("link", { name: "EST-2026-000001" });
    await userEvent.selectOptions(screen.getByLabelText("Filter by status"), "SENT");
    await vi.waitFor(() => expect(vi.mocked(estimateService.list).mock.lastCall?.[0]).toMatchObject({ status: "SENT", page: 0 }));
  });

  it("offers no create or edit to readers", async () => {
    signIn(reader());
    renderApp("/estimates");
    await screen.findByRole("link", { name: "EST-2026-000001" });
    expect(screen.queryByRole("link", { name: /New estimate/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Edit EST/ })).not.toBeInTheDocument();
  });

  describe("archive tab", () => {
    const archivedEntry = { estimate: { ...summary, id: "e5", estimateNumber: "EST-2026-000005" }, deletedAt: "2026-10-25T10:00:00Z", deletedByName: "Test Admin", deleteReason: "Raised against the wrong partner" };

    beforeEach(() => {
      vi.mocked(estimateService.archive).mockResolvedValue({ content: [archivedEntry], meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 } });
      vi.mocked(estimateService.archived).mockResolvedValue({ estimate: { ...estimate, id: "e5", estimateNumber: "EST-2026-000005" }, deletedAt: archivedEntry.deletedAt, deletedByName: "Test Admin", deleteReason: archivedEntry.deleteReason });
    });

    it("lists archived estimates with who, when and why, and opens a read-only view", async () => {
      signIn(writer());
      renderApp("/estimates");
      await userEvent.click(await screen.findByRole("tab", { name: "Archive" }));
      const row5 = (await screen.findByText("EST-2026-000005")).closest("tr")!;
      expect(within(row5).getByText("Test Admin")).toBeInTheDocument();
      expect(within(row5).getByText("Raised against the wrong partner")).toBeInTheDocument();
      expect(screen.getByRole("columnheader", { name: /Deleted on/ })).toBeInTheDocument();
      expect(screen.getByRole("columnheader", { name: /Deleted by/ })).toBeInTheDocument();
      expect(screen.getByRole("columnheader", { name: /Reason/ })).toBeInTheDocument();
      await userEvent.click(within(row5).getByRole("button", { name: "View archived EST-2026-000005" }));
      const dialog = await screen.findByRole("dialog", { name: "Archived estimate: EST-2026-000005" });
      expect(within(dialog).getByText(/Reason: Raised against the wrong partner/)).toBeInTheDocument();
      expect(within(dialog).getByText("Smart Glide")).toBeInTheDocument();
      expect(within(dialog).getByText(/Read-only/)).toBeInTheDocument();
      expect(estimateService.archived).toHaveBeenCalledWith("e5", expect.anything());
    });

    it("restores after a confirmation", async () => {
      signIn(makeMe("ADMIN", { modules: [row("DASHBOARD"), row("ESTIMATES", { write: true, approve: true, dataScope: "ALL" })] }));
      vi.mocked(estimateService.restore).mockResolvedValue({ ...estimate, id: "e5", estimateNumber: "EST-2026-000005" });
      renderApp("/estimates");
      await userEvent.click(await screen.findByRole("tab", { name: "Archive" }));
      await userEvent.click(await screen.findByRole("button", { name: "Restore EST-2026-000005" }));
      const dialog = await screen.findByRole("dialog", { name: "Restore estimate?" });
      expect(estimateService.restore).not.toHaveBeenCalled();
      await userEvent.click(within(dialog).getByRole("button", { name: "Restore estimate" }));
      await vi.waitFor(() => expect(estimateService.restore).toHaveBeenCalledWith("e5"));
    });

    it("offers no restore without the Estimates approve or delete permission", async () => {
      signIn(writer());
      renderApp("/estimates");
      await userEvent.click(await screen.findByRole("tab", { name: "Archive" }));
      await screen.findByText("EST-2026-000005");
      expect(screen.queryByRole("button", { name: /Restore EST/ })).not.toBeInTheDocument();
    });
  });
});

describe("estimate details", () => {
  it("shows only the partner copy without Internal B2C read", async () => {
    signIn(writer());
    renderApp("/estimates/e1");
    expect(await screen.findByRole("heading", { name: "EST-2026-000001" })).toBeInTheDocument();
    expect(await screen.findByTitle("Partner copy")).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "Internal B2C copy" })).not.toBeInTheDocument();
    expect(estimateService.internalDocument).not.toHaveBeenCalled();
    expect(screen.queryByRole("button", { name: /Archive/ })).not.toBeInTheDocument();
  });

  it("opens the internal copy for Internal B2C readers", async () => {
    signIn(internalUser());
    renderApp("/estimates/e1");
    await userEvent.click(await screen.findByRole("tab", { name: "Internal B2C copy" }));
    expect(await screen.findByTitle("Internal B2C copy")).toBeInTheDocument();
    expect(estimateService.internalDocument).toHaveBeenCalledWith("e1");
    expect(screen.getByText(/Never send this copy/)).toBeInTheDocument();
  });

  it("confirms Mark sent and sends the version", async () => {
    signIn(writer());
    vi.mocked(estimateService.send).mockResolvedValue({ ...estimate, status: "SENT", statusLabel: "Sent", version: 4 });
    renderApp("/estimates/e1");
    await userEvent.click(await screen.findByRole("button", { name: /Mark sent/ }));
    const dialog = await screen.findByRole("dialog", { name: "Mark this estimate as sent?" });
    expect(within(dialog).getByText(/Estimate sent/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Mark sent" }));
    await vi.waitFor(() => expect(estimateService.send).toHaveBeenCalledWith("e1", 3));
  });

  it("warns before archiving a sent estimate and will not archive without a reason", async () => {
    signIn(internalUser());
    vi.mocked(estimateService.get).mockResolvedValue({ ...estimate, status: "SENT", statusLabel: "sent" });
    vi.mocked(estimateService.remove).mockResolvedValue();
    renderApp("/estimates/e1");
    await userEvent.click(await screen.findByRole("button", { name: /Archive/ }));
    const dialog = await screen.findByRole("dialog", { name: "Archive EST-2026-000001?" });
    expect(within(dialog).getByText("This estimate is sent")).toBeInTheDocument();
    // No reason: refused on the spot, nothing sent.
    await userEvent.click(within(dialog).getByRole("button", { name: "Archive estimate" }));
    expect(await within(dialog).findByText("Say why it is being archived (at least 10 characters).")).toBeInTheDocument();
    expect(estimateService.remove).not.toHaveBeenCalled();
    // Too short is refused as well.
    await userEvent.type(within(dialog).getByLabelText(/Reason for archiving/), "short");
    await userEvent.click(within(dialog).getByRole("button", { name: "Archive estimate" }));
    expect(estimateService.remove).not.toHaveBeenCalled();
    await userEvent.type(within(dialog).getByLabelText(/Reason for archiving/), "  but now long enough  ");
    await userEvent.click(within(dialog).getByRole("button", { name: "Archive estimate" }));
    await vi.waitFor(() => expect(estimateService.remove).toHaveBeenCalledWith("e1", "short  but now long enough"));
  });

  it("shows the server's refusal of the archive reason under the reason box", async () => {
    signIn(internalUser());
    vi.mocked(estimateService.remove).mockRejectedValueOnce(
      new ApiRequestError(400, "VALIDATION_FAILED", "Validation failed", [{ field: "reason", message: "Give a reason of 10 to 500 characters." }]),
    );
    renderApp("/estimates/e1");
    await userEvent.click(await screen.findByRole("button", { name: /Archive/ }));
    const dialog = await screen.findByRole("dialog", { name: "Archive EST-2026-000001?" });
    await userEvent.type(within(dialog).getByLabelText(/Reason for archiving/), "Duplicate of another estimate");
    await userEvent.click(within(dialog).getByRole("button", { name: "Archive estimate" }));
    expect(await within(dialog).findByText("Give a reason of 10 to 500 characters.")).toBeInTheDocument();
  });

  it("shows the revision reason prominently in the history timeline", async () => {
    signIn(writer());
    vi.mocked(estimateService.history).mockResolvedValue([
      {
        id: "h2",
        at: "2026-10-21T00:00:00Z",
        actorName: "Test Admin",
        action: "ESTIMATE_UPDATED",
        changes: [
          { field: "revisionReason", from: null, to: "Client asked for installation to be added" },
          { field: "discountPercent", from: "0", to: "5" },
        ],
      },
    ]);
    renderApp("/estimates/e1");
    expect(await screen.findByText("Reason for revision:")).toBeInTheDocument();
    expect(screen.getByText("Client asked for installation to be added")).toBeInTheDocument();
  });

  it("shows who archived an estimate and why in the history", async () => {
    signIn(writer());
    vi.mocked(estimateService.history).mockResolvedValue([
      { id: "h3", at: "2026-10-22T00:00:00Z", actorName: "Test Admin", action: "ESTIMATE_DELETED", changes: [{ field: "reason", from: null, to: "Raised against the wrong partner" }] },
    ]);
    renderApp("/estimates/e1");
    expect(await screen.findByText("Reason for archiving:")).toBeInTheDocument();
    expect(screen.getByText("Raised against the wrong partner")).toBeInTheDocument();
  });

  it("locks invoiced estimates", async () => {
    signIn(internalUser());
    vi.mocked(estimateService.get).mockResolvedValue({ ...estimate, status: "INVOICED", statusLabel: "Invoiced" });
    renderApp("/estimates/e1");
    expect(await screen.findByRole("heading", { name: "EST-2026-000001" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Edit/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Archive/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Mark/ })).not.toBeInTheDocument();
  });
});

describe("estimate form", () => {
  it("requires at least one item before sending anything", async () => {
    signIn(writer());
    renderApp("/estimates/new?leadId=l1");
    await userEvent.click(await screen.findByRole("button", { name: "Create estimate" }));
    expect(await screen.findAllByText("Add at least one item.")).not.toHaveLength(0);
    expect(estimateService.create).not.toHaveBeenCalled();
  });

  it("picks leads through the restricted picker without Leads read", async () => {
    signIn(estimatesOnly());
    renderApp("/estimates/new");
    await userEvent.click(await screen.findByRole("combobox", { name: /Lead/ }));
    await userEvent.type(screen.getByRole("searchbox", { name: "Search leads" }), "Rao");
    await vi.waitFor(() => expect(estimateService.leadOptions).toHaveBeenCalledWith({ q: "Rao" }, expect.anything()));
    await userEvent.click(await screen.findByRole("option", { name: /Rao Residence/ }));
    expect(screen.getByText("Referred by Alpha Interiors.")).toBeInTheDocument();
    expect(leadService.list).not.toHaveBeenCalled();
    expect(leadService.get).not.toHaveBeenCalled();
  });

  it("explains when the linked lead can't get an estimate", async () => {
    signIn(writer());
    renderApp("/estimates/new?leadId=l9");
    expect(await screen.findByText("This lead can't get a new estimate")).toBeInTheDocument();
  });

  it("never assumes the tax type of a new estimate", async () => {
    signIn(writer());
    renderApp("/estimates/new?leadId=l1");
    await userEvent.click(await screen.findByRole("button", { name: /Add item/ }));
    await userEvent.click(await screen.findByRole("button", { name: "Add Smart Glide" }));
    expect(screen.getByLabelText(/^Tax/)).toHaveValue("");
    await userEvent.click(screen.getByRole("button", { name: "Create estimate" }));
    expect(await screen.findAllByText("Choose CGST + SGST (same state) or IGST (other state).")).not.toHaveLength(0);
    expect(estimateService.create).not.toHaveBeenCalled();
  });

  it("asks before saving changes to a sent estimate and keeps its status", async () => {
    signIn(writer());
    vi.mocked(estimateService.get).mockResolvedValue({ ...estimate, status: "SENT", statusLabel: "sent" });
    vi.mocked(estimateService.update).mockResolvedValue({ ...estimate, status: "SENT", statusLabel: "sent", version: 4 });
    renderApp("/estimates/e1/edit");
    expect(await screen.findByText("This estimate is sent")).toBeInTheDocument();
    const qty = await screen.findByLabelText("Quantity for SG-10F Smart Glide");
    await userEvent.clear(qty);
    await userEvent.type(qty, "5");
    await userEvent.type(screen.getByLabelText(/Reason for revision/), "Client asked for more units");
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));
    const dialog = await screen.findByRole("dialog", { name: "Save changes to a sent estimate?" });
    expect(estimateService.update).not.toHaveBeenCalled();
    await userEvent.click(within(dialog).getByRole("button", { name: "Save changes" }));
    await vi.waitFor(() => expect(estimateService.update).toHaveBeenCalled());
    expect(vi.mocked(estimateService.update).mock.calls[0]).toMatchObject([
      "e1",
      { version: 3, igst: false, revisionReason: "Client asked for more units", lines: [{ id: "li1", quantity: "5" }] },
    ]);
  });

  it("needs a reason of at least 10 characters to revise a sent estimate, and maps the server's refusal onto it", async () => {
    signIn(writer());
    vi.mocked(estimateService.get).mockResolvedValue({ ...estimate, status: "SENT", statusLabel: "sent" });
    vi.mocked(estimateService.update).mockRejectedValueOnce(
      new ApiRequestError(400, "VALIDATION_FAILED", "Validation failed", [{ field: "revisionReason", message: "Say why this estimate is being revised (at least 10 characters)." }]),
    );
    renderApp("/estimates/e1/edit");
    const reason = await screen.findByLabelText(/Reason for revision/);
    expect(screen.getByText("Kept in the estimate's history and shown to your team.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));
    expect(await screen.findAllByText("Say why this estimate is being revised (at least 10 characters).")).not.toHaveLength(0);
    await userEvent.type(reason, "too short");
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));
    expect(await screen.findAllByText("Say why this estimate is being revised (at least 10 characters).")).not.toHaveLength(0);
    expect(screen.queryByRole("dialog", { name: "Save changes to a sent estimate?" })).not.toBeInTheDocument();
    expect(estimateService.update).not.toHaveBeenCalled();

    await userEvent.type(reason, " enough");
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));
    await userEvent.click(within(await screen.findByRole("dialog", { name: "Save changes to a sent estimate?" })).getByRole("button", { name: "Save changes" }));
    await vi.waitFor(() => expect(estimateService.update).toHaveBeenCalledTimes(1));
    expect(vi.mocked(estimateService.update).mock.calls[0][1]).toMatchObject({ revisionReason: "too short enough" });
    // The server's field error lands on the reason field.
    expect(await screen.findAllByText("Say why this estimate is being revised (at least 10 characters).")).not.toHaveLength(0);
  });

  it("asks for no revision reason while the estimate is a draft", async () => {
    signIn(writer());
    vi.mocked(estimateService.update).mockResolvedValue(estimate);
    renderApp("/estimates/e1/edit");
    const qty = await screen.findByLabelText("Quantity for SG-10F Smart Glide");
    expect(screen.queryByLabelText(/Reason for revision/)).not.toBeInTheDocument();
    await userEvent.clear(qty);
    await userEvent.type(qty, "3");
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));
    await vi.waitFor(() => expect(estimateService.update).toHaveBeenCalled());
    expect(vi.mocked(estimateService.update).mock.calls[0][1]).toMatchObject({ revisionReason: "" });
  });

  it("adds an optional installation line, includes it in the preview and the payload, and shows its subtotal", async () => {
    signIn(writer());
    vi.mocked(estimateService.create).mockResolvedValue(estimate);
    vi.mocked(estimateService.preview).mockImplementation(async (input) => ({
      ...preview(input.lines.length),
      lines: input.lines.map((l, i) => ({
        lineNo: i + 1,
        id: null,
        kind: l.kind,
        productId: l.productId ?? null,
        sku: null,
        description: l.description ?? "Smart Glide",
        hsn: null,
        unit: l.unit ?? "nos",
        quantity: l.quantity,
        unitPrice: l.unitPrice ?? "1000",
        gstPercent: l.gstPercent ?? "18",
        amount: l.kind === "INSTALLATION" ? "1000.00" : "1000.00",
      })),
      totals: { ...totals, subtotal: "2000.00", taxableAmount: "2000.00", gstAmount: "300.00", cgstAmount: "150.00", sgstAmount: "150.00", grandTotal: "2300.00" },
    }));
    renderApp("/estimates/new?leadId=l1");
    await userEvent.click(await screen.findByRole("button", { name: /Add item/ }));
    await userEvent.click(await screen.findByRole("button", { name: "Add Smart Glide" }));
    expect(screen.getByText(/No installation lines/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Add installation line" }));
    // A blank installation line is refused before anything is sent.
    await userEvent.selectOptions(screen.getByLabelText(/^Tax/), "false");
    await userEvent.click(screen.getByRole("button", { name: "Create estimate" }));
    expect(await screen.findAllByText(/Describe the installation work./)).not.toHaveLength(0);
    expect(estimateService.create).not.toHaveBeenCalled();

    await userEvent.type(screen.getByLabelText("Description of installation line 1"), "Fit rails and commission");
    expect(screen.getByLabelText("Unit of installation line 1")).toHaveValue("job");
    expect(screen.getByLabelText("GST of installation line 1")).toHaveValue("18");
    const qty = screen.getByLabelText("Quantity of installation line 1");
    await userEvent.clear(qty);
    await userEvent.type(qty, "2");
    await userEvent.type(screen.getByLabelText("Rate of installation line 1"), "500");
    expect(await screen.findByText("Installation subtotal")).toBeInTheDocument();
    expect(await screen.findByText("₹2,300.00")).toBeInTheDocument();
    await vi.waitFor(() => expect(vi.mocked(estimateService.preview).mock.lastCall?.[0].lines).toHaveLength(2));
    await userEvent.click(screen.getByRole("button", { name: "Create estimate" }));
    await vi.waitFor(() => expect(estimateService.create).toHaveBeenCalled());
    expect(vi.mocked(estimateService.create).mock.calls[0][0].lines).toMatchObject([
      { kind: "PRODUCT", productId: "pr1", quantity: "1" },
      { kind: "INSTALLATION", description: "Fit rails and commission", unit: "job", quantity: "2", unitPrice: "500", gstPercent: "18" },
    ]);
    expect(vi.mocked(estimateService.create).mock.calls[0][0].lines[1]).not.toHaveProperty("productId");
  });

  it("shows a saved installation line and sends it back unchanged with its id", async () => {
    signIn(writer());
    const withInstall: Estimate = {
      ...estimate,
      lines: [
        ...estimate.lines,
        { id: "li2", lineNo: 2, kind: "INSTALLATION", productId: null, sku: null, productActive: true, description: "Wiring", hsn: null, unit: "job", quantity: "1", unitPrice: "800", gstPercent: "18", amount: "800.00" },
      ],
    };
    vi.mocked(estimateService.get).mockResolvedValue(withInstall);
    vi.mocked(estimateService.update).mockResolvedValue(withInstall);
    renderApp("/estimates/e1/edit");
    const description = await screen.findByLabelText("Description of installation line 1");
    expect(description).toHaveValue("Wiring");
    await userEvent.clear(description);
    await userEvent.type(description, "Wiring and testing");
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));
    await vi.waitFor(() => expect(estimateService.update).toHaveBeenCalled());
    expect(vi.mocked(estimateService.update).mock.calls[0][1].lines).toMatchObject([
      { id: "li1", kind: "PRODUCT", productId: "pr1" },
      { id: "li2", kind: "INSTALLATION", description: "Wiring and testing", unitPrice: "800" },
    ]);
  });

  it("adds a product, shows server totals and creates the estimate", async () => {
    signIn(writer());
    vi.mocked(estimateService.create).mockResolvedValue(estimate);
    renderApp("/estimates/new?leadId=l1");
    await userEvent.click(await screen.findByRole("button", { name: /Add item/ }));
    await userEvent.click(await screen.findByRole("button", { name: "Add Smart Glide" }));
    expect(await screen.findByText("₹1,180.00")).toBeInTheDocument();
    // Without Partners approve the fee is read-only, and no B2C comparison is shown.
    expect(screen.getByLabelText("Fee rate")).toHaveAttribute("readonly");
    expect(screen.queryByText("Internal B2C comparison")).not.toBeInTheDocument();
    expect(screen.getByLabelText(/Notes and terms/)).toHaveValue("Prices exclude civil work.");
    await userEvent.selectOptions(screen.getByLabelText(/^Tax/), "false");
    await userEvent.click(screen.getByRole("button", { name: "Create estimate" }));
    await vi.waitFor(() => expect(estimateService.create).toHaveBeenCalled());
    expect(vi.mocked(estimateService.create).mock.calls[0][0]).toMatchObject({
      leadId: "l1",
      billTo: "CLIENT",
      igst: false,
      feePercent: null,
      lines: [{ productId: "pr1", quantity: "1" }],
    });
    // Rate and GST of a product line are the product sheet's: the form never sends them.
    const sent = vi.mocked(estimateService.create).mock.calls[0][0].lines[0];
    expect(sent).not.toHaveProperty("unitPrice");
    expect(sent).not.toHaveProperty("gstPercent");
    expect(await screen.findByRole("heading", { name: "EST-2026-000001" })).toBeInTheDocument();
  });

  it("shows a product line's rate and GST as plain text, not as inputs", async () => {
    signIn(writer());
    renderApp("/estimates/e1/edit");
    await screen.findByLabelText("Quantity for SG-10F Smart Glide");
    expect(screen.queryByLabelText(/^Rate for SG-10F/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/^GST for SG-10F/)).not.toBeInTheDocument();
    const row = screen.getByLabelText("Quantity for SG-10F Smart Glide").closest("tr")!;
    expect(within(row).getAllByTitle("From the product sheet")).toHaveLength(2);
    expect(within(row).getByText("₹1,000.00")).toBeInTheDocument();
    expect(within(row).getByText(/^18(\.0+)?%$/)).toBeInTheDocument();
    // Only quantity is an editable number on the product row.
    expect(within(row).getAllByRole("textbox")).toHaveLength(1);
  });

  it.each(["SENT", "ACCEPTED", "REJECTED"] as const)("needs a revision reason for an installation-only change on a %s estimate", async (status) => {
    signIn(writer());
    const withInstall: Estimate = {
      ...estimate,
      status,
      statusLabel: status.toLowerCase(),
      lines: [
        ...estimate.lines,
        { id: "li2", lineNo: 2, kind: "INSTALLATION", productId: null, sku: null, productActive: true, description: "Wiring", hsn: null, unit: "job", quantity: "1", unitPrice: "800", gstPercent: "18", amount: "800.00" },
      ],
    };
    vi.mocked(estimateService.get).mockResolvedValue(withInstall);
    vi.mocked(estimateService.update).mockResolvedValue(withInstall);
    renderApp("/estimates/e1/edit");
    const rate = await screen.findByLabelText("Rate of installation line 1");
    await userEvent.clear(rate);
    await userEvent.type(rate, "900");
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));
    expect(await screen.findAllByText("Say why this estimate is being revised (at least 10 characters).")).not.toHaveLength(0);
    expect(estimateService.update).not.toHaveBeenCalled();
    await userEvent.type(screen.getByLabelText(/Reason for revision/), "Installation re-quoted after site visit");
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));
    await userEvent.click(within(await screen.findByRole("dialog", { name: `Save changes to a ${status.toLowerCase()} estimate?` })).getByRole("button", { name: "Save changes" }));
    await vi.waitFor(() => expect(estimateService.update).toHaveBeenCalledTimes(1));
    expect(vi.mocked(estimateService.update).mock.calls[0][1]).toMatchObject({
      revisionReason: "Installation re-quoted after site visit",
      lines: [{ id: "li1" }, { id: "li2", kind: "INSTALLATION", unitPrice: "900" }],
    });
  });

  it("needs a revision reason for a discount-only or notes-only change on a sent estimate", async () => {
    signIn(writer());
    vi.mocked(estimateService.get).mockResolvedValue({ ...estimate, status: "SENT", statusLabel: "sent" });
    vi.mocked(estimateService.update).mockResolvedValue({ ...estimate, status: "SENT", statusLabel: "sent" });
    renderApp("/estimates/e1/edit");
    const discount = await screen.findByLabelText(/Discount/);
    await userEvent.clear(discount);
    await userEvent.type(discount, "5");
    const notes = screen.getByLabelText(/Notes and terms/);
    await userEvent.type(notes, " Valid for 10 days.");
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));
    expect(await screen.findAllByText("Say why this estimate is being revised (at least 10 characters).")).not.toHaveLength(0);
    expect(estimateService.update).not.toHaveBeenCalled();
    await userEvent.type(screen.getByLabelText(/Reason for revision/), "Goodwill discount agreed on call");
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));
    await userEvent.click(within(await screen.findByRole("dialog", { name: "Save changes to a sent estimate?" })).getByRole("button", { name: "Save changes" }));
    await vi.waitFor(() => expect(estimateService.update).toHaveBeenCalledTimes(1));
    expect(vi.mocked(estimateService.update).mock.calls[0][1]).toMatchObject({ discountPercent: "5", revisionReason: "Goodwill discount agreed on call" });
  });

  it("keeps saved line ids and asks for a reason when the fee is overridden", async () => {
    signIn(writer());
    vi.mocked(estimateService.meta).mockResolvedValue({ ...meta, canOverrideFee: true });
    vi.mocked(estimateService.update).mockResolvedValue(estimate);
    renderApp("/estimates/e1/edit");
    const fee = await screen.findByLabelText("Fee rate");
    await screen.findByText("₹1,180.00");
    await userEvent.clear(fee);
    await userEvent.type(fee, "12");
    await userEvent.click(await screen.findByRole("button", { name: "Save changes" }));
    expect(await screen.findAllByText("Give a reason for using a fee other than the default.")).not.toHaveLength(0);
    expect(estimateService.update).not.toHaveBeenCalled();
    await userEvent.type(screen.getByLabelText(/Reason for the fee/), "Strategic project");
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));
    await vi.waitFor(() => expect(estimateService.update).toHaveBeenCalled());
    expect(vi.mocked(estimateService.update).mock.calls[0]).toMatchObject([
      "e1",
      { feePercent: "12", feeOverrideReason: "Strategic project", version: 3, lines: [{ id: "li1", productId: "pr1", quantity: "2" }] },
    ]);
  });

  it("shows the B2C comparison only when the server sends it", async () => {
    signIn(internalUser());
    vi.mocked(estimateService.preview).mockImplementation(async (input) => ({
      ...preview(input.lines.length),
      internal: { lines: [{ lineNo: 1, b2cName: "Glide Pro", b2cRate: null, amount: "0.00", b2cPriceMissing: true }], totals, uplift: "-1000.00", missingB2cPrices: 1 },
    }));
    renderApp("/estimates/new?leadId=l1");
    await userEvent.click(await screen.findByRole("button", { name: /Add item/ }));
    await userEvent.click(await screen.findByRole("button", { name: "Add Smart Glide" }));
    expect(await screen.findByText("Internal B2C comparison")).toBeInTheDocument();
    expect(screen.getByText(/1 item\(s\) have no B2C price/)).toBeInTheDocument();
    expect(screen.getByText(/B2C: Glide Pro/)).toBeInTheDocument();
  });
});
