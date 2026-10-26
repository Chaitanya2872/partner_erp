import type { PeriodPreset, PeriodView } from "@/modules/dashboard/types/dashboard.types";

/** Phase 12 report payloads (`/api/v1/reports`). Columns are only those the caller may see. */
export type ColumnType = "TEXT" | "CODE" | "DATE" | "MONEY" | "NUMBER" | "PERCENT" | "BOOLEAN" | "MASKED";

export interface ReportColumn {
  key: string;
  label: string;
  type: ColumnType;
  sortable: boolean;
  total: boolean;
}

export interface ReportOption {
  value: string;
  label: string;
}

export interface ReportInfo {
  code: string;
  title: string;
  description: string;
  module: string;
  available: boolean;
  /** Why the report can't be run (no access, or a SELF/TEAM scope), when unavailable. */
  reason: string | null;
  canExport: boolean;
  dateBasis: string;
  columns: ReportColumn[];
  statusColumn: string | null;
  statuses: ReportOption[];
  partnerFilter: boolean;
  searchColumns: string[];
  defaultSort: string;
  defaultDescending: boolean;
}

export type ReportValue = string | number | boolean | null;

export interface ReportRow {
  id: string;
  /** Detail page path, or null. */
  link: string | null;
  [key: string]: ReportValue;
}

export interface ReportResult {
  code: string;
  title: string;
  period: PeriodView;
  columns: ReportColumn[];
  rows: ReportRow[];
  /** Over every row matching the filters, not only the page. */
  totals: Record<string, number | string>;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  sort: string;
}

export interface ReportFilters {
  preset: PeriodPreset;
  from?: string;
  to?: string;
  status?: string;
  partnerId?: string;
  q?: string;
  sort?: string;
  page?: number;
  size?: number;
}

export interface PartnerOption {
  id: string;
  name: string;
  code: string | null;
}
