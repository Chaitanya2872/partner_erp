import { useState } from "react";
import { Link } from "react-router";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Pencil, Plus, TrendingUp } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useDebounce } from "@/hooks/useDebounce";
import { DataTable, type ColumnDef } from "@/components/tables/DataTable";
import { toSortParam, type SortState } from "@/components/tables/sorting";
import { EmptyState } from "@/components/feedback/States";
import { PageActions, PageContainer, PageHeader, Toolbar } from "@/components/layout/Page";
import { SearchInput } from "@/components/forms/SearchInput";
import { StatusBadge } from "@/components/data/StatusBadge";
import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Input";
import { formatCurrency, formatNumber } from "@/utils/format";
import { queryKeys } from "@/lib/queryKeys";
import { partnerService } from "../services/partnerService";
import { usePartnerMeta } from "../hooks/usePartners";
import { TierReviewDialog } from "../components/TierReviewDialog";
import { TierCell } from "../components/TierCell";
import type { AgreementStatus, PartnerCategory, PartnerStatus, PartnerSummary } from "../types/partner.types";

export function PartnersPage() {
  const { canWrite, canPerform } = useAuth();
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<PartnerStatus | "">("");
  const [category, setCategory] = useState<PartnerCategory | "">("");
  const [tier, setTier] = useState("");
  const [agreementStatus, setAgreementStatus] = useState<AgreementStatus | "">("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState<SortState | null>({ key: "name", direction: "asc" });
  const [reviewing, setReviewing] = useState(false);
  const q = useDebounce(search.trim());

  const meta = usePartnerMeta();
  const filters = { q, status, category, tier, agreementStatus, page, size, sort: toSortParam(sort) };
  const partners = useQuery({
    queryKey: queryKeys.partners.list(filters),
    queryFn: ({ signal }) => partnerService.list(filters, signal),
    placeholderData: keepPreviousData,
  });

  const canEdit = canWrite("PARTNERS");
  const canApprove = canPerform("PARTNERS", "APPROVE");
  /** Every filter change starts again from the first page. */
  const set =
    <T,>(setter: (value: T) => void) =>
    (value: T) => {
      setter(value);
      setPage(0);
    };

  const columns: ColumnDef<PartnerSummary>[] = [
    {
      id: "name",
      header: "Partner",
      sortKey: "name",
      cell: (p) => (
        <div className="min-w-48 max-w-64">
          <Link
            to={`/partners/${p.id}`}
            className="block truncate font-medium text-accent-ink underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
          >
            {p.name}
          </Link>
          {p.contactName || p.phone ? (
            <div className="truncate text-xs text-muted-foreground">{[p.contactName, p.phone].filter(Boolean).join(" · ")}</div>
          ) : null}
        </div>
      ),
    },
    { id: "category", header: "Category", sortKey: "category", cell: (p) => <span className="whitespace-nowrap">{p.categoryLabel}</span> },
    { id: "city", header: "City", sortKey: "city", cell: (p) => p.city ?? <span className="text-muted-foreground">—</span> },
    { id: "status", header: "Status", sortKey: "status", cell: (p) => <StatusBadge domain="partner" status={p.status} /> },
    { id: "tier", header: "Tier", cell: (p) => <TierCell tier={p.tier} calculated={p.calculatedTier} programme={p.strategicProgramme} grace={p.downgradeGrace} /> },
    {
      id: "revenue",
      header: "Revenue (12 months)",
      numeric: true,
      cell: (p) => <span className="whitespace-nowrap">{formatCurrency(p.revenue12m)}</span>,
    },
    { id: "agreement", header: "Agreement", sortKey: "agreementStatus", cell: (p) => <StatusBadge domain="agreement" status={p.agreementStatus} /> },
    { id: "leads", header: "Leads", numeric: true, cell: (p) => formatNumber(p.leadCount) },
  ];

  const filtered = !!q || !!status || !!category || !!tier || !!agreementStatus;
  const clearFilters = () => { setSearch(""); setStatus(""); setCategory(""); setTier(""); setAgreementStatus(""); setPage(0); };

  return (
    <PageContainer>
      <PageHeader
        title="Partners"
        description="Referral partners, their tiers and agreements."
        actions={
          canEdit || canApprove ? (
            <PageActions>
              {canApprove ? (
                <Button onClick={() => setReviewing(true)}>
                  <TrendingUp aria-hidden="true" /> Run tier review
                </Button>
              ) : null}
              {canEdit ? (
                <Button asChild variant="primary">
                  <Link to="/partners/new">
                    <Plus aria-hidden="true" /> New partner
                  </Link>
                </Button>
              ) : null}
            </PageActions>
          ) : undefined
        }
      />

      <DataTable
        caption="Partners"
        itemLabel="partners"
        columns={columns}
        rows={partners.data?.content}
        rowKey={(p) => p.id}
        loading={partners.isPending}
        fetching={partners.isFetching && !partners.isPending}
        error={partners.error}
        onRetry={() => partners.refetch()}
        sort={sort}
        onSortChange={set(setSort)}
        page={partners.data?.meta}
        onPageChange={setPage}
        pageSizeOptions={[20, 50, 100]}
        onPageSizeChange={set(setSize)}
        empty={
          <EmptyState
            title={filtered ? "No partners match" : "No partners yet"}
            onClearFilters={filtered ? clearFilters : undefined}
            action={
              !filtered && canEdit ? (
                <Button asChild variant="primary">
                  <Link to="/partners/new">
                    <Plus aria-hidden="true" /> New partner
                  </Link>
                </Button>
              ) : undefined
            }
          >
            {filtered ? undefined : "Add your first referral partner to start tracking leads and fees."}
          </EmptyState>
        }
        // The partner name already links to the detail page, so the only row action is Edit.
        rowActions={
          canEdit
            ? (p) => (
                <Button asChild size="sm" variant="outline">
                  <Link to={`/partners/${p.id}/edit`} aria-label={`Edit ${p.name}`}>
                    <Pencil aria-hidden="true" /> Edit
                  </Link>
                </Button>
              )
            : undefined
        }
        toolbar={
          <Toolbar label="Partner filters">
            <SearchInput
              placeholder="Search partners"
              aria-label="Search partners"
              value={search}
              onValueChange={set(setSearch)}
            />
            <Select aria-label="Filter by status" value={status} wrapperClassName="sm:w-40" onChange={(e) => set(setStatus)(e.target.value as PartnerStatus | "")}>
              <option value="">All statuses</option>
              {meta.data?.statuses.map((s) => (
                <option key={s.code} value={s.code}>
                  {s.label}
                </option>
              ))}
            </Select>
            <Select
              aria-label="Filter by category"
              value={category}
              wrapperClassName="sm:w-48"
              onChange={(e) => set(setCategory)(e.target.value as PartnerCategory | "")}
            >
              <option value="">All categories</option>
              {meta.data?.categories.map((c) => (
                <option key={c.code} value={c.code}>
                  {c.label}
                </option>
              ))}
            </Select>
            <Select aria-label="Filter by tier" value={tier} wrapperClassName="sm:w-36" onChange={(e) => set(setTier)(e.target.value)}>
              <option value="">All tiers</option>
              {meta.data?.tiers.map((t) => (
                <option key={t.level} value={String(t.level)}>
                  {t.name}
                </option>
              ))}
            </Select>
            <Select
              aria-label="Filter by agreement"
              value={agreementStatus}
              wrapperClassName="sm:w-40"
              onChange={(e) => set(setAgreementStatus)(e.target.value as AgreementStatus | "")}
            >
              <option value="">All agreements</option>
              {meta.data?.agreementStatuses.map((a) => (
                <option key={a.code} value={a.code}>
                  {a.label}
                </option>
              ))}
            </Select>
          </Toolbar>
        }
      />

      {canApprove ? <TierReviewDialog open={reviewing} onOpenChange={setReviewing} /> : null}
    </PageContainer>
  );
}

