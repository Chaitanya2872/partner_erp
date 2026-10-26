import { BarsChart } from "@/components/charts/Charts";
import type { Slice } from "@/modules/dashboard/types/dashboard.types";

/** Leads currently in each stage, in pipeline order (a reopened lead counts once, in its current stage). */
export function LeadFunnelChart({ slices, loading, measure = "count" }: { slices?: Slice[] | null; loading?: boolean; measure?: "count" | "amount" }) {
  return (
    <BarsChart
      title={measure === "count" ? "Leads by stage" : "Pipeline value by stage"}
      description={measure === "count" ? "Every lead in its current stage." : "Budget of leads in each stage, where recorded."}
      slices={slices}
      measure={measure}
      layout="horizontal"
      category="Stage"
      loading={loading}
      unavailable={!loading && !slices ? "Needs Leads read access with the All records scope." : undefined}
      height={280}
    />
  );
}
