import { apiDownload, apiGet } from "@/services/api";
import type { PartnerOption, ReportFilters, ReportInfo, ReportResult } from "../types/report.types";

/** Empty values are dropped, and custom dates only travel with the CUSTOM preset. */
const params = (filters: Partial<ReportFilters>) => {
  const custom = filters.preset === "CUSTOM";
  return Object.fromEntries(
    Object.entries({ ...filters, from: custom ? filters.from : undefined, to: custom ? filters.to : undefined }).filter(
      ([, v]) => v !== "" && v !== undefined && v !== null,
    ),
  );
};

type ExportFilters = Omit<ReportFilters, "page" | "size">;

export const reportService = {
  /** Reports read: every report with whether the caller can run and export it. */
  catalog: (signal?: AbortSignal) => apiGet<ReportInfo[]>("/reports", undefined, signal),
  run: (code: string, filters: ReportFilters, signal?: AbortSignal) => apiGet<ReportResult>(`/reports/${code}`, params(filters), signal),
  /** Reports export plus the module's own export; at most 10,000 rows. */
  exportCsv: (code: string, filters: ExportFilters) => apiDownload(`/reports/${code}/export`, `iotiq-report-${code}.csv`, params(filters)),
  /** Partner filter options (needs Partners read with the ALL scope): name and code only, at most 20. */
  partnerOptions: (q: string, signal?: AbortSignal) => apiGet<PartnerOption[]>("/reports/partner-options", q ? { q } : undefined, signal),
};
