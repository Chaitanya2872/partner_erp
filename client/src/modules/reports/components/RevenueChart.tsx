import { TrendChart } from "@/components/charts/Charts";
import type { Trend } from "@/modules/dashboard/types/dashboard.types";

/**
 * Invoiced (taxable, issued invoices) against collected (ex-GST share of standing receipts) per bucket. Only the
 * rupee series are plotted, so the chart keeps a single axis.
 */
export function RevenueChart({ trend, loading, title = "Invoiced vs collected", description }: { trend?: Trend | null; loading?: boolean; title?: string; description?: string }) {
  const inr = trend ? { ...trend, series: trend.series.filter((s) => s.unit === "INR") } : trend;
  return (
    <TrendChart
      title={title}
      description={description ?? "Taxable value of invoices issued and the ex-GST share of payments received, excluding cancelled invoices and reversed receipts."}
      trend={inr}
      loading={loading}
      unavailable={!loading && (!inr || inr.series.length === 0) ? "Needs Invoices or Payments read access with the All records scope." : undefined}
    />
  );
}
