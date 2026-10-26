import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderApp } from "@/test/renderApp";
import { catalog, makeMe, row } from "@/test/fixtures";
import { authService } from "@/services/auth.service";
import { ApiRequestError } from "@/services/api";
import { paymentService } from "../services/paymentService";
import type { Payment, PaymentInvoiceOption, PaymentMeta, PaymentReport, PaymentSummary } from "../types/payment.types";

vi.mock("@/services/auth.service", () => ({
  authService: { restore: vi.fn(), me: vi.fn(), modules: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() },
}));
vi.mock("../services/paymentService", () => ({
  paymentService: { list: vi.fn(), report: vi.fn(), meta: vi.fn(), get: vi.fn(), history: vi.fn(), record: vi.fn(), reverse: vi.fn(), exportCsv: vi.fn(), invoiceOptions: vi.fn() },
}));

const meta: PaymentMeta = {
  methods: [
    { value: "UPI", label: "UPI" },
    { value: "NEFT_RTGS", label: "NEFT / RTGS" },
    { value: "CHEQUE", label: "Cheque" },
    { value: "CASH", label: "Cash" },
    { value: "CARD", label: "Card" },
  ],
  entryTypes: [
    { value: "RECEIPT", label: "Receipt" },
    { value: "REVERSAL", label: "Reversal" },
    { value: "REFUND", label: "Refund" },
  ],
  states: [
    { value: "ACTIVE", label: "Active" },
    { value: "REVERSED", label: "Reversed" },
  ],
  referenceOptional: ["CASH"],
  maxReason: 500,
  maxNotes: 500,
  canWrite: true,
  canReverse: false,
  canExport: false,
};

const base = {
  invoice: { id: "i1", invoiceNumber: "INV-2026-000001" },
  billingName: "Rao Residence",
  partner: { id: "p1", name: "Alpha Interiors" },
  reversalReason: null,
  createdAt: "2026-10-26T05:00:00Z",
};
const summaries: PaymentSummary[] = [
  { ...base, id: "pay1", paymentNumber: "PAY-2026-000001", entryType: "RECEIPT", entryTypeLabel: "Receipt", paymentDate: "2026-10-20", amount: "1000.00", netAmount: "847.46", method: "UPI", methodLabel: "UPI", reference: "UTR123456", reversed: true, reversalOf: null },
  { ...base, id: "pay2", paymentNumber: "PAY-2026-000002", entryType: "REVERSAL", entryTypeLabel: "Reversal", paymentDate: "2026-10-26", amount: "-1000.00", netAmount: "-847.46", method: "UPI", methodLabel: "UPI", reference: "UTR123456", reversed: false, reversalOf: { id: "pay1", paymentNumber: "PAY-2026-000001" }, reversalReason: "Bounced" },
];
const report: PaymentReport = {
  entries: 2, receipts: 1, reversals: 1, received: "1000.00", reversed: "-1000.00", refunded: "0.00", netReceived: "0.00", netReceivedExGst: "0.00",
  byMethod: [{ method: "UPI", label: "UPI", receipts: 1, netReceived: "0.00" }],
  receivables: { outstandingInvoices: 1, outstandingAmount: "2360.00", overdueInvoices: 0, overdueAmount: "0.00" },
};
const option: PaymentInvoiceOption = {
  id: "i1", invoiceNumber: "INV-2026-000001", invoiceDate: "2026-10-01", dueDate: "2026-10-31", billingName: "Rao Residence",
  partner: { id: "p1", name: "Alpha Interiors" }, grandTotal: "2360.00", paidAmount: "0.00", balance: "2360.00", paymentStatusLabel: "Unpaid",
};
const receipt: Payment = {
  id: "pay3", paymentNumber: "PAY-2026-000003", entryType: "RECEIPT", entryTypeLabel: "Receipt", paymentDate: "2026-10-26", amount: "1000.00", netAmount: "847.46",
  method: "NEFT_RTGS", methodLabel: "NEFT / RTGS", reference: "UTR998877", notes: null, partner: { id: "p1", name: "Alpha Interiors" },
  invoice: { id: "i1", invoiceNumber: "INV-2026-000001", status: "ISSUED", billingName: "Rao Residence", invoiceDate: "2026-10-01", dueDate: "2026-10-31", grandTotal: "2360.00", taxableAmount: "2000.00", paidAmount: "1000.00", balance: "1360.00", paymentStatus: "PARTLY_PAID", paymentStatusLabel: "Partly paid" },
  reversal: null, reversalOf: null, reversalReason: null, canReverse: true, reverseBlockedBy: null, createdAt: "2026-10-26T05:00:00Z", createdByName: "Test Admin",
};

