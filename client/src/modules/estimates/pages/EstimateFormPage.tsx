import { useNavigate, useParams, useSearchParams } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";
import { Alert } from "@/components/ui/Alert";
import { ErrorState, LoadingState, PermissionDenied } from "@/components/feedback/States";
import { PageContainer, PageHeader } from "@/components/layout/Page";
import { queryKeys } from "@/lib/queryKeys";
import { estimateService } from "../services/estimateService";
import { useEstimate, useEstimateMeta, useLinkedLead } from "../hooks/useEstimates";
import { EstimateForm, type LeadChoice } from "../components/EstimateForm";
import { EstimateStatusBadge } from "../components/EstimateStatusBadge";
import type { EstimateInput } from "../types/estimate.types";

/**
 * `/estimates/new` (optionally `?leadId=`) and `/estimates/:id/edit`. Leads come from the restricted lead picker
 * (Estimates write), so Leads read is not needed (Phase 8 decision 4).
 */
export function EstimateFormPage() {
  const { id } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { canRead, canWrite, canPerform } = useAuth();
  const editing = !!id;
  const linkedLeadId = editing ? undefined : (searchParams.get("leadId") ?? undefined);

  const meta = useEstimateMeta();
  const estimate = useEstimate(id);
  const linked = useLinkedLead(linkedLeadId);
  const linkedLead: LeadChoice | undefined = linked.data?.[0]
    ? { id: linked.data[0].id, leadNumber: linked.data[0].leadNumber, clientName: linked.data[0].clientName, partnerName: linked.data[0].partner.name }
    : undefined;

  const back = editing
    ? { to: `/estimates/${id}`, label: estimate.data?.estimateNumber ?? "Estimate" }
    : linkedLeadId && canRead("LEADS")
      ? { to: `/leads/${linkedLeadId}`, label: linkedLead?.clientName ?? "Lead" }
      : { to: "/estimates", label: "Estimates" };
  const title = editing ? "Edit estimate" : "New estimate";

  // The route guard checks read access; saving needs write (the server enforces it either way).
  if (!canWrite("ESTIMATES")) return <PermissionDenied />;
  if (meta.isPending || (editing && estimate.isPending) || (!!linkedLeadId && linked.isPending)) return <LoadingState label="Loading…" />;
  const error = meta.error ?? estimate.error ?? linked.error;
  if (error || !meta.data) {
    return (
      <PageContainer>
        <PageHeader title={title} back={back} />
        <ErrorState error={error} onRetry={() => void Promise.all([meta.refetch(), editing ? estimate.refetch() : null, linkedLeadId ? linked.refetch() : null])} />
      </PageContainer>
    );
  }

  const e = estimate.data;
  if (e?.status === "INVOICED") {
    return (
      <PageContainer>
        <PageHeader title={title} back={back} />
        <Alert tone="warning" title="This estimate is invoiced">
          Invoiced estimates can't be edited.
        </Alert>
      </PageContainer>
    );
  }

  const submit = async (input: EstimateInput) => {
    const saved = editing ? await estimateService.update(id, input) : await estimateService.create(input);
    toast.success(editing ? `${saved.estimateNumber} updated.` : `${saved.estimateNumber} created.`);
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.estimates.all }),
      queryClient.invalidateQueries({ queryKey: queryKeys.leads.all }),
    ]);
    navigate(`/estimates/${saved.id}`, { replace: true });
  };

  return (
    <PageContainer>
      <PageHeader
        title={title}
        back={back}
        documentTitle={editing ? `Edit ${e?.estimateNumber}` : title}
        description={e?.estimateNumber}
        badge={e ? <EstimateStatusBadge status={e.status} expired={e.expired} /> : undefined}
      />
      {linkedLeadId && !linkedLead ? (
        <Alert tone="warning" title="This lead can't get a new estimate">
          It may be lost, deleted or outside your access. Reopen the lead first, or choose another one below.
        </Alert>
      ) : null}
      <EstimateForm
        meta={meta.data}
        estimate={e}
        initialLead={linkedLead}
        internal={canPerform("PRODUCTS", "VIEW_INTERNAL_B2C")}
        onSubmit={submit}
        onCancel={() => navigate(back.to)}
      />
    </PageContainer>
  );
}
