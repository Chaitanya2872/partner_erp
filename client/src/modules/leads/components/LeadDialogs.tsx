import { useState, type ReactNode } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";
import { Textarea } from "@/components/ui/Input";
import { Label } from "@/components/ui/Controls";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { formatPercent } from "@/utils/format";
import { leadService } from "../services/leadService";
import { isClosed, type LeadMeta, type LeadStage, type LeadSummary } from "../types/lead.types";

type LeadRef = Pick<LeadSummary, "id" | "clientName" | "partner" | "stage" | "stageLabel" | "version">;

interface LeadActionsOptions {
  meta: LeadMeta | undefined;
  /** Called after a successful delete (the detail page leaves for the list). */
  onDeleted?: () => void;
}

/**
 * Stage change, acknowledge and delete for a lead, with their confirmations. Pages call the `request…` functions and
 * render `dialogs`. Open stages change at once; reopening a Won or Lost lead asks first and takes a reason (D-04).
 */
export function useLeadActions({ meta, onDeleted }: LeadActionsOptions) {
  const queryClient = useQueryClient();
  const [ackTarget, setAckTarget] = useState<LeadRef | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<LeadRef | null>(null);
  const [reopen, setReopen] = useState<{ lead: LeadRef; stage: LeadStage } | null>(null);
  const [reason, setReason] = useState("");

  // Lead counts and partner pages read leads too.
  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.leads.all }),
      queryClient.invalidateQueries({ queryKey: queryKeys.partners.all }),
    ]);

  const stageMutation = useMutation({
    mutationFn: ({ lead, stage, why }: { lead: LeadRef; stage: LeadStage; why?: string }) => leadService.setStage(lead.id, stage, lead.version, why),
    onSuccess: (updated) => {
      toast.success(`Stage changed to ${updated.stageLabel}.`);
      setReopen(null);
    },
    onError: (error) => toast.error(errorMessage(error)),
    // A failed change may be a stale version: reload so the select shows the stored stage again.
    onSettled: () => refresh(),
  });

  const ackMutation = useMutation({
    mutationFn: (lead: LeadRef) => leadService.acknowledge(lead.id),
    onSuccess: (updated) => {
      toast.success(`Acknowledged. ${formatPercent(updated.lockedFeePercent)} fee rate locked.`);
      setAckTarget(null);
    },
    onError: (error) => {
      toast.error(errorMessage(error));
      setAckTarget(null);
    },
    onSettled: () => refresh(),
  });

  const deleteMutation = useMutation({
    mutationFn: (lead: LeadRef) => leadService.remove(lead.id),
    onSuccess: async () => {
      toast.success("Lead deleted.");
      setDeleteTarget(null);
      onDeleted?.();
      await refresh();
    },
    onError: (error) => {
      toast.error(errorMessage(error));
      setDeleteTarget(null);
    },
  });

  const requestStage = (lead: LeadRef, stage: LeadStage) => {
    if (stage === lead.stage) return;
    if (isClosed(lead.stage)) {
      setReason("");
      setReopen({ lead, stage });
    } else {
      stageMutation.mutate({ lead, stage });
    }
  };

  const stageLabel = (code: LeadStage) => meta?.stages.find((s) => s.code === code)?.label ?? code;
  const ackPartner = ackTarget ? meta?.partners.find((p) => p.id === ackTarget.partner.id) : undefined;

  const dialogs: ReactNode = (
    <>
      <ConfirmDialog
        open={!!ackTarget}
        title="Acknowledge registration?"
        confirmLabel="Acknowledge"
        busy={ackMutation.isPending}
        onCancel={() => setAckTarget(null)}
        onConfirm={() => ackTarget && ackMutation.mutate(ackTarget)}
      >
        {ackTarget ? (
          <p>
            Acknowledge “{ackTarget.clientName}” for {ackTarget.partner.name}?{" "}
            {ackPartner ? (
              <>
                The {formatPercent(ackPartner.feePercent)} ({ackPartner.tierName}) fee rate locks now
              </>
            ) : (
              <>The partner&apos;s current fee rate locks now</>
            )}{" "}
            and {meta?.protectionDays ?? 90}-day protection starts.
          </p>
        ) : null}
      </ConfirmDialog>

      <ConfirmDialog
        open={!!reopen}
        title="Reopen lead?"
        confirmLabel="Reopen"
        busy={stageMutation.isPending}
        onCancel={() => setReopen(null)}
        onConfirm={() => reopen && stageMutation.mutate({ lead: reopen.lead, stage: reopen.stage, why: reason.trim() })}
      >
        {reopen ? (
          <>
            <p>
              “{reopen.lead.clientName}” moves from {reopen.lead.stageLabel} to {stageLabel(reopen.stage)}. The change is kept in the lead&apos;s
              history.
            </p>
            <div className="space-y-1.5 pt-1">
              <Label htmlFor="lead-reopen-reason">Reason (optional)</Label>
              <Textarea id="lead-reopen-reason" rows={2} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
            </div>
          </>
        ) : null}
      </ConfirmDialog>

      <ConfirmDialog
        open={!!deleteTarget}
        title="Delete lead?"
        confirmLabel="Delete"
        danger
        busy={deleteMutation.isPending}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget)}
      >
        {deleteTarget ? (
          <p>
            “{deleteTarget.clientName}” ({deleteTarget.partner.name}) is removed from lists and stops protecting its phone number. A lead with
            estimates, invoices or payments can&apos;t be deleted; mark it Lost instead.
          </p>
        ) : null}
      </ConfirmDialog>
    </>
  );

  return {
    requestStage,
    requestAcknowledge: (lead: LeadRef) => setAckTarget(lead),
    requestDelete: (lead: LeadRef) => setDeleteTarget(lead),
    /** The lead whose stage is being saved, so its select can show it is busy. */
    stagePendingId: stageMutation.isPending ? stageMutation.variables?.lead.id : undefined,
    dialogs,
  };
}
