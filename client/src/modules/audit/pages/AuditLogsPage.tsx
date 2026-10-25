import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { apiPage } from "@/services/api";
import { DataTable, Pager, type Column } from "@/components/tables/DataTable";
import { EmptyState, ErrorState } from "@/components/common/EmptyState";
import { LoadingSpinner } from "@/components/common/LoadingSpinner";
import { Dialog } from "@/components/ui/Dialog";
import { formatDateTime } from "@/utils/formatDate";

interface AuditLog {
  id: string;
  createdAt: string;
  userId: string | null;
  actorName: string | null;
  actorEmail: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  oldValues: unknown;
  newValues: unknown;
  ipAddress: string | null;
  requestId: string | null;
}

const ACTIONS = [
  "LOGIN_SUCCEEDED",
  "LOGIN_FAILED",
  "ACCOUNT_LOCKED",
  "LOGOUT",
  "REFRESH_TOKEN_REUSED",
  "PASSWORD_CHANGED",
  "SUPER_ADMIN_BOOTSTRAPPED",
  "USER_CREATED",
  "USER_UPDATED",
  "USER_ROLE_CHANGED",
  "USER_STATUS_CHANGED",
  "PERMISSIONS_CHANGED",
  "ROLE_UPDATED",
];

const humanize = (code: string) => code.charAt(0) + code.slice(1).toLowerCase().replaceAll("_", " ");

/** Read-only audit trail (AUDIT_LOGS:READ). Secrets are redacted by the server before storage. */
export function AuditLogsPage() {
  const [action, setAction] = useState("");
  const [entityType, setEntityType] = useState("");
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<AuditLog | null>(null);

  const logs = useQuery({
    queryKey: ["audit-logs", { action, entityType, page }],
    queryFn: () => apiPage<AuditLog>("/audit-logs", { action, entityType, page, size: 50 }),
    placeholderData: keepPreviousData,
  });

  const columns: Column<AuditLog>[] = [
    { key: "when", header: "When", cell: (e) => <span className="small">{formatDateTime(e.createdAt)}</span> },
    { key: "action", header: "Action", cell: (e) => <span className="pill">{humanize(e.action)}</span> },
    {
      key: "actor",
      header: "Actor",
      cell: (e) => (e.actorName ? <span title={e.actorEmail ?? undefined}>{e.actorName}</span> : <span className="muted">System / anonymous</span>),
    },
    { key: "entity", header: "Target", cell: (e) => <span className="mono">{e.entityType}{e.entityId ? ` · ${e.entityId.slice(0, 8)}` : ""}</span> },
    { key: "ip", header: "IP", cell: (e) => <span className="mono">{e.ipAddress ?? "—"}</span> },
    {
      key: "details",
      header: <span className="sr-only">Details</span>,
      className: "ra",
      cell: (e) => (
        <button type="button" className="btn sm" onClick={() => setSelected(e)}>
          Details
        </button>
      ),
    },
  ];

  return (
    <>
      <div className="top">
        <div>
          <h1>Audit logs</h1>
          <p>Security and administration events, newest first.</p>
        </div>
      </div>
      <div className="tools">
        <select aria-label="Filter by action" value={action} onChange={(e) => { setAction(e.target.value); setPage(0); }}>
          <option value="">All actions</option>
          {ACTIONS.map((a) => (
            <option key={a} value={a}>
              {humanize(a)}
            </option>
          ))}
        </select>
        <select aria-label="Filter by target" value={entityType} onChange={(e) => { setEntityType(e.target.value); setPage(0); }}>
          <option value="">All targets</option>
          <option value="USER">Users</option>
          <option value="ROLE">Roles</option>
        </select>
      </div>
      {logs.isPending ? (
        <LoadingSpinner label="Loading audit trail…" />
      ) : logs.isError ? (
        <ErrorState error={logs.error} onRetry={() => logs.refetch()} />
      ) : (
        <>
          <DataTable caption="Audit logs" columns={columns} rows={logs.data.content} rowKey={(e) => e.id} empty={<EmptyState title="No events recorded" />} />
          <Pager meta={logs.data.meta} onPage={setPage} />
        </>
      )}
      <Dialog open={!!selected} onOpenChange={(open) => !open && setSelected(null)} title={selected ? humanize(selected.action) : ""} wide>
        {selected ? (
          <div style={{ display: "grid", gap: 10 }}>
            <div className="small muted">
              {formatDateTime(selected.createdAt)} · {selected.actorEmail ?? "system"} · request {selected.requestId ?? "—"}
            </div>
            <div className="fgrid">
              <div className="fld">
                <label>Before</label>
                <pre className="mono card" style={{ margin: 0, overflow: "auto", maxHeight: 360 }}>
                  {selected.oldValues ? JSON.stringify(selected.oldValues, null, 2) : "—"}
                </pre>
              </div>
              <div className="fld">
                <label>After</label>
                <pre className="mono card" style={{ margin: 0, overflow: "auto", maxHeight: 360 }}>
                  {selected.newValues ? JSON.stringify(selected.newValues, null, 2) : "—"}
                </pre>
              </div>
            </div>
          </div>
        ) : null}
      </Dialog>
    </>
  );
}
