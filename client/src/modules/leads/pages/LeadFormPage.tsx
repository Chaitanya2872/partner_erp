import { useNavigate, useParams, useSearchParams } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";
import { ErrorState, LoadingState, PermissionDenied } from "@/components/feedback/States";
import { PageContainer, PageHeader } from "@/components/layout/Page";
import { queryKeys } from "@/lib/queryKeys";
import { leadService } from "../services/leadService";
import { useLead, useLeadMeta } from "../hooks/useLeads";
import { LeadForm } from "../components/LeadForm";
import type { LeadInput } from "../types/lead.types";

/** `/leads/new` (optionally `?partnerId=`) and `/leads/:id/edit`. */
export function LeadFormPage() {
  const { id } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { canWrite, canPerform } = useAuth();
  const meta = useLeadMeta();
  const lead = useLead(id);
  const editing = !!id;
  const back = editing ? { to: `/leads/${id}`, label: lead.data?.clientName ?? "Lead" } : { to: "/leads", label: "Leads" };
  const title = editing ? "Edit lead" : "New lead";

  // The route guard checks read access; editing needs write (the server enforces it either way).
  if (!canWrite("LEADS")) return <PermissionDenied />;
  if (meta.isPending || (editing && lead.isPending)) return <LoadingState label="Loading…" />;
  if (meta.isError || (editing && lead.isError)) {
    return (
      <PageContainer width="narrow">
        <PageHeader title={title} back={back} />
        <ErrorState error={meta.error ?? lead.error} onRetry={() => void Promise.all([meta.refetch(), editing ? lead.refetch() : null])} />
      </PageContainer>
    );
  }

  const submit = async (input: LeadInput) => {
    const saved = editing ? await leadService.update(id, input) : await leadService.create(input);
    // BR-L6: a protected phone is a warning only; the lead is saved and flagged.
    if (saved.conflict && saved.conflictPartnerName) toast.warning(`Saved. Same phone is already protected for ${saved.conflictPartnerName}.`);
    else toast.success(editing ? "Lead updated." : "Lead created.");
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.leads.all }),
      queryClient.invalidateQueries({ queryKey: queryKeys.partners.all }),
    ]);
    navigate(`/leads/${saved.id}`, { replace: true });
  };

  return (
    <PageContainer width="narrow">
      <PageHeader title={title} back={back} documentTitle={editing ? `Edit ${lead.data?.clientName}` : title} />
      <LeadForm
        meta={meta.data}
        lead={lead.data}
        defaultPartnerId={searchParams.get("partnerId") ?? undefined}
        canApprove={canPerform("LEADS", "APPROVE")}
        onSubmit={submit}
        onCancel={() => navigate(back.to)}
      />
    </PageContainer>
  );
}