const signIn = (me: ReturnType<typeof makeMe>) => {
  vi.mocked(authService.restore).mockResolvedValue(me);
  vi.mocked(authService.me).mockResolvedValue(me);
};
const reader = () => makeMe("ADMIN", { modules: [row("DASHBOARD"), row("PAYMENTS", { dataScope: "ALL" })] });
const writer = () => makeMe("ADMIN", { modules: [row("DASHBOARD"), row("PAYMENTS", { write: true, export: true, dataScope: "ALL" }), row("INVOICES", { dataScope: "ALL" })] });
const reverser = () => makeMe("ADMIN", { modules: [row("DASHBOARD"), row("PAYMENTS", { dataScope: "ALL" })] }, ["PAYMENTS:REVERSE_PAYMENT"]);

beforeEach(() => {
  vi.mocked(authService.modules).mockResolvedValue(catalog);
  vi.mocked(paymentService.meta).mockResolvedValue(meta);
  vi.mocked(paymentService.list).mockResolvedValue({ content: summaries, meta: { page: 0, size: 20, totalElements: 2, totalPages: 1 } });
  vi.mocked(paymentService.report).mockResolvedValue(report);
  vi.mocked(paymentService.invoiceOptions).mockResolvedValue([option]);
  vi.mocked(paymentService.get).mockResolvedValue(receipt);
  vi.mocked(paymentService.history).mockResolvedValue([
    { id: "h1", at: "2026-10-26T05:00:00Z", actorName: "Test Admin", action: "PAYMENT_RECORDED", changes: [{ field: "reference", from: null, to: "••8877" }, { field: "balanceAfter", from: null, to: "1360.00" }] },
  ]);
});

describe("payments ledger", () => {
  it("lists receipts and reversals with totals, without record or export for a reader", async () => {
    signIn(reader());
    renderApp("/payments");
    const table = await screen.findByRole("table", { name: "Payments" });
    expect(await within(table).findByText("PAY-2026-000001")).toBeInTheDocument();
    expect(within(table).getByText("Reversed")).toBeInTheDocument();
    expect(within(table).getByText("Reversal")).toBeInTheDocument();
    expect(within(table).getByText(/reverses PAY-2026-000001/)).toBeInTheDocument();
    // Without Invoices read the invoice number isn't a link.
    expect(within(table).queryByRole("link", { name: "INV-2026-000001" })).not.toBeInTheDocument();
    expect(await screen.findByText("Outstanding")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Record payment/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Export CSV/ })).not.toBeInTheDocument();
  });

  it("passes filters to the list and the report and exports the filtered ledger", async () => {
    signIn(writer());
    vi.mocked(paymentService.exportCsv).mockResolvedValue({ text: "payment_number\n", filename: "iotiq-payments.csv" });
    renderApp("/payments");
    await screen.findByRole("table", { name: "Payments" });
    await screen.findByRole("option", { name: "Reversed" });
    await userEvent.selectOptions(screen.getByLabelText("Filter by state"), "REVERSED");
    await vi.waitFor(() => expect(paymentService.list).toHaveBeenLastCalledWith(expect.objectContaining({ state: "REVERSED", page: 0 }), expect.anything()));
    expect(paymentService.report).toHaveBeenLastCalledWith(expect.objectContaining({ state: "REVERSED" }), expect.anything());
    await userEvent.click(screen.getByRole("button", { name: /Export CSV/ }));
    await vi.waitFor(() => expect(paymentService.exportCsv).toHaveBeenCalledWith(expect.objectContaining({ state: "REVERSED" })));
  });
});

