import { useMemo, useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Download, Eye, FilterX } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";
import { useDebounce } from "@/hooks/useDebounce";
import { DataTable, type ColumnDef } from "@/components/tables/DataTable";
import { EmptyState } from "@/components/feedback/States";
import { PageActions, PageContainer, PageHeader, Toolbar } from "@/components/layout/Page";
import { DescriptionList, DetailRow } from "@/components/data/DataDisplay";
import { FilterDate } from "@/components/forms/inputs";
import { SearchInput } from "@/components/forms/SearchInput";
import { SearchableSelect } from "@/components/forms/SearchableSelect";
import { Dialog } from "@/components/ui/Dialog";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Input";
import { formatDateTime, humanizeCode, shortId } from "@/utils/format";
import { saveTextFile } from "@/utils/download";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { auditService } from "../services/auditService";
import type { AuditActor, AuditLog } from "../types/audit.types";

const SECURITY_ACTIONS = new Set(["LOGIN_FAILED", "ACCOUNT_LOCKED", "REFRESH_TOKEN_REUSED", "PASSWORD_RESET", "ACCOUNT_UNLOCKED"]);

/** Local calendar day boundaries as instants for the API's from/to filters. */
function dayStart(date: string): string | undefined {
  return date ? new Date(`${date}T00:00:00`).toISOString() : undefined;
}
function dayEnd(date: string): string | undefined {
  return date ? new Date(`${date}T23:59:59.999`).toISOString() : undefined;
}

/**
 * Read-only audit trail (Audit logs read). The module and action lists come from the server, the search covers the
 * action, target, acting user and request id (never the recorded values), and the CSV export needs Audit logs export
 * and is itself recorded. Secrets are redacted before they are stored and B2C pricing is hidden by the server.
 */
export function AuditLogsPage() {
  const { canPerform } = useAuth();
  const [module, setModule] = useState("");
  const [action, setAction] = useState("");
  const [userId, setUserId] = useState("");
  const [actor, setActor] = useState<AuditActor | null>(null);
  const [actorQuery, setActorQuery] = useState("");
  const [search, setSearch] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(50);
  const [selected, setSelected] = useState<AuditLog | null>(null);
  const [exporting, setExporting] = useState(false);
  const q = useDebounce(search.trim());
  const actorQ = useDebounce(actorQuery.trim());
  const canExport = canPerform("AUDIT_LOGS", "EXPORT");

  const criteria = { module, action, userId, q, from: dayStart(from), to: dayEnd(to) };
  const filters = { ...criteria, page, size };
  const logs = useQuery({
    queryKey: queryKeys.auditLogs.list(filters),
    queryFn: ({ signal }) => auditService.list(filters, signal),
    placeholderData: keepPreviousData,
  });
  const catalog = useQuery({ queryKey: queryKeys.auditLogs.catalog, queryFn: ({ signal }) => auditService.catalog(signal), staleTime: Infinity });
  const actors = useQuery({
    queryKey: queryKeys.auditLogs.actors(actorQ),
    queryFn: ({ signal }) => auditService.actors(actorQ, signal),
    placeholderData: keepPreviousData,
  });

  const actions = useMemo(() => {
    const modules = catalog.data ?? [];
    const list = module ? (modules.find((m) => m.code === module)?.actions ?? []) : modules.flatMap((m) => m.actions);
    return [...new Set(list)].sort((a, b) => humanizeCode(a).localeCompare(humanizeCode(b)));
  }, [catalog.data, module]);
  const actorOptions = useMemo(() => {
    const found = actors.data ?? [];
    const all = actor && !found.some((a) => a.id === actor.id) ? [actor, ...found] : found;
    return all.map((a) => ({ value: a.id, label: a.fullName, description: a.email }));
  }, [actors.data, actor]);

  const set = (setter: (value: string) => void) => (value: string) => {
    setter(value);
    setPage(0);
  };
  const filtered = !!(module || action || userId || q || from || to);
  const clear = () => {
    setModule("");
    setAction("");
    setUserId("");
    setActor(null);
    setSearch("");
    setFrom("");
    setTo("");
    setPage(0);
  };

  const exportCsv = async () => {
    setExporting(true);
    try {
      const { text, filename } = await auditService.exportCsv(criteria);
      saveTextFile(text, filename);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setExporting(false);
    }
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
      <PageHeader
        title="Audit logs"
        description="Security, administration and financial events, newest first. Passwords, tokens and full PAN or bank numbers are never recorded."
        actions={
          canExport ? (
            <PageActions>
              <Button onClick={exportCsv} loading={exporting} loadingText="Exporting…" disabled={!logs.data || logs.data.meta.totalElements === 0}>
                <Download aria-hidden="true" /> Export CSV
              </Button>
            </PageActions>
          ) : undefined
        }
      />
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
            <SearchInput
              placeholder="Search action, user or request id"
              aria-label="Search audit logs"
              value={search}
              maxLength={100}
              onValueChange={set(setSearch)}
            />
            <Select
              aria-label="Filter by module"
              value={module}
              wrapperClassName="sm:w-40"
              onChange={(e) => {
                setModule(e.target.value);
                setAction("");
                setPage(0);
              }}
            >
              <option value="">All modules</option>
              {(catalog.data ?? []).map((m) => (
                <option key={m.code} value={m.code}>
                  {m.label}
                </option>
              ))}
            </Select>
            <Select aria-label="Filter by action" value={action} wrapperClassName="sm:w-52" onChange={(e) => set(setAction)(e.target.value)}>
              <option value="">All actions</option>
              {actions.map((a) => (
                <option key={a} value={a}>
                  {humanizeCode(a)}
                </option>
              ))}
            </Select>
            <SearchableSelect
              aria-label="Filter by acting user"
              options={actorOptions}
              value={userId}
              onChange={(value) => {
                setUserId(value);
                setActor(actorOptionsSource(actors.data, actor, value));
                setPage(0);
              }}
              onQueryChange={setActorQuery}
              clearLabel="All users"
              placeholder="All users"
              searchPlaceholder="Search users"
              loading={actors.isPending}
              className="sm:w-52"
            />
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
              <FilterDate label="From" id="audit-from" value={from} max={to || undefined} onChange={(e) => set(setFrom)(e.target.value)} aria-label="From date" />
              <FilterDate label="To" id="audit-to" value={to} min={from || undefined} onChange={(e) => set(setTo)(e.target.value)} aria-label="To date" />
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

/** The chosen actor, kept so its label stays visible after the search text changes. */
function actorOptionsSource(found: AuditActor[] | undefined, current: AuditActor | null, id: string): AuditActor | null {
  if (!id) return null;
  return (found ?? []).find((a) => a.id === id) ?? (current?.id === id ? current : null);
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
