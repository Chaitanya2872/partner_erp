import { describe, expect, it } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { BarsChart, DonutChart, FunnelChart, TrendChart } from "./Charts";
import { bucketLabel, foldSlices, formatValue } from "./chartUtils";
import { percentChange } from "@/modules/dashboard/utils/change";

const trend = {
  granularity: "MONTH" as const,
  buckets: ["2026-04-01", "2026-05-01"],
  series: [
    { key: "invoicedTaxable", label: "Invoiced (taxable)", unit: "INR", values: ["1000.00", "2500.50"] },
    { key: "collectedExGst", label: "Collected (ex-GST)", unit: "INR", values: ["0.00", "847.46"] },
  ],
};

describe("chart helpers", () => {
  it("labels buckets from ISO dates without shifting the day", () => {
    expect(bucketLabel("2026-10-01", "DAY")).toBe("1 Oct");
    expect(bucketLabel("2026-04-01", "MONTH")).toBe("Apr 2026");
    expect(bucketLabel("2027-03-31", "DAY")).toBe("31 Mar");
  });

  it("folds categories beyond the fifth into Other instead of generating colours", () => {
    const slices = ["A", "B", "C", "D", "E", "F", "G"].map((k, i) => ({ key: k, label: k, count: i + 1, amount: String(10 * (i + 1)) }));
    const folded = foldSlices(slices);
    expect(folded).toHaveLength(5);
    expect(folded[4]).toEqual({ key: "OTHER", label: "Other", count: 5 + 6 + 7, amount: 50 + 60 + 70 });
    expect(foldSlices(slices.slice(0, 5))).toHaveLength(5);
  });

  it("formats values by unit and computes period-over-period change", () => {
    expect(formatValue("1234.5", "INR")).toMatch(/₹\s?1,234\.50/);
    expect(formatValue(null, "INR")).toBe("—");
    expect(percentChange("150", "100")).toBe(50);
    expect(percentChange(50, 100)).toBe(-50);
    // No ratio against an empty previous period.
    expect(percentChange(10, 0)).toBeNull();
    expect(percentChange(null, 10)).toBeNull();
  });
});

describe("charts", () => {
  it("draws a trend with a legend and switches to a table with totals", async () => {
    render(<TrendChart title="Invoiced vs collected" trend={trend} />);
    expect(screen.getByRole("img", { name: /Invoiced vs collected chart/ })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Show Invoiced vs collected as a table" }));
    const table = screen.getByRole("table", { name: "Invoiced vs collected" });
    expect(within(table).getByRole("columnheader", { name: "Month" })).toBeInTheDocument();
    expect(within(table).getByRole("rowheader", { name: "May 2026" })).toBeInTheDocument();
    const total = within(table).getByRole("rowheader", { name: "Total" }).closest("tr")!;
    expect(within(total).getByText(/3,500\.50/)).toBeInTheDocument();
    expect(within(total).getByText(/847\.46/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /as a table/ })).toHaveAttribute("aria-pressed", "true");
  });

  it("shows loading, empty and unavailable states instead of a plot", () => {
    const { rerender } = render(<TrendChart title="Trend" loading />);
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /as a table/ })).not.toBeInTheDocument();
    rerender(<TrendChart title="Trend" trend={{ ...trend, series: trend.series.map((s) => ({ ...s, values: ["0", "0"] })) }} />);
    expect(screen.getByText("No data for this period.")).toBeInTheDocument();
    rerender(<TrendChart title="Trend" unavailable="Needs Payments read access with the All records scope." />);
    expect(screen.getByText(/Needs Payments read access/)).toBeInTheDocument();
  });

  it("names every donut slice with its value and share", async () => {
    render(
      <DonutChart
        title="Collections by method"
        measure="amount"
        slices={[
          { key: "UPI", label: "UPI", count: 2, amount: "750.00" },
          { key: "CASH", label: "Cash", count: 1, amount: "250.00" },
          { key: "CHEQUE", label: "Cheque", count: 0, amount: "0.00" },
        ]}
      />,
    );
    expect(screen.getByText("75.0%")).toBeInTheDocument();
    expect(screen.getByText("25.0%")).toBeInTheDocument();
    // Zero slices are left out rather than drawn as an invisible wedge.
    expect(screen.queryByText("Cheque")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /as a table/ }));
    const table = screen.getByRole("table", { name: "Collections by method" });
    expect(within(table).getByRole("columnheader", { name: "Amount" })).toBeInTheDocument();
    expect(within(table).getByRole("rowheader", { name: "UPI" })).toBeInTheDocument();
  });

  it("shows ageing buckets as bars with a table alternative", async () => {
    render(
      <BarsChart
        title="Receivables by age"
        measure="amount"
        category="Age"
        slices={[
          { key: "NOT_DUE", label: "Not due", count: 1, amount: "100.00" },
          { key: "D90_PLUS", label: "90+ days", count: 2, amount: "900.00" },
        ]}
      />,
    );
    await userEvent.click(screen.getByRole("button", { name: /as a table/ }));
    const row = screen.getByRole("rowheader", { name: "90+ days" }).closest("tr")!;
    expect(within(row).getByText("2")).toBeInTheDocument();
    expect(within(row).getByText(/900\.00/)).toBeInTheDocument();
  });

  it("states each funnel step as a share of the first", () => {
    render(<FunnelChart title="Lead to estimate" steps={[{ label: "Leads created", count: 3 }, { label: "With an estimate", count: 1 }]} />);
    expect(screen.getByText(/100\.0%/)).toBeInTheDocument();
    expect(screen.getByText(/33\.3%/)).toBeInTheDocument();
  });
});