describe("recording a payment", () => {
  it("checks the balance and reference, then records with an idempotency key", async () => {
    signIn(writer());
    vi.mocked(paymentService.record).mockResolvedValue(receipt);
    renderApp("/payments");
    await userEvent.click(await screen.findByRole("button", { name: /Record payment/ }));
    const dialog = await screen.findByRole("dialog", { name: "Record payment" });
    await userEvent.click(within(dialog).getByRole("combobox", { name: /Invoice/ }));
    await userEvent.click(await screen.findByRole("option", { name: /INV-2026-000001/ }));
    const amount = within(dialog).getByLabelText(/Amount received/);
    expect(amount).toHaveValue("2360.00");
    await userEvent.clear(amount);
    await userEvent.type(amount, "2500");
    await userEvent.selectOptions(within(dialog).getByLabelText(/Method/), "NEFT_RTGS");
    await userEvent.click(within(dialog).getByRole("button", { name: "Record payment" }));
    expect(await within(dialog).findByText(/can't be more than the balance/)).toBeInTheDocument();
    expect(within(dialog).getByText("Enter the reference (UTR, cheque number or transaction id).")).toBeInTheDocument();
    expect(paymentService.record).not.toHaveBeenCalled();

    await userEvent.clear(amount);
    await userEvent.type(amount, "1000");
    await userEvent.type(within(dialog).getByLabelText(/Reference/), "UTR998877");
    await userEvent.click(within(dialog).getByRole("button", { name: "Record payment" }));
    await vi.waitFor(() =>
      expect(paymentService.record).toHaveBeenCalledWith(
        expect.objectContaining({ invoiceId: "i1", amount: "1000", method: "NEFT_RTGS", reference: "UTR998877", idempotencyKey: expect.stringMatching(/^[0-9a-f-]{36}$/) }),
      ),
    );
    expect(vi.mocked(paymentService.record).mock.calls[0][0].confirmDuplicate).toBeUndefined();
  });

  it("asks before recording a possible duplicate and resends with the same key", async () => {
    signIn(writer());
    vi.mocked(paymentService.record)
      .mockRejectedValueOnce(new ApiRequestError(409, "POSSIBLE_DUPLICATE", "A payment of ₹1,000.00 by Cash on 26 Oct 2026 is already recorded."))
      .mockResolvedValueOnce(receipt);
    renderApp("/payments");
    await userEvent.click(await screen.findByRole("button", { name: /Record payment/ }));
    const dialog = await screen.findByRole("dialog", { name: "Record payment" });
    await userEvent.click(within(dialog).getByRole("combobox", { name: /Invoice/ }));
    await userEvent.click(await screen.findByRole("option", { name: /INV-2026-000001/ }));
    const amount = within(dialog).getByLabelText(/Amount received/);
    await userEvent.clear(amount);
    await userEvent.type(amount, "1000");
    // Cash needs no reference.
    await userEvent.selectOptions(within(dialog).getByLabelText(/Method/), "CASH");
    await userEvent.click(within(dialog).getByRole("button", { name: "Record payment" }));
    expect(await within(dialog).findByText(/is already recorded/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Record anyway" }));
    await vi.waitFor(() => expect(paymentService.record).toHaveBeenCalledTimes(2));
    const [first, second] = vi.mocked(paymentService.record).mock.calls.map((c) => c[0]);
    expect(first.confirmDuplicate).toBeUndefined();
    expect(second).toMatchObject({ confirmDuplicate: true, method: "CASH", reference: undefined });
    expect(second.idempotencyKey).toBe(first.idempotencyKey);
  });
});

describe("payment details", () => {
  it("reverses a receipt only with a written reason", async () => {
    signIn(reverser());
    vi.mocked(paymentService.reverse).mockResolvedValue({ ...receipt, canReverse: false, reversal: { id: "pay4", paymentNumber: "PAY-2026-000004", paymentDate: "2026-10-26", reason: "Cheque bounced", createdAt: "2026-10-26T06:00:00Z", createdByName: "Test Admin" } });
    renderApp("/payments/pay3");
    expect(await screen.findByRole("heading", { name: "PAY-2026-000003" })).toBeInTheDocument();
    expect(await screen.findByText(/Reference: ••8877/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Reverse payment/ }));
    const dialog = await screen.findByRole("dialog", { name: "Reverse PAY-2026-000003?" });
    expect(within(dialog).getByText("₹2,360.00")).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Reverse payment" }));
    expect(await within(dialog).findByText("Give the reason for reversing this payment.")).toBeInTheDocument();
    expect(paymentService.reverse).not.toHaveBeenCalled();
    await userEvent.type(within(dialog).getByLabelText(/Reason for reversing/), "Cheque bounced");
    await userEvent.click(within(dialog).getByRole("button", { name: "Reverse payment" }));
    await vi.waitFor(() => expect(paymentService.reverse).toHaveBeenCalledWith("pay3", "Cheque bounced"));
  });

  it("explains why a receipt can't be reversed and hides the action", async () => {
    signIn(reverser());
    vi.mocked(paymentService.get).mockResolvedValue({ ...receipt, canReverse: false, reverseBlockedBy: "This receipt is being reversed by someone else." });
    renderApp("/payments/pay3");
    expect(await screen.findByText(/being reversed by someone else/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Reverse payment/ })).not.toBeInTheDocument();
  });

  it("shows a reversed receipt with its reversal", async () => {
    signIn(reader());
    vi.mocked(paymentService.get).mockResolvedValue({ ...receipt, canReverse: false, reversal: { id: "pay4", paymentNumber: "PAY-2026-000004", paymentDate: "2026-10-26", reason: "Cheque bounced", createdAt: "2026-10-26T06:00:00Z", createdByName: "Test Admin" } });
    renderApp("/payments/pay3");
    expect(await screen.findByRole("link", { name: "PAY-2026-000004" })).toBeInTheDocument();
    expect(screen.getByText(/“Cheque bounced”/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Reverse payment/ })).not.toBeInTheDocument();
  });
});
