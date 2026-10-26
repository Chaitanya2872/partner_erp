import { useNavigate, useParams } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";
import { Alert } from "@/components/ui/Alert";
import { ErrorState, LoadingState, PermissionDenied } from "@/components/feedback/States";
import { PageContainer, PageHeader } from "@/components/layout/Page";
import { queryKeys } from "@/lib/queryKeys";
import { invoiceService } from "../services/invoiceService";
import { useInvoice, useInvoiceMeta } from "../hooks/useInvoices";
import { InvoiceForm } from "../components/InvoiceForm";
import { InvoiceStatusBadge } from "../components/InvoiceParts";
import type { InvoiceInput } from "../types/invoice.types";

/**
 * `/invoices/new` (direct invoice) and `/invoices/:id/edit` (drafts only). Leads and products come from restricted
 * pickers (Invoices write), so Leads read and Products read are not needed (Phase 9 decision 5).
 */
export function InvoiceFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { canWrite, canPerform } = useAuth();
  const editing = !!id;
  const meta = useInvoiceMeta();
  const invoice = useInvoice(id);

  const back = editing ? { to: `/invoices/${id}`, label: invoice.data?.invoiceNumber ?? "Draft invoice" } : { to: "/invoices", label: "Invoices" };
  const title = editing ? "Edit draft invoice" : "New invoice";

  // The route guard checks read access; saving needs write (the server enforces it either way).
  if (!canWrite("INVOICES")) return <PermissionDenied />;
  if (meta.isPending || (editing && invoice.isPending)) return <LoadingState label="Loading…" />;
  const error = meta.error ?? invoice.error;
  if (error || !meta.data) {
    return (
      <PageContainer>
        <PageHeader title={title} back={back} />
        <ErrorState error={error} onRetry={() => void Promise.all([meta.refetch(), editing ? invoice.refetch() : null])} />
      </PageContainer>
    );
  }

  const inv = invoice.data;
  if (inv && inv.status !== "DRAFT") {
    return (
      <PageContainer>
        <PageHeader title={title} back={back} />
        <Alert tone="warning" title={`This invoice is ${inv.statusLabel.toLowerCase()}`}>
          Issued and cancelled invoices can&apos;t be edited: their lines, prices, tax, customer details and number are fixed.
        </Alert>
      </PageContainer>
    );
  }

  const submit = async (input: InvoiceInput) => {
    const saved = editing ? await invoiceService.update(id, input) : await invoiceService.create(input);
    toast.success(editing ? "Draft invoice saved." : "Draft invoice created.");
    await queryClient.invalidateQueries({ queryKey: queryKeys.invoices.all });
    navigate(`/invoices/${saved.id}`, { replace: true });
  };

  return (
    <PageContainer>
      <PageHeader
        title={title}
        back={back}
        description={inv?.estimate ? `From ${inv.estimate.estimateNumber}` : "Drafts get an invoice number when they are issued."}
        badge={inv ? <InvoiceStatusBadge status={inv.status} paymentStatus={inv.paymentStatus} /> : undefined}
      />
      <InvoiceForm meta={meta.data} invoice={inv} internal={canPerform("PRODUCTS", "VIEW_INTERNAL_B2C")} onSubmit={submit} onCancel={() => navigate(back.to)} />
    </PageContainer>
  );
}
