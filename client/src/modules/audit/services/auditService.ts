import { apiDownload, apiGet, apiPage } from "@/services/api";
import type { AuditActor, AuditFilters, AuditLog, AuditModule } from "../types/audit.types";

export const auditService = {
  list: (filters: AuditFilters & { page: number; size: number }, signal?: AbortSignal) =>
    apiPage<AuditLog>("/audit-logs", { ...filters }, signal),
  catalog: (signal?: AbortSignal) => apiGet<AuditModule[]>("/audit-logs/catalog", undefined, signal),
  actors: (q: string, signal?: AbortSignal) => apiGet<AuditActor[]>("/audit-logs/actors", { q }, signal),
  /** CSV of every matching event (Audit logs export). The export itself is recorded in the audit log. */
  exportCsv: (filters: AuditFilters) => apiDownload("/audit-logs/export", "iotiq-audit-log.csv", { ...filters }),
};
