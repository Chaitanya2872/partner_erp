import { useState } from "react";
import { Link } from "react-router";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useDebounce } from "@/hooks/useDebounce";
import { toSortParam, type SortState } from "@/components/tables/sorting";
import { EmptyState } from "@/components/feedback/States";
import { PageActions, PageContainer, PageHeader, Toolbar } from "@/components/layout/Page";
import { SearchInput } from "@/components/forms/SearchInput";
import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Input";
import { queryKeys } from "@/lib/queryKeys";
import { leadService } from "../services/leadService";
import { useLeadMeta } from "../hooks/useLeads";
import { useLeadActions } from "../components/LeadDialogs";
import { LeadTable } from "../components/LeadTable";
import type { LeadStage } from "../types/lead.types";

export function LeadsPage() {
  const { canWrite } = useAuth();
  const [search, setSearch] = useState("");
  const [stage, setStage] = useState<LeadStage | "">("");
  const [partnerId, setPartnerId] = useState("");
  const [conflict, setConflict] = useState("");
  const [slaBreach, setSlaBreach] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState<SortState | null>(null);
  const q = useDebounce(search.trim());

  const meta = useLeadMeta();
  const actions = useLeadActions({ meta: meta.data });
  const filters = { q, stage, partnerId, conflict, slaBreach, page, size, sort: toSortParam(sort) };
  const leads = useQuery({
    queryKey: queryKeys.leads.list(filters),
    queryFn: ({ signal }) => leadService.list(filters, signal),
    placeholderData: keepPreviousData,
  });

  const canEdit = canWrite("LEADS");
  /** Every filter change starts again from the first page. */
  const set =
    <T,>(setter: (value: T) => void) =>
    (value: T) => {
      setter(value);
      setPage(0);
    };
  const filtered = !!q || !!stage || !!partnerId || !!conflict || !!slaBreach;
  const noPartners = meta.data?.partners.length === 0;

  const newLead = canEdit ? (
    noPartners ? (
      <Button asChild>
        <Link to="/partners/new">Add a partner first</Link>
      </Button>
    ) : (
      <Button asChild variant="primary">
        <Link to="/leads/new">
          <Plus aria-hidden="true" /> New lead
        </Link>
      </Button>
    )
  ) : undefined;

  return (
    <PageContainer>
      <PageHeader
        title="Leads"
        description="Deal registration, protection period and SLA clocks."
        actions={newLead ? <PageActions>{newLead}</PageActions> : undefined}
      />

      <LeadTable
        caption="Leads"
        rows={leads.data?.content}
        meta={meta.data}
        actions={actions}
        loading={leads.isPending}
        fetching={leads.isFetching && !leads.isPending}
        error={leads.error}
        onRetry={() => leads.refetch()}
        sort={sort}
        onSortChange={set(setSort)}
        page={leads.data?.meta}
        onPageChange={setPage}
        onPageSizeChange={set(setSize)}
        empty={
          <EmptyState title={filtered ? "No leads match" : "No leads yet"} action={!filtered ? newLead : undefined}>
            {filtered ? "Try other filters." : "Register the first lead a partner refers to start its protection and SLA clocks."}
          </EmptyState>
        }
        toolbar={
          <Toolbar label="Lead filters">
            <SearchInput placeholder="Search leads" aria-label="Search leads" value={search} onValueChange={set(setSearch)} />
            <Select aria-label="Filter by stage" value={stage} wrapperClassName="sm:w-40" onChange={(e) => set(setStage)(e.target.value as LeadStage | "")}>
              <option value="">All stages</option>
              {meta.data?.stages.map((s) => (
                <option key={s.code} value={s.code}>
                  {s.label}
                </option>
              ))}
            </Select>
            <Select aria-label="Filter by partner" value={partnerId} wrapperClassName="sm:w-52" onChange={(e) => set(setPartnerId)(e.target.value)}>
              <option value="">All partners</option>
              {meta.data?.partners.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </Select>
            <Select aria-label="Filter by SLA" value={slaBreach} wrapperClassName="sm:w-40" onChange={(e) => set(setSlaBreach)(e.target.value)}>
              <option value="">Any SLA</option>
              <option value="true">SLA late</option>
              <option value="false">SLA on track</option>
            </Select>
            <Select aria-label="Filter by duplicates" value={conflict} wrapperClassName="sm:w-48" onChange={(e) => set(setConflict)(e.target.value)}>
              <option value="">All leads</option>
              <option value="true">Possible duplicates</option>
              <option value="false">No duplicate warning</option>
            </Select>
          </Toolbar>
        }
      />

      {actions.dialogs}
    </PageContainer>
  );
}
