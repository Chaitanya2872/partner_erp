import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderApp } from "@/test/renderApp";
import { catalog, makeMe, row } from "@/test/fixtures";
import { authService } from "@/services/auth.service";
import { saveTextFile } from "@/utils/download";
import { productService } from "../services/productService";
import type { Product, ProductHistoryEntry, ProductImportResult, ProductMeta } from "../types/product.types";

vi.mock("@/services/auth.service", () => ({
  authService: { restore: vi.fn(), me: vi.fn(), modules: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() },
}));
vi.mock("@/utils/download", () => ({ saveTextFile: vi.fn() }));
vi.mock("../services/productService", () => ({
  productService: {
    list: vi.fn(),
    meta: vi.fn(),
    get: vi.fn(),
    history: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    setActive: vi.fn(),
    remove: vi.fn(),
    exportCsv: vi.fn(),
    template: vi.fn(),
    importCsv: vi.fn(),
  },
}));

const meta: ProductMeta = {
  categories: [
    { value: "SMART_TOUCH_INTERFACES", label: "Smart Touch Interfaces", total: 6, active: 6 },
    { value: "LOCKS_ENTRY", label: "Locks & Entry", total: 5, active: 5 },
  ],
  defaultGstPercent: 18,
  defaultUnit: "nos",
  maxAliases: 20,
};

/** Partner-safe view, as served without VIEW_INTERNAL_B2C. */
const sti: Product = {
  id: "p1",
  sku: "STI-2M",
  category: "SMART_TOUCH_INTERFACES",
  categoryLabel: "Smart Touch Interfaces",
  partnerName: "Smart Touch Interface - 2M (STI-2M)",
  unit: "nos",
  hsn: null,
  partnerPrice: 5749,
  gstPercent: 18,
  active: true,
  version: 3,
  updatedAt: "2026-10-20T00:00:00Z",
};

/** Internal view of the same product (B2C price not set yet). */
const stiInternal: Product = {
  ...sti,
  b2cName: "2 Module With Edge",
  aliases: ["Dual-module capacitive panel"],
  b2cPrice: null,
  costPrice: null,
  discountPercent: 0,
  b2cPriceMissing: true,
  sameNameAsB2c: false,
};

const lock: Product = {
  ...stiInternal,
  id: "p2",
  sku: "LOCK-P1",
  category: "LOCKS_ENTRY",
  categoryLabel: "Locks & Entry",
  partnerName: "Smart Lock Pro",
  b2cName: "Smart Lock Pro",
  partnerPrice: 8000,
  b2cPrice: 10000,
  costPrice: 6000,
  discountPercent: 20,
  b2cPriceMissing: false,
  sameNameAsB2c: true,
  active: false,
  version: 1,
};

const history: ProductHistoryEntry[] = [
  { id: "h2", at: "2026-10-21T00:00:00Z", actorName: "Test Admin", action: "PRODUCT_UPDATED", changes: [{ field: "partnerPrice", from: 5500, to: 5749 }] },
  { id: "h1", at: "2026-10-20T00:00:00Z", actorName: null, action: "PRODUCT_CREATED", changes: [{ field: "sku", from: null, to: "STI-2M" }] },
];

const page = (content: Product[]) => ({ content, meta: { page: 0, size: 50, totalElements: content.length, totalPages: 1 } });

const internalAdmin = () =>
  makeMe("ADMIN", { modules: [row("DASHBOARD"), row("PRODUCTS", { write: true, delete: true, export: true })] }, ["PRODUCTS:VIEW_INTERNAL_B2C"]);
const editor = () => makeMe("ADMIN"); // Products read + write, no internal action, export or delete
const reader = () => makeMe("USER"); // Products read only

beforeEach(() => {
  vi.mocked(authService.modules).mockResolvedValue(catalog);
  vi.mocked(productService.meta).mockResolvedValue(meta);
  vi.mocked(productService.history).mockResolvedValue(history);
});

const signIn = (me: ReturnType<typeof makeMe>) => {
  vi.mocked(authService.restore).mockResolvedValue(me);
  vi.mocked(authService.me).mockResolvedValue(me);
};

