import { useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import { toast } from "sonner";
import { useModules } from "@/hooks/useModules";
import { useAuth } from "@/hooks/useAuth";
import { LoadingSpinner } from "@/components/common/LoadingSpinner";
import { ErrorState } from "@/components/common/EmptyState";
import { ConfirmDialog } from "@/components/modals/ConfirmDialog";
import { ApiRequestError } from "@/services/api";
import { userService } from "../services/userService";
import { PermissionMatrix } from "../components/PermissionMatrix";
import { missingScopes, sameDraft, sensitiveAdditions, toDraft, toRequest } from "../lib/permissionMatrix";
import type { PermissionDraft } from "../types/user.types";

export function UserPermissionsPage() {
  const { id = "" } = useParams();
  const queryClient = useQueryClient();
  const { refreshUser } = useAuth();
  const modules = useModules();
  const user = useQuery({ queryKey: ["users", id], queryFn: () => userService.get(id) });
  const permissions = useQuery({ queryKey: ["users", id, "permissions"], queryFn: () => userService.permissions(id) });

  const original = useMemo<PermissionDraft | null>(
    () => (modules.data && permissions.data ? toDraft(modules.data, permissions.data.modules, permissions.data.actions) : null),
    [modules.data, permissions.data],
  );
  /** Unsaved edits; null means "as stored on the server". */
  const [edits, setDraft] = useState<PermissionDraft | null>(null);
  const draft = edits ?? original;
  const [confirming, setConfirming] = useState<string[] | null>(null);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<string[]>([]);
  const [invalidScopes, setInvalidScopes] = useState<string[]>([]);

  if (modules.isPending || user.isPending || permissions.isPending || !draft || !original) {
    if (modules.isError || user.isError || permissions.isError) {
      return <ErrorState error={modules.error ?? user.error ?? permissions.error} />;
    }
    return <LoadingSpinner label="Loading permissions…" />;
  }

  const catalog = modules.data!;
  const target = permissions.data!;
  const readOnly = !target.editable;
  const dirty = !sameDraft(draft, original, catalog);

  const persist = async () => {
    setSaving(true);
    setErrors([]);
    try {
      await userService.replacePermissions(id, toRequest(draft, catalog));
      toast.success("Permissions saved. They apply to the user's next request.");
      setConfirming(null);
      setDraft(null);
      await queryClient.invalidateQueries({ queryKey: ["users", id] });
      await refreshUser().catch(() => undefined);
    } catch (error) {
      setConfirming(null);
      if (error instanceof ApiRequestError && error.fieldErrors.length > 0) {
        setErrors(error.fieldErrors.map((fe) => fe.message));
      } else {
        toast.error(error instanceof Error ? error.message : "Saving failed.");
      }
    } finally {
      setSaving(false);
    }
  };

  const save = () => {
    const missing = missingScopes(draft, catalog);
    setInvalidScopes(missing);
    if (missing.length > 0) {
      setErrors(missing.map((code) => `${modules.labelOf(code)}: choose a data scope.`));
      return;
    }
    const sensitive = sensitiveAdditions(original, draft, catalog);
    if (sensitive.length > 0) setConfirming(sensitive);
    else void persist();
  };

  return (
    <>
      <Link to="/users" className="lnk small" style={{ display: "inline-flex", gap: 4, alignItems: "center", marginBottom: 8 }}>
        <ArrowLeft size={13} aria-hidden="true" /> Users
      </Link>
      <div className="top">
        <div>
          <h1>Permissions</h1>
          <p>
            {user.data!.fullName} · <span className="pill">{target.role.displayName}</span>
          </p>
        </div>
        {!readOnly ? (
          <div className="acts">
            <button type="button" className="btn" disabled={!dirty || saving} onClick={() => { setDraft(null); setErrors([]); setInvalidScopes([]); }}>
              Cancel
            </button>
            <button type="button" className="btn primary" disabled={!dirty || saving} onClick={save}>
              {saving ? "Saving…" : "Save permissions"}
            </button>
          </div>
        ) : null}
      </div>

      {target.superAdmin ? (
        <div className="notice info" style={{ marginBottom: 12 }}>
          A {target.role.displayName} always has full access to every module; these permissions cannot be edited.
        </div>
      ) : readOnly ? (
        <div className="notice" style={{ marginBottom: 12 }}>
          Read-only view. Only a Super Admin can change permissions, and nobody can change their own.
        </div>
      ) : (
        <div className="notice info" style={{ marginBottom: 12 }}>
          Write includes Read. Delete, Export, Approve and special actions must be granted separately. Self and Team scopes
          show no records until record ownership and teams are introduced; choose All for full visibility.
        </div>
      )}

      {errors.length > 0 ? (
        <div className="notice bad" role="alert" style={{ marginBottom: 12 }}>
          <ul style={{ margin: 0, paddingLeft: 18 }}>
            {errors.map((e) => (
              <li key={e}>{e}</li>
            ))}
          </ul>
        </div>
      ) : null}

      <PermissionMatrix
        catalog={catalog}
        draft={draft}
        roleCode={target.role.code}
        readOnly={readOnly}
        invalidScopes={invalidScopes}
        onChange={(next) => {
          setDraft(next);
          setInvalidScopes((prev) => prev.filter((code) => missingScopes(next, catalog).includes(code)));
        }}
      />

      <ConfirmDialog
        open={!!confirming}
        title="Grant sensitive permissions?"
        confirmLabel="Save permissions"
        busy={saving}
        onCancel={() => setConfirming(null)}
        onConfirm={() => void persist()}
      >
        <p>You are granting {user.data!.fullName}:</p>
        <ul>
          {(confirming ?? []).map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
        <p className="muted small">The change is recorded in the audit log.</p>
      </ConfirmDialog>
    </>
  );
}
