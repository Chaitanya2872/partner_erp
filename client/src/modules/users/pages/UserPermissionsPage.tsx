import { useMemo, useState } from "react";
import { useParams } from "react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useModules } from "@/hooks/useModules";
import { useAuth } from "@/hooks/useAuth";
import { ErrorState, LoadingState } from "@/components/feedback/States";
import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";
import { UnsavedChangesGuard } from "@/components/forms/UnsavedChangesGuard";
import { PageActions, PageContainer, PageHeader } from "@/components/layout/Page";
import { Alert } from "@/components/ui/Alert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { ApiRequestError } from "@/services/api";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { userService } from "../services/userService";
import { PermissionMatrix } from "../components/PermissionMatrix";
import { missingScopes, sameDraft, sensitiveAdditions, toDraft, toRequest } from "../lib/permissionMatrix";
import type { PermissionDraft } from "../types/user.types";

export function UserPermissionsPage() {
  const { id = "" } = useParams();
  const queryClient = useQueryClient();
  const { refreshUser } = useAuth();
  const modules = useModules();
  const user = useQuery({ queryKey: queryKeys.users.detail(id), queryFn: ({ signal }) => userService.get(id, signal) });
  const permissions = useQuery({ queryKey: queryKeys.users.permissions(id), queryFn: ({ signal }) => userService.permissions(id, signal) });

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

  const dirty = !!(draft && original && modules.data && !sameDraft(draft, original, modules.data));

  if (modules.isPending || user.isPending || permissions.isPending || !draft || !original) {
    if (modules.isError || user.isError || permissions.isError) {
      return (
        <PageContainer>
          <PageHeader title="Permissions" back={{ to: "/users", label: "Users" }} />
          <ErrorState error={modules.error ?? user.error ?? permissions.error} onRetry={() => void Promise.all([user.refetch(), permissions.refetch()])} />
        </PageContainer>
      );
    }
    return <LoadingState label="Loading permissions…" />;
  }

  const catalog = modules.data!;
  const target = permissions.data!;
  const readOnly = !target.editable;

  const discard = () => {
    setDraft(null);
    setErrors([]);
    setInvalidScopes([]);
  };

  const persist = async () => {
    setSaving(true);
    setErrors([]);
    try {
      await userService.replacePermissions(id, toRequest(draft, catalog));
      toast.success("Permissions saved. They apply to the user's next request.");
      setConfirming(null);
      setDraft(null);
      await queryClient.invalidateQueries({ queryKey: queryKeys.users.detail(id) });
      await refreshUser().catch(() => undefined);
    } catch (error) {
      setConfirming(null);
      if (error instanceof ApiRequestError && error.fieldErrors.length > 0) {
        setErrors(error.fieldErrors.map((fe) => fe.message));
      } else {
        toast.error(errorMessage(error));
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
    <PageContainer>
      <PageHeader
        title="Permissions"
        documentTitle={`Permissions · ${user.data!.fullName}`}
        back={{ to: "/users", label: "Users" }}
        description={
          <span className="inline-flex flex-wrap items-center gap-2">
            <span className="font-medium text-foreground">{user.data!.fullName}</span>
            <Badge tone="outline">{target.role.displayName}</Badge>
            {dirty ? <Badge tone="warning">Unsaved changes</Badge> : null}
          </span>
        }
        actions={
          !readOnly ? (
            <PageActions>
              <Button disabled={!dirty || saving} onClick={discard}>
                Cancel
              </Button>
              <Button variant="primary" disabled={!dirty} loading={saving} loadingText="Saving…" onClick={save}>
                Save permissions
              </Button>
            </PageActions>
          ) : undefined
        }
      />

      {target.superAdmin ? (
        <Alert tone="info">A {target.role.displayName} always has full access to every module; these permissions cannot be edited.</Alert>
      ) : readOnly ? (
        <Alert tone="neutral">Read-only view. Only a Super Admin can change permissions, and nobody can change their own.</Alert>
      ) : (
        <Alert tone="info" title="How permissions combine">
          Write includes Read. Delete, Export, Approve and special actions must be granted separately. Self and Team scopes show no records
          until record ownership and teams are introduced; choose All for full visibility.
        </Alert>
      )}

      {errors.length > 0 ? (
        <Alert tone="danger" title="The permissions were not saved">
          <ul>
            {errors.map((e) => (
              <li key={e}>{e}</li>
            ))}
          </ul>
        </Alert>
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
        <p className="text-xs text-muted-foreground">The change is recorded in the audit log.</p>
      </ConfirmDialog>

      <UnsavedChangesGuard when={dirty && !saving} message="Your permission changes have not been saved and will be lost." />
    </PageContainer>
  );
}
