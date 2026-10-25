import { useMemo, useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Eye, FilterX } from "lucide-react";
import { apiPage } from "@/services/api";
import { useAuth } from "@/hooks/useAuth";
import { DataTable, type ColumnDef } from "@/components/tables/DataTable";
import { EmptyState } from "@/components/feedback/States";
import { PageContainer, PageHeader, Toolbar } from "@/components/layout/Page";
import { DescriptionList, DetailRow } from "@/components/data/DataDisplay";
import { DateInput } from "@/components/forms/inputs";
import { SearchableSelect } from "@/components/forms/SearchableSelect";
import { Dialog } from "@/components/ui/Dialog";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Input";
import { formatDateTime, humanizeCode, shortId } from "@/utils/format";
import { queryKeys } from "@/lib/queryKeys";
import { userService } from "@/modules/users/services/userService";

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

/** Actions the server records (AuditAction). */
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
  "PARTNER_CREATED",
  "PARTNER_UPDATED",
  "PARTNER_STATUS_CHANGED",
  "PARTNER_TIER_REVIEWED",
  "LEAD_CREATED",
  "LEAD_UPDATED",
  "LEAD_STAGE_CHANGED",
  "LEAD_ACKNOWLEDGED",
  "LEAD_DELETED",
  "PRODUCT_CREATED",
  "PRODUCT_UPDATED",
  "PRODUCT_STATUS_CHANGED",
  "PRODUCT_DELETED",
  "PRODUCT_IMPORTED",
  "PRODUCT_IMPORT_COMPLETED",
];

const SECURITY_ACTIONS = new Set(["LOGIN_FAILED", "ACCOUNT_LOCKED", "REFRESH_TOKEN_REUSED"]);

/** Local calendar day boundaries as instants for the API's from/to filters. */
function dayStart(date: string): string | undefined {
  return date ? new Date(`${date}T00:00:00`).toISOString() : undefined;
}
function dayEnd(date: string): string | undefined {
  return date ? new Date(`${date}T23:59:59.999`).toISOString() : undefined;
}

