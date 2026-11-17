import { useState, type ReactNode } from "react";
import { Alert } from "@/components/ui/Alert";
import { Dialog } from "@/components/ui/Dialog";
import { Button } from "@/components/ui/Button";
import { Textarea } from "@/components/ui/Input";
import { FormField } from "@/components/forms/FormField";
import { formatDateTime } from "@/utils/format";

export const MIN_ARCHIVE_REASON = 10;
export const MAX_ARCHIVE_REASON = 500;

interface ArchiveDialogProps {
  open: boolean;
  title: string;
  /** "Archive lead" / "Archive estimate". */
  confirmLabel: string;
  busy?: boolean;
  /** The server's message for the `reason` field, shown under the box. */
  serverError?: string | null;
  /** What is being archived and what it affects. */
  children?: ReactNode;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}

/**
 * Archiving takes a written reason (10 to 500 characters), which is kept with the record and in its audit history.
 * Nothing is deleted for good: the record moves to the Archive, where it can be restored.
 */
export function ArchiveDialog(props: ArchiveDialogProps) {
  // Mounted only while open, so every opening starts with an empty reason.
  return props.open ? <ArchiveDialogBody {...props} /> : null;
}

function ArchiveDialogBody({ open, title, confirmLabel, busy, serverError, children, onConfirm, onCancel }: ArchiveDialogProps) {
  const [reason, setReason] = useState("");
  const [touched, setTouched] = useState(false);

  const trimmed = reason.trim();
  const tooShort = trimmed.length < MIN_ARCHIVE_REASON;
  const localError = touched && tooShort ? `Say why it is being archived (at least ${MIN_ARCHIVE_REASON} characters).` : null;
  const submit = () => {
    setTouched(true);
    if (!tooShort) onConfirm(trimmed);
  };

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
          <Button variant="destructive" onClick={submit} loading={busy} loadingText="Working…">
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className="space-y-3 text-sm">
        {children}
        <p className="text-muted-foreground">It moves to the Archive and can be restored; nothing is permanently deleted.</p>
        <FormField
          label="Reason for archiving"
          required
          error={serverError ?? localError ?? undefined}
          hint={`${trimmed.length} / ${MAX_ARCHIVE_REASON} characters (at least ${MIN_ARCHIVE_REASON}). Kept with the record and in its history.`}
        >
          <Textarea
            rows={3}
            maxLength={MAX_ARCHIVE_REASON}
            value={reason}
            onChange={(e) => {
              setReason(e.target.value);
              setTouched(true);
            }}
          />
        </FormField>
      </div>
    </Dialog>
  );
}

interface ArchiveStampProps {
  deletedAt: string;
  deletedByName: string | null;
  deleteReason: string;
}

/** Who archived a record, when and why: the banner of the read-only archive views. */
export function ArchiveStamp({ deletedAt, deletedByName, deleteReason }: ArchiveStampProps) {
  return (
    <Alert tone="warning" title={`Archived ${formatDateTime(deletedAt)}${deletedByName ? ` by ${deletedByName}` : ""}`}>
      <span className="whitespace-pre-wrap">Reason: {deleteReason}</span>
    </Alert>
  );
}

/** The reason in a table cell: one truncated line, the full text on hover. */
export function ArchiveReasonCell({ reason }: { reason: string }) {
  return (
    <span className="block max-w-64 truncate" title={reason}>
      {reason}
    </span>
  );
}

/** A label and value pair for the read-only archive views. */
export function ArchiveField({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="font-medium">{value || "—"}</dd>
    </div>
  );
}
