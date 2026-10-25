import type { ReactNode } from "react";
import { Dialog } from "@/components/ui/Dialog";

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  children: ReactNode;
  confirmLabel?: string;
  danger?: boolean;
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmDialog({ open, title, children, confirmLabel = "Confirm", danger, busy, onConfirm, onCancel }: ConfirmDialogProps) {
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => !next && onCancel()}
      title={title}
      footer={
        <>
          <button type="button" className="btn" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
          <button type="button" className={danger ? "btn danger" : "btn primary"} onClick={onConfirm} disabled={busy}>
            {busy ? "Working…" : confirmLabel}
          </button>
        </>
      }
    >
      {children}
    </Dialog>
  );
}
