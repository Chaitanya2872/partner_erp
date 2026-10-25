import type { ReactNode } from "react";
import { Dialog } from "@/components/ui/Dialog";
import { Button } from "@/components/ui/Button";

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  children: ReactNode;
  confirmLabel?: string;
  /** Destructive actions get the red button. */
  danger?: boolean;
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/** Asks before consequential actions. Cancel is first in the tab order so Enter never confirms by accident. */
export function ConfirmDialog({ open, title, children, confirmLabel = "Confirm", danger, busy, onConfirm, onCancel }: ConfirmDialogProps) {
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => !next && !busy && onCancel()}
      title={title}
      footer={
        <>
          <Button onClick={onCancel} disabled={busy}>
            Cancel
          </Button>
          <Button variant={danger ? "destructive" : "primary"} onClick={onConfirm} loading={busy} loadingText="Working…">
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className="space-y-2 text-sm [&_ul]:list-disc [&_ul]:space-y-0.5 [&_ul]:pl-5">{children}</div>
    </Dialog>
  );
}