/** Read-only audit trail (AUDIT_LOGS:READ). Secrets are redacted by the server before they are stored. */
export function AuditLogsPage() {
  const { canRead } = useAuth();
  const [action, setAction] = useState("");
  const [entityType, setEntityType] = useState("");
  const [userId, setUserId] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(50);
  const [selected, setSelected] = useState<AuditLog | null>(null);
  const canPickActor = canRead("USER_MANAGEMENT");

  const filters = { action, entityType, userId, from: dayStart(from), to: dayEnd(to), page, size };
  const logs = useQuery({
    queryKey: queryKeys.auditLogs.list(filters),
    queryFn: ({ signal }) => apiPage<AuditLog>("/audit-logs", filters, signal),
    placeholderData: keepPreviousData,
  });

  const actors = useQuery({
    queryKey: queryKeys.users.list({ picker: true }),
    queryFn: ({ signal }) => userService.list({ page: 0, size: 200, sort: "fullName,asc" }, signal),
    enabled: canPickActor,
    staleTime: 5 * 60_000,
  });
  const actorOptions = useMemo(
    () => (actors.data?.content ?? []).map((u) => ({ value: u.id, label: u.fullName, description: u.email })),
    [actors.data],
  );

  const set = (setter: (value: string) => void) => (value: string) => {
    setter(value);
    setPage(0);
  };
  const filtered = !!(action || entityType || userId || from || to);
  const clear = () => {
    setAction("");
    setEntityType("");
    setUserId("");
    setFrom("");
    setTo("");
    setPage(0);
  };

  const columns: ColumnDef<AuditLog>[] = [
    { id: "when", header: "When", cell: (e) => <span className="whitespace-nowrap">{formatDateTime(e.createdAt)}</span> },
    {
      id: "action",
      header: "Action",
      cell: (e) => <Badge tone={SECURITY_ACTIONS.has(e.action) ? "warning" : "neutral"}>{humanizeCode(e.action)}</Badge>,
    },
    {
      id: "actor",
      header: "Acting user",
      cell: (e) =>
        e.actorName ? (
          <div className="min-w-36">
            <div className="font-medium">{e.actorName}</div>
            <div className="text-xs text-muted-foreground">{e.actorEmail}</div>
          </div>
        ) : (
          <span className="text-muted-foreground">System / anonymous</span>
        ),
    },
    {
      id: "entity",
      header: "Target",
      cell: (e) => (
        <span className="whitespace-nowrap">
          {humanizeCode(e.entityType)}
          {e.entityId ? (
            <span className="ml-1.5 font-mono text-xs text-muted-foreground" title={e.entityId}>
              {shortId(e.entityId)}
            </span>
          ) : null}
        </span>
      ),
    },
    { id: "ip", header: "IP address", cell: (e) => <span className="font-mono text-xs">{e.ipAddress ?? "—"}</span> },
    { id: "request", header: "Request", defaultHidden: true, cell: (e) => <span className="font-mono text-xs">{e.requestId ?? "—"}</span> },
  ];

  return (
    <PageContainer>
      <PageHeader title="Audit logs" description="Security and administration events, newest first. Passwords and tokens are never recorded." />
      <DataTable
        caption="Audit logs"
        itemLabel="events"
        columns={columns}
        rows={logs.data?.content}
        rowKey={(e) => e.id}
        loading={logs.isPending}
        fetching={logs.isFetching && !logs.isPending}
        error={logs.error}
        onRetry={() => logs.refetch()}
        page={logs.data?.meta}
        onPageChange={setPage}
        pageSizeOptions={[25, 50, 100]}
        onPageSizeChange={(next) => {
          setSize(next);
          setPage(0);
        }}
        empty={
          <EmptyState
            title={filtered ? "No events match these filters" : "No events recorded"}
            action={
              filtered ? (
                <Button size="sm" onClick={clear}>
                  Clear filters
                </Button>
              ) : undefined
            }
          />
        }
        rowActions={(e) => (
          <Button size="sm" variant="ghost" onClick={() => setSelected(e)} aria-label={`Details of ${humanizeCode(e.action)} at ${formatDateTime(e.createdAt)}`}>
            <Eye aria-hidden="true" /> Details
          </Button>
        )}
        toolbar={
          <Toolbar label="Audit log filters" className="sm:items-end">
            <Select aria-label="Filter by action" value={action} wrapperClassName="sm:w-52" onChange={(e) => set(setAction)(e.target.value)}>
              <option value="">All actions</option>
              {ACTIONS.map((a) => (
                <option key={a} value={a}>
                  {humanizeCode(a)}
                </option>
              ))}
            </Select>
            <Select aria-label="Filter by target" value={entityType} wrapperClassName="sm:w-36" onChange={(e) => set(setEntityType)(e.target.value)}>
              <option value="">All targets</option>
              <option value="USER">Users</option>
              <option value="ROLE">Roles</option>
              <option value="PARTNER">Partners</option>
              <option value="LEAD">Leads</option>
              <option value="PRODUCT">Products</option>
            </Select>
            {canPickActor ? (
              <SearchableSelect
                aria-label="Filter by acting user"
                options={actorOptions}
                value={userId}
                onChange={set(setUserId)}
                clearLabel="All users"
                placeholder="All users"
                searchPlaceholder="Search users"
                loading={actors.isPending}
                className="sm:w-52"
              />
            ) : null}
            <div className="flex items-center gap-2">
              <DateInput id="audit-from" value={from} max={to || undefined} onChange={(e) => set(setFrom)(e.target.value)} className="sm:w-40" aria-label="From date" />
              <span className="text-muted-foreground" aria-hidden="true">
                –
              </span>
              <DateInput id="audit-to" value={to} min={from || undefined} onChange={(e) => set(setTo)(e.target.value)} className="sm:w-40" aria-label="To date" />
            </div>
            {filtered ? (
              <Button variant="ghost" onClick={clear}>
                <FilterX aria-hidden="true" /> Clear
              </Button>
            ) : null}
          </Toolbar>
        }
      />

      <Dialog open={!!selected} onOpenChange={(open) => !open && setSelected(null)} title={selected ? humanizeCode(selected.action) : "Event"} wide>
        {selected ? (
          <div className="space-y-4">
            <DescriptionList columns={2}>
              <DetailRow label="When">{formatDateTime(selected.createdAt)}</DetailRow>
              <DetailRow label="Acting user">{selected.actorName ? `${selected.actorName} (${selected.actorEmail})` : "System / anonymous"}</DetailRow>
              <DetailRow label="Target">{humanizeCode(selected.entityType)}</DetailRow>
              <DetailRow label="Target id" mono>
                {selected.entityId ?? "—"}
              </DetailRow>
              <DetailRow label="IP address" mono>
                {selected.ipAddress ?? "—"}
              </DetailRow>
              <DetailRow label="Request id" mono>
                {selected.requestId ?? "—"}
              </DetailRow>
            </DescriptionList>
            <div className="grid gap-3 md:grid-cols-2">
              <JsonBlock title="Before" value={selected.oldValues} />
              <JsonBlock title="After" value={selected.newValues} />
            </div>
          </div>
        ) : null}
      </Dialog>
    </PageContainer>
  );
}

function JsonBlock({ title, value }: { title: string; value: unknown }) {
  return (
    <section aria-label={title} className="min-w-0">
      <h3 className="mb-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">{title}</h3>
      <pre className="max-h-80 overflow-auto rounded-md border border-border bg-surface-sunken p-3 font-mono text-xs leading-relaxed">
        {value ? JSON.stringify(value, null, 2) : "—"}
      </pre>
    </section>
  );
}