const rowOf = async (sku: string) => (await screen.findByRole("link", { name: sku })).closest("tr")!;

describe("price sheet", () => {
  it("shows the internal columns, pills and discount with VIEW_INTERNAL_B2C", async () => {
    signIn(internalAdmin());
    vi.mocked(productService.list).mockResolvedValue(page([stiInternal, lock]));
    renderApp("/products");

    const stiRow = await rowOf("STI-2M");
    expect(within(stiRow).getByText("B2C: 2 Module With Edge")).toBeInTheDocument();
    expect(within(stiRow).getByText("B2C price needed")).toBeInTheDocument();
    expect(within(stiRow).getByText("0%")).toBeInTheDocument();
    const lockRow = await rowOf("LOCK-P1");
    expect(within(lockRow).getByText("same as B2C")).toBeInTheDocument();
    expect(within(lockRow).getByText("20%")).toBeInTheDocument();
    expect(within(lockRow).getByText("Inactive")).toBeInTheDocument();
    expect(within(lockRow).getByRole("button", { name: "Activate LOCK-P1" })).toBeInTheDocument();

    expect(screen.getByRole("columnheader", { name: "Discount" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /New product/ })).toHaveAttribute("href", "/products/new");
    expect(screen.getByRole("button", { name: /Import CSV/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Export CSV/ })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Filter by B2C price" })).toBeInTheDocument();
  });

  it("gives writers without the internal action partner columns and edit only", async () => {
    signIn(editor());
    vi.mocked(productService.list).mockResolvedValue(page([sti]));
    renderApp("/products");

    const stiRow = await rowOf("STI-2M");
    expect(within(stiRow).getByRole("link", { name: "Edit STI-2M" })).toHaveAttribute("href", "/products/p1/edit");
    expect(within(stiRow).getByRole("button", { name: "Deactivate STI-2M" })).toBeInTheDocument();
    expect(within(stiRow).queryByRole("button", { name: "Delete STI-2M" })).not.toBeInTheDocument();
    expect(screen.queryByRole("columnheader", { name: "B2C price" })).not.toBeInTheDocument();
    expect(screen.queryByRole("columnheader", { name: "Discount" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /New product/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Import CSV/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Export CSV/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: "Filter by B2C price" })).not.toBeInTheDocument();
  });

  it("passes the status filter to the server and resets to the first page", async () => {
    signIn(reader());
    vi.mocked(productService.list).mockResolvedValue(page([sti]));
    renderApp("/products");
    await rowOf("STI-2M");
    expect(within(await rowOf("STI-2M")).queryByRole("link", { name: /Edit/ })).not.toBeInTheDocument();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Filter by status" }), "false");
    await waitFor(() => expect(productService.list).toHaveBeenLastCalledWith(expect.objectContaining({ active: "false", page: 0 }), expect.anything()));
  });

  it("asks before deactivating and activates at once", async () => {
    signIn(internalAdmin());
    vi.mocked(productService.list).mockResolvedValue(page([stiInternal, lock]));
    vi.mocked(productService.setActive).mockImplementation(async (id, active) => ({ ...(id === "p1" ? stiInternal : lock), active }));
    renderApp("/products");

    await userEvent.click(within(await rowOf("STI-2M")).getByRole("button", { name: "Deactivate STI-2M" }));
    const dialog = await screen.findByRole("dialog", { name: "Deactivate product?" });
    expect(within(dialog).getByText(/can't be added to new estimates or invoices/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Deactivate product" }));
    await waitFor(() => expect(productService.setActive).toHaveBeenCalledWith("p1", false, 3));
    expect(await screen.findByText("STI-2M deactivated.")).toBeInTheDocument();

    await userEvent.click(within(await rowOf("LOCK-P1")).getByRole("button", { name: "Activate LOCK-P1" }));
    await waitFor(() => expect(productService.setActive).toHaveBeenCalledWith("p2", true, 1));
  });

  it("deletes after confirmation and reports a product in use", async () => {
    signIn(internalAdmin());
    vi.mocked(productService.list).mockResolvedValue(page([stiInternal]));
    const { ApiRequestError } = await import("@/services/api");
    vi.mocked(productService.remove).mockRejectedValue(
      new ApiRequestError(409, "PRODUCT_IN_USE", "This product is used on estimates or invoices, so it can't be deleted. Deactivate it instead."),
    );
    renderApp("/products");

    await userEvent.click(within(await rowOf("STI-2M")).getByRole("button", { name: "Delete STI-2M" }));
    await userEvent.click(within(await screen.findByRole("dialog", { name: "Delete product?" })).getByRole("button", { name: "Delete product" }));
    await waitFor(() => expect(productService.remove).toHaveBeenCalledWith("p1"));
    expect(await screen.findByText(/so it can't be deleted. Deactivate it instead./)).toBeInTheDocument();
  });

  it("exports the filtered sheet as a CSV download", async () => {
    signIn(internalAdmin());
    vi.mocked(productService.list).mockResolvedValue(page([stiInternal]));
    vi.mocked(productService.exportCsv).mockResolvedValue({ text: "sku\r\nSTI-2M\r\n", filename: "iotiq-item-sheet.csv" });
    renderApp("/products");
    await rowOf("STI-2M");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Filter by category" }), "SMART_TOUCH_INTERFACES");
    await userEvent.click(screen.getByRole("button", { name: /Export CSV/ }));
    await waitFor(() => expect(saveTextFile).toHaveBeenCalledWith("sku\r\nSTI-2M\r\n", "iotiq-item-sheet.csv"));
    expect(productService.exportCsv).toHaveBeenCalledWith(expect.objectContaining({ category: "SMART_TOUCH_INTERFACES" }));
  });
});

describe("CSV import", () => {
  const preview: ProductImportResult = {
    dryRun: true,
    total: 3,
    created: 1,
    updated: 1,
    unchanged: 0,
    failed: 1,
    columns: ["sku", "category", "b2c_name", "cp_name", "cp_price"],
    rows: [
      { row: 1, sku: "NEW-1", outcome: "CREATE", changes: [], errors: [], warnings: ["B2C price needed."] },
      { row: 2, sku: "STI-2M", outcome: "UPDATE", changes: ["cp_price: 5749 → 5999"], errors: [], warnings: [] },
      { row: 3, sku: "BAD-1", outcome: "ERROR", changes: [], errors: ['category: Unknown category "Switching".'], warnings: [] },
    ],
  };

  it("previews first, then imports the valid rows", async () => {
    signIn(internalAdmin());
    vi.mocked(productService.list).mockResolvedValue(page([stiInternal]));
    vi.mocked(productService.importCsv).mockImplementation(async (_file, dryRun) =>
      dryRun ? preview : { ...preview, dryRun: false },
    );
    renderApp("/products");
    await rowOf("STI-2M");
    await userEvent.click(screen.getByRole("button", { name: /Import CSV/ }));
    const dialog = await screen.findByRole("dialog", { name: "Import price sheet CSV" });
    const previewButton = within(dialog).getByRole("button", { name: "Preview" });
    expect(previewButton).toBeDisabled();

    const file = new File(["sku,b2c_name,cp_name\n"], "items.csv", { type: "text/csv" });
    await userEvent.upload(within(dialog).getByLabelText("CSV file"), file);
    await userEvent.click(previewButton);
    await waitFor(() => expect(productService.importCsv).toHaveBeenCalledWith(file, true));
    expect(await within(dialog).findByText(/3 rows: 1 new, 1 to update, 0 unchanged, 1 with errors \(skipped\)/)).toBeInTheDocument();
    expect(within(dialog).getByText("cp_price: 5749 → 5999")).toBeInTheDocument();
    expect(within(dialog).getByText('category: Unknown category "Switching".')).toBeInTheDocument();
    expect(within(dialog).getByText("B2C price needed.")).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole("button", { name: "Import 2 rows" }));
    await waitFor(() => expect(productService.importCsv).toHaveBeenCalledWith(file, false));
    expect(await screen.findByText("Import finished: 1 created, 1 updated, 1 skipped with errors.")).toBeInTheDocument();
    expect(screen.queryByRole("dialog", { name: "Import price sheet CSV" })).not.toBeInTheDocument();
  });

  it("downloads the template from the dialog", async () => {
    signIn(internalAdmin());
    vi.mocked(productService.list).mockResolvedValue(page([stiInternal]));
    vi.mocked(productService.template).mockResolvedValue({ text: "sku,category\r\n", filename: "iotiq-item-template.csv" });
    renderApp("/products");
    await rowOf("STI-2M");
    await userEvent.click(screen.getByRole("button", { name: /Import CSV/ }));
    await userEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: /Download template/ }));
    await waitFor(() => expect(saveTextFile).toHaveBeenCalledWith("sku,category\r\n", "iotiq-item-template.csv"));
  });
});

describe("product form", () => {
  it("validates, previews the discount and creates with the internal fields", async () => {
    signIn(internalAdmin());
    vi.mocked(productService.create).mockResolvedValue({ ...lock, id: "p9", sku: "SG-NEW" });
    vi.mocked(productService.get).mockResolvedValue({ ...lock, id: "p9", sku: "SG-NEW" });
    const { router } = renderApp("/products/new");

    const form = await screen.findByRole("form", { name: "New product" });
    await userEvent.click(within(form).getByRole("button", { name: "Create product" }));
    expect(await within(form).findByText("SKU is required.")).toBeInTheDocument();
    expect(within(form).getByText("B2C name is required.")).toBeInTheDocument();
    expect(within(form).getByText("Partner price is required.")).toBeInTheDocument();

    await userEvent.type(within(form).getByLabelText(/^SKU/), "SG-NEW");
    await userEvent.selectOptions(within(form).getByLabelText(/^Category/), "SMART_GLIDE");
    await userEvent.type(within(form).getByLabelText(/^HSN/), "8536 5090");
    await userEvent.type(within(form).getByLabelText(/^Partner-facing name/), "Curtain drive");
    await userEvent.type(within(form).getByLabelText(/^B2C name/), "curtain drive");
    expect(within(form).getByText(/same as the B2C name/)).toBeInTheDocument();
    await userEvent.clear(within(form).getByLabelText(/^B2C name/));
    await userEvent.type(within(form).getByLabelText(/^B2C name/), "Curtain Controller");
    await userEvent.type(within(form).getByLabelText(/^Alternative partner names/), "Drive A\nDrive B\ndrive a\n");
    await userEvent.type(within(form).getByLabelText(/^Partner price/), "8000");
    expect(within(form).getByText(/B2C price needed/)).toBeInTheDocument();
    await userEvent.type(within(form).getByLabelText(/^B2C price/), "12000");
    expect(within(form).getByText("Partner discount: 33%")).toBeInTheDocument();
    await userEvent.type(within(form).getByLabelText(/^Landed cost/), "9000");
    expect(within(form).getByText("The partner price is below the landed cost.")).toBeInTheDocument();

    await userEvent.click(within(form).getByRole("button", { name: "Create product" }));
    await waitFor(() =>
      expect(productService.create).toHaveBeenCalledWith({
        sku: "SG-NEW",
        category: "SMART_GLIDE",
        unit: "nos",
        hsn: "85365090",
        partnerName: "Curtain drive",
        partnerPrice: "8000",
        gstPercent: "18",
        b2cName: "Curtain Controller",
        aliases: ["Drive A", "Drive B"],
        b2cPrice: "12000",
        costPrice: "9000",
        version: undefined,
      }),
    );
    await waitFor(() => expect(router.state.location.pathname).toBe("/products/p9"));
    expect(await screen.findByText("SG-NEW created.")).toBeInTheDocument();
  });

  it("shows server field errors such as a duplicate SKU", async () => {
    signIn(internalAdmin());
    const { ApiRequestError } = await import("@/services/api");
    vi.mocked(productService.create).mockRejectedValue(
      new ApiRequestError(400, "VALIDATION_FAILED", "Validation failed.", [{ field: "sku", message: "That SKU already exists." }]),
    );
    renderApp("/products/new");
    const form = await screen.findByRole("form", { name: "New product" });
    await userEvent.type(within(form).getByLabelText(/^SKU/), "sti-2m");
    await userEvent.selectOptions(within(form).getByLabelText(/^Category/), "SMART_TOUCH_INTERFACES");
    await userEvent.type(within(form).getByLabelText(/^Partner-facing name/), "Panel");
    await userEvent.type(within(form).getByLabelText(/^B2C name/), "Panel B2C");
    await userEvent.type(within(form).getByLabelText(/^Partner price/), "100");
    await userEvent.click(within(form).getByRole("button", { name: "Create product" }));
    expect(await within(form).findByText("That SKU already exists.")).toBeInTheDocument();
  });

  it("lets writers without the internal action edit partner fields only", async () => {
    signIn(editor());
    vi.mocked(productService.get).mockResolvedValue(sti);
    vi.mocked(productService.update).mockResolvedValue({ ...sti, partnerPrice: 5999, version: 4 });
    renderApp("/products/p1/edit");

    const form = await screen.findByRole("form", { name: "Edit STI-2M" });
    expect(screen.getByText("Partner-facing fields only")).toBeInTheDocument();
    expect(within(form).queryByLabelText(/^B2C name/)).not.toBeInTheDocument();
    expect(within(form).queryByLabelText(/^B2C price/)).not.toBeInTheDocument();
    expect(within(form).queryByLabelText(/^Landed cost/)).not.toBeInTheDocument();
    const price = within(form).getByLabelText(/^Partner price/);
    await userEvent.clear(price);
    await userEvent.type(price, "5999");
    await userEvent.click(within(form).getByRole("button", { name: "Save changes" }));
    await waitFor(() => expect(productService.update).toHaveBeenCalled());
    const [, body] = vi.mocked(productService.update).mock.calls[0];
    expect(body).toEqual({
      sku: "STI-2M",
      category: "SMART_TOUCH_INTERFACES",
      unit: "nos",
      hsn: "",
      partnerName: "Smart Touch Interface - 2M (STI-2M)",
      partnerPrice: "5999",
      gstPercent: "18",
      version: 3,
    });
  });

  it("refuses the new-product page without the internal action", async () => {
    signIn(editor());
    renderApp("/products/new");
    expect(await screen.findByRole("heading", { name: /don't have access/i })).toBeInTheDocument();
  });
});

describe("product details", () => {
  it("shows the internal card and the full history with the action", async () => {
    signIn(internalAdmin());
    vi.mocked(productService.get).mockResolvedValue(lock);
    renderApp("/products/p2");
    expect(await screen.findByRole("heading", { name: "Smart Lock Pro" })).toBeInTheDocument();
    expect(screen.getByText("Inactive product")).toBeInTheDocument();
    expect(screen.getByText("same as B2C")).toBeInTheDocument();
    expect(screen.getByText("₹10,000.00 excl. GST")).toBeInTheDocument();
    expect(screen.getByText("20%")).toBeInTheDocument();
    expect(screen.getByText("₹9,440.00")).toBeInTheDocument();
    expect(await screen.findByText("Partner price: ₹5,500.00 → ₹5,749.00")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Activate/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Delete/ })).toBeInTheDocument();
  });

  it("hides the internal card from readers", async () => {
    signIn(reader());
    vi.mocked(productService.get).mockResolvedValue(sti);
    renderApp("/products/p1");
    expect(await screen.findByRole("heading", { name: "Smart Touch Interface - 2M (STI-2M)" })).toBeInTheDocument();
    expect(screen.queryByText("Internal B2C")).not.toBeInTheDocument();
    expect(screen.getByText("Changes to internal B2C fields are not shown.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Deactivate/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Edit/ })).not.toBeInTheDocument();
  });
});
