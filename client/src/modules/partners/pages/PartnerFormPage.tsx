import { useNavigate, useParams } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";
import { ErrorState, LoadingState, PermissionDenied } from "@/components/feedback/States";
import { PageContainer, PageHeader } from "@/components/layout/Page";
import { queryKeys } from "@/lib/queryKeys";
import { partnerService } from "../services/partnerService";
import { usePartner, usePartnerMeta } from "../hooks/usePartners";
import { PartnerForm } from "../components/PartnerForm";
import type { PartnerInput } from "../types/partner.types";

/** `/partners/new` and `/partners/:id/edit`. */
export function PartnerFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { canWrite, canPerform } = useAuth();
  const meta = usePartnerMeta();
  const partner = usePartner(id);
  const editing = !!id;
  const back = editing ? { to: `/partners/${id}`, label: partner.data?.name ?? "Partner" } : { to: "/partners", label: "Partners" };
  const title = editing ? "Edit partner" : "New partner";

  // The route guard checks read access; editing needs write (the server enforces it either way).
  if (!canWrite("PARTNERS")) return <PermissionDenied />;
  if (meta.isPending || (editing && partner.isPending)) return <LoadingState label="Loading…" />;
  if (meta.isError || (editing && partner.isError)) {
    return (
      <PageContainer width="narrow">
        <PageHeader title={title} back={back} />
        <ErrorState error={meta.error ?? partner.error} onRetry={() => void Promise.all([meta.refetch(), editing ? partner.refetch() : null])} />
      </PageContainer>
    );
  }

  const submit = async (input: PartnerInput) => {
    const saved = editing ? await partnerService.update(id, input) : await partnerService.create(input);
    toast.success(editing ? "Partner updated." : "Partner created.");
    await queryClient.invalidateQueries({ queryKey: queryKeys.partners.all });
    navigate(`/partners/${saved.id}`, { replace: true });
  };

  return (
    <PageContainer width="narrow">
      <PageHeader title={title} back={back} documentTitle={editing ? `Edit ${partner.data?.name}` : title} />
      <PartnerForm
        meta={meta.data}
        partner={partner.data}
        canApprove={canPerform("PARTNERS", "APPROVE")}
        onSubmit={submit}
        onCancel={() => navigate(back.to)}
      />
    </PageContainer>
  );
}
