import { useState, type ReactNode } from "react";
import { Bar, BarChart, CartesianGrid, Cell, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { BarChart3, Table2 } from "lucide-react";
import { ChartContainer } from "@/components/dashboard/Dashboard";
import { Button } from "@/components/ui/Button";
import { formatCurrencyCompact, formatNumber, NOT_AVAILABLE } from "@/utils/format";
import { bucketLabel, foldSlices, formatValue, hasAmounts, num, type SliceData, type Unit } from "./chartUtils";

/*
 * Phase 12 charts on Recharts (ADR-12). Colours come only from the --chart-* tokens so both themes stay legible.
 * The full five-token palette is not safe for colour-vision deficiencies (DECISIONS.md §M), so identity is never
 * colour alone: two-series lines pair --chart-1 (solid) with --chart-2 (dashed), multi-category charts carry a
 * legend with labels and values, and every chart can switch to a table. Tooltips show only the plotted values.
 * Animation is off: figures appear in place and nothing moves for users who prefer reduced motion.
 */


const axisTick = (unit: Unit) => (value: number) => (unit === "INR" ? formatCurrencyCompact(value) : formatNumber(value));

const SERIES_COLORS = ["var(--chart-1)", "var(--chart-2)", "var(--chart-3)", "var(--chart-4)", "var(--chart-5)"];
const SERIES_DASHES = [undefined, "6 4", "2 3", "8 3 2 3", undefined];

interface TooltipRow {
  name?: unknown;
  value?: unknown;
  color?: string;
  payload?: Record<string, unknown>;
}

function ChartTooltip({ active, payload, label, unit }: { active?: boolean; payload?: TooltipRow[]; label?: ReactNode; unit: Unit }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-md border border-border bg-surface px-3 py-2 text-xs shadow-md">
      {label !== undefined && label !== "" ? <div className="mb-1 font-medium">{label}</div> : null}
      <ul className="space-y-0.5">
        {payload.map((p, i) => (
          <li key={i} className="flex items-center gap-2">
            <span aria-hidden="true" className="inline-block size-2 rounded-full" style={{ background: p.color }} />
            <span className="text-muted-foreground">{String(p.name ?? "")}</span>
            <span className="ml-auto font-mono tabular">{formatValue(p.value, unit)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** A small table of the chart's data: the accessible and exact alternative to the plot. */
function DataTableView({ caption, headers, rows }: { caption: string; headers: string[]; rows: ReactNode[][] }) {
  return (
    <div className="size-full overflow-auto">
      <table className="w-full text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr className="border-b border-border text-left text-xs text-muted-foreground">
            {headers.map((h, i) => (
              <th key={h} scope="col" className={i === 0 ? "py-1.5 pr-3 font-medium" : "py-1.5 pl-3 text-right font-medium"}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, r) => (
            <tr key={r} className="border-b border-border last:border-0">
              {row.map((cell, i) =>
                i === 0 ? (
                  <th key={i} scope="row" className="py-1.5 pr-3 text-left font-normal">
                    {cell}
                  </th>
                ) : (
                  <td key={i} className="py-1.5 pl-3 text-right font-mono tabular">
                    {cell}
                  </td>
                ),
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

interface ChartCardProps {
  title: string;
  description?: ReactNode;
  loading?: boolean;
  empty?: boolean;
  unavailable?: string;
  height?: number;
  chart: () => ReactNode;
  table: () => ReactNode;
  /** Shown under the plot (legend); hidden in table view, which already names every value. */
  legend?: ReactNode;
}

/** ChartContainer with a Chart / Table switch. */
export function ChartCard({ title, description, loading, empty, unavailable, height = 240, chart, table, legend }: ChartCardProps) {
  const [asTable, setAsTable] = useState(false);
  const ready = !loading && !empty && !unavailable;
  return (
    <ChartContainer
      title={title}
      description={description}
      loading={loading}
      empty={empty}
      unavailable={unavailable}
      height={height}
      action={
        ready ? (
          <Button size="sm" variant="ghost" onClick={() => setAsTable((v) => !v)} aria-pressed={asTable} aria-label={`Show ${title} as a table`}>
            {asTable ? <BarChart3 aria-hidden="true" /> : <Table2 aria-hidden="true" />}
            {asTable ? "Chart" : "Table"}
          </Button>
        ) : undefined
      }
    >
      {asTable ? (
        table()
      ) : (
        <div className="flex size-full flex-col" role="img" aria-label={`${title} chart. Use the Table button for the values.`}>
          <div className="min-h-0 flex-1">{chart()}</div>
          {legend}
        </div>
      )}
    </ChartContainer>
  );
}

function SeriesLegend({ items }: { items: { label: string; color: string; dash?: string; value?: string }[] }) {
  return (
    <ul className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground" aria-hidden="true">
      {items.map((item) => (
        <li key={item.label} className="flex items-center gap-1.5">
          <svg width="18" height="6" className="shrink-0">
            <line x1="0" y1="3" x2="18" y2="3" stroke={item.color} strokeWidth="2" strokeDasharray={item.dash} />
          </svg>
          <span>{item.label}</span>
          {item.value ? <span className="font-mono tabular text-foreground">{item.value}</span> : null}
        </li>
      ))}
    </ul>
  );
}

export interface TrendData {
  granularity: "DAY" | "MONTH";
  buckets: string[];
  series: { key: string; label: string; unit: Unit; values: (number | string)[] }[];
}

/** Line chart over the period's buckets. All series share one axis, so pass series of a single unit. */
export function TrendChart({ title, description, trend, loading, unavailable, height = 260 }: { title: string; description?: ReactNode; trend?: TrendData | null; loading?: boolean; unavailable?: string; height?: number }) {
  const series = trend?.series ?? [];
  const unit = series[0]?.unit ?? "COUNT";
  const data = (trend?.buckets ?? []).map((bucket, i) => ({
    bucket: bucketLabel(bucket, trend!.granularity),
    ...Object.fromEntries(series.map((s) => [s.key, num(s.values[i])])),
  }));
  const empty = !loading && !unavailable && (data.length === 0 || series.every((s) => s.values.every((v) => num(v) === 0)));
  const totals = series.map((s) => s.values.reduce<number>((sum, v) => sum + num(v), 0));
  return (
    <ChartCard
      title={title}
      description={description}
      loading={loading}
      unavailable={unavailable}
      empty={empty}
      height={height}
      legend={
        series.length > 1 ? (
          <SeriesLegend items={series.map((s, i) => ({ label: s.label, color: SERIES_COLORS[i], dash: SERIES_DASHES[i], value: formatValue(totals[i], unit) }))} />
        ) : null
      }
      chart={() => (
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
            <CartesianGrid stroke="var(--chart-grid)" vertical={false} />
            <XAxis dataKey="bucket" tick={{ fontSize: 11, fill: "var(--muted-foreground)" }} tickLine={false} axisLine={{ stroke: "var(--chart-grid)" }} minTickGap={16} />
            <YAxis tickFormatter={axisTick(unit)} tick={{ fontSize: 11, fill: "var(--muted-foreground)" }} tickLine={false} axisLine={false} width={64} allowDecimals={unit !== "COUNT"} />
            <Tooltip content={(p) => <ChartTooltip {...(p as object)} unit={unit} />} cursor={{ stroke: "var(--border-strong)" }} />
            {series.map((s, i) => (
              <Line
                key={s.key}
                dataKey={s.key}
                name={s.label}
                type="monotone"
                stroke={SERIES_COLORS[i]}
                strokeDasharray={SERIES_DASHES[i]}
                strokeWidth={2}
                dot={data.length <= 12 ? { r: 3, strokeWidth: 0, fill: SERIES_COLORS[i] } : false}
                activeDot={{ r: 4, stroke: "var(--surface)", strokeWidth: 2 }}
                isAnimationActive={false}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      )}
      table={() => (
        <DataTableView
          caption={title}
          headers={[trend?.granularity === "MONTH" ? "Month" : "Day", ...series.map((s) => s.label)]}
          rows={[
            ...data.map((row) => [row.bucket, ...series.map((s) => formatValue((row as Record<string, unknown>)[s.key], unit))]),
            ["Total", ...totals.map((t) => formatValue(t, unit))],
          ]}
        />
      )}
    />
  );
}

interface SliceChartProps {
  title: string;
  description?: ReactNode;
  slices?: SliceData[] | null;
  /** Which figure to plot; the other is shown in the table. */
  measure: "count" | "amount";
  loading?: boolean;
  unavailable?: string;
  height?: number;
}

const measureOf = (s: SliceData, measure: "count" | "amount") => (measure === "count" ? s.count : num(s.amount));

function sliceTable(title: string, slices: SliceData[], first: string) {
  const amounts = hasAmounts(slices);
  return (
    <DataTableView
      caption={title}
      headers={amounts ? [first, "Count", "Amount"] : [first, "Count"]}
      rows={slices.map((s) => (amounts ? [s.label, formatNumber(s.count), formatValue(s.amount, "INR")] : [s.label, formatNumber(s.count)]))}
    />
  );
}

/**
 * Bars in the order given (pipeline stages, ageing buckets, rankings): one series, one colour, so no legend; the
 * category axis names every bar. `layout="horizontal"` suits long labels and funnels.
 */
export function BarsChart({ title, description, slices, measure, loading, unavailable, height = 240, layout = "vertical", category = "Category" }: SliceChartProps & { layout?: "vertical" | "horizontal"; category?: string }) {
  const unit: Unit = measure === "amount" ? "INR" : "COUNT";
  const rows = slices ?? [];
  const data = rows.map((s) => ({ label: s.label, value: measureOf(s, measure) }));
  const empty = !loading && !unavailable && data.every((d) => d.value === 0);
  const horizontal = layout === "horizontal";
  return (
    <ChartCard
      title={title}
      description={description}
      loading={loading}
      unavailable={unavailable}
      empty={empty}
      height={height}
      chart={() => (
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} layout={horizontal ? "vertical" : "horizontal"} margin={{ top: 8, right: 12, bottom: 0, left: 0 }} barCategoryGap={horizontal ? 6 : "20%"}>
            <CartesianGrid stroke="var(--chart-grid)" horizontal={!horizontal} vertical={horizontal} />
            {horizontal ? (
              <>
                <XAxis type="number" tickFormatter={axisTick(unit)} tick={{ fontSize: 11, fill: "var(--muted-foreground)" }} tickLine={false} axisLine={false} allowDecimals={unit !== "COUNT"} />
                <YAxis type="category" dataKey="label" tick={{ fontSize: 11, fill: "var(--foreground)" }} tickLine={false} axisLine={{ stroke: "var(--chart-grid)" }} width={112} />
              </>
            ) : (
              <>
                <XAxis dataKey="label" tick={{ fontSize: 11, fill: "var(--muted-foreground)" }} tickLine={false} axisLine={{ stroke: "var(--chart-grid)" }} interval={0} />
                <YAxis tickFormatter={axisTick(unit)} tick={{ fontSize: 11, fill: "var(--muted-foreground)" }} tickLine={false} axisLine={false} width={64} allowDecimals={unit !== "COUNT"} />
              </>
            )}
            <Tooltip content={(p) => <ChartTooltip {...(p as object)} unit={unit} />} cursor={{ fill: "var(--hover)" }} />
            <Bar dataKey="value" name={measure === "amount" ? "Amount" : "Count"} fill="var(--chart-1)" radius={horizontal ? [0, 4, 4, 0] : [4, 4, 0, 0]} maxBarSize={36} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      )}
      table={() => sliceTable(title, rows, category)}
    />
  );
}

/** Share of a whole. The legend names each slice with its value and share, so colour never carries identity alone. */
export function DonutChart({ title, description, slices, measure, loading, unavailable, height = 240, category = "Category" }: SliceChartProps & { category?: string }) {
  const unit: Unit = measure === "amount" ? "INR" : "COUNT";
  const rows = foldSlices((slices ?? []).filter((s) => measureOf(s, measure) > 0));
  const total = rows.reduce((n, s) => n + measureOf(s, measure), 0);
  const empty = !loading && !unavailable && total === 0;
  const share = (s: SliceData) => (total > 0 ? `${((measureOf(s, measure) / total) * 100).toFixed(1)}%` : NOT_AVAILABLE);
  return (
    <ChartCard
      title={title}
      description={description}
      loading={loading}
      unavailable={unavailable}
      empty={empty}
      height={height}
      chart={() => (
        <div className="flex size-full flex-col items-center gap-3 sm:flex-row">
          <div className="h-32 w-32 shrink-0 sm:h-full sm:w-1/2">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Tooltip content={(p) => <ChartTooltip {...(p as object)} unit={unit} />} />
                <Pie data={rows.map((s) => ({ name: s.label, value: measureOf(s, measure) }))} dataKey="value" nameKey="name" innerRadius="58%" outerRadius="92%" paddingAngle={rows.length > 1 ? 2 : 0} stroke="var(--surface)" strokeWidth={2} isAnimationActive={false}>
                  {rows.map((s, i) => (
                    <Cell key={s.key} fill={SERIES_COLORS[i]} />
                  ))}
                </Pie>
              </PieChart>
            </ResponsiveContainer>
          </div>
          <ul className="w-full min-w-0 space-y-1 text-xs" aria-hidden="true">
            {rows.map((s, i) => (
              <li key={s.key} className="flex items-center gap-2">
                <span className="inline-block size-2.5 shrink-0 rounded-sm" style={{ background: SERIES_COLORS[i] }} />
                <span className="truncate text-muted-foreground">{s.label}</span>
                <span className="ml-auto font-mono tabular">{formatValue(measureOf(s, measure), unit)}</span>
                <span className="w-12 text-right font-mono tabular text-muted-foreground">{share(s)}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
      table={() => sliceTable(title, rows, category)}
    />
  );
}

/**
 * Stage-to-stage funnel as horizontal bars with the share of the first stage, e.g. leads → estimates → invoices.
 * Steps are counts, so the conversion is shown as text beside each bar.
 */
export function FunnelChart({ title, description, steps, loading, unavailable, height = 200 }: { title: string; description?: ReactNode; steps?: { label: string; count: number | null }[] | null; loading?: boolean; unavailable?: string; height?: number }) {
  const rows = (steps ?? []).filter((s) => s.count !== null) as { label: string; count: number }[];
  const top = rows[0]?.count ?? 0;
  const empty = !loading && !unavailable && top === 0;
  const pct = (n: number) => (top > 0 ? `${((n / top) * 100).toFixed(1)}%` : NOT_AVAILABLE);
  return (
    <ChartCard
      title={title}
      description={description}
      loading={loading}
      unavailable={unavailable}
      empty={empty}
      height={height}
      chart={() => (
        <ol className="flex size-full flex-col justify-center gap-2.5">
          {rows.map((s) => (
            <li key={s.label} className="grid grid-cols-[minmax(0,7rem)_1fr_auto] items-center gap-3 text-sm">
              <span className="truncate text-muted-foreground">{s.label}</span>
              <span className="h-5 overflow-hidden rounded-r-sm bg-secondary">
                <span className="block h-full rounded-r-sm bg-[var(--chart-1)]" style={{ width: top > 0 ? `${Math.max((s.count / top) * 100, s.count > 0 ? 2 : 0)}%` : 0 }} />
              </span>
              <span className="w-24 text-right font-mono text-xs tabular">
                {formatNumber(s.count)} <span className="text-muted-foreground">· {pct(s.count)}</span>
              </span>
            </li>
          ))}
        </ol>
      )}
      table={() => <DataTableView caption={title} headers={["Step", "Count", "Of first step"]} rows={rows.map((s) => [s.label, formatNumber(s.count), pct(s.count)])} />}
    />
  );
}
