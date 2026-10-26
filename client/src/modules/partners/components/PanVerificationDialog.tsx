import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { FormField } from "@/components/forms/FormField";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { Textarea } from "@/components/ui/Input";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { ApiRequestError } from "@/services/api";
import { partnerService } from "../services/partnerService";
import type { Partner } from "../types/partner.types";

/**
 * Records (or withdraws) that someone checked the partner's PAN. Payout TDS uses the rate for a verified PAN only
 * when this is recorded; otherwise the rate without a PAN applies or, if Finance set none, the payout is blocked.
 */
export function PanVerificationDialog({ partner, open, onClose }: { partner: Partner; open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const verify = !partner.panVerified;
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const close = () => {
    setNote("");
    setError(null);
    onClose();
  };
  const confirm = async () => {
    if (verify && !note.trim()) return setError("Say how the PAN was checked.");
    if (note.trim().length > 500) return setError("Use at most 500 characters.");
    setSaving(true);
    try {
      await partnerService.verifyPan(partner.id, verify, note, partner.version);
      toast.success(verify ? "PAN verification recorded." : "PAN verification withdrawn.");
      await Promise.all([queryClient.invalidateQueries({ queryKey: queryKeys.partners.detail(partner.id) }), queryClient.invalidateQueries({ queryKey: queryKeys.payouts.all })]);
      close();
    } catch (e) {
      if (e instanceof ApiRequestError && e.fieldErrors.length > 0) {
        setError(e.fieldErrors[0].message);
      } else {
        toast.error(errorMessage(e));
        close();
        void queryClient.invalidateQueries({ queryKey: queryKeys.partners.detail(partner.id) });
      }
    } finally {
      setSaving(false);
    }
  };
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => !next && !saving && close()}
      title={verify ? "Record PAN verification" : "Withdraw PAN verification?"}
      footer={
        <>
          <Button onClick={close} disabled={saving}>
            Back
          </Button>
          <Button variant={verify ? "primary" : "destructive"} onClick={confirm} loading={saving} loadingText="Saving…">
            {verify ? "Record verification" : "Withdraw"}
          </Button>
        </>
      }
    >
      <div className="space-y-3 text-sm">
        <p>
          {verify
            ? "Confirm you checked this PAN (for example against the income-tax portal or the PAN card). Payout TDS then uses the rate for a verified PAN."
            : "Payouts calculated from now on use the rate without a verified PAN, or are blocked if none is set. Payouts already approved don't change."}
        </p>
        <p className="font-mono">{partner.pan}</p>
        <FormField label={verify ? "How it was checked" : "Reason"} required={verify} error={error ?? undefined} hint="At most 500 characters. Don't paste documents or other identifiers.">
          <Textarea
            rows={3}
            value={note}
            onChange={(e) => {
              setNote(e.target.value);
              setError(null);
            }}
          />
        </FormField>
      </div>
    </Dialog>
  );
}
