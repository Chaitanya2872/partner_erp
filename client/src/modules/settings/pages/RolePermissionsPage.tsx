import { useMemo, useState } from "react";
import { useParams } from "react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useModules } from "@/hooks/useModules";
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
import { formatNumber } from "@/utils/format";
import { PermissionMatrix } from "@/modules/users/components/PermissionMatrix";
import { missingScopes, sameDraft, toDraft, toRequest } from "@/modules/users/lib/permissionMatrix";
import type { PermissionDraft } from "@/modules/users/types/user.types";
import { roleService } from "../services/roleService";

/**
 * A role's default permissions: the module matrix new accounts start with, and what an account moved to the role is
 * reset to. Changing it never alters existing accounts. Special actions are always granted per user. Only a Super
 * Admin may change defaults (the server decides `editable`), and the Super Admin role has fixed, complete access.
 */
export function RolePermissionsPage() {
  const { id = "" } = useParams();
  const queryClient = useQueryClient();
  const modules = useModules();
  const role = useQuery({ queryKey: queryKeys.rolePermissions(id), queryFn: ({ signal }) => roleService.permissions(id, signal) });

  const original = useMemo<PermissionDraft | null>(
    () => (modules.data && role.data ? toDraft(modules.data, role.data.modules, []) : null),
    [modules.data, role.data],
  );
  const [edits, setDraft] = useState<PermissionDraft | null>(null);
  const draft = edits ?? original;
  const [confirming, setConfirming] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<string[]>([]);
  const [invalidScopes, setInvalidScopes] = useState<string[]>([]);

  const dirty = !!(draft && original && modules.data && !sameDraft(draft, original, modules.data));

  if (modules.isPending || role.isPending || !draft || !original) {
    if (modules.isError || role.isError) {
      return (
        <PageContainer>
          <PageHeader title="Default permissions" back={{ to: "/roles", label: "Roles" }} />
          <ErrorState error={modules.error ?? role.error} onRetry={() => void role.refetch()} />
        </PageContainer>
      );
    }
    return <LoadingState label="Loading default permissions…" />;
  }

  const catalog = modules.data!;
  const target = role.data!;
  const readOnly = !target.editable;

  const discard = () => {
    setDraft(null);
    setErrors([]);
    setInvalidScopes([]);
  };

  const review = () => {
    const missing = missingScopes(draft, catalog);
    setInvalidScopes(missing);
    if (missing.length > 0) {
      setErrors(missing.map((code) => `${modules.labelOf(code)}: choose a data scope.`));
      return;
    }
    setErrors([]);
    setConfirming(true);
  };

  const persist = async () => {
    setSaving(true);
    try {
      await roleService.updatePermissions(id, toRequest(draft, catalog).modules, target.version);
      toast.success("Default permissions saved. Existing accounts are unchanged.");
      setConfirming(false);
      setDraft(null);
      await queryClient.invalidateQueries({ queryKey: queryKeys.roles });
    } catch (error) {
      setConfirming(false);
      if (error instanceof ApiRequestError && error.fieldErrors.length > 0) {
        setErrors(error.fieldErrors.map((fe) => fe.message));
      } else if (error instanceof ApiRequestError && error.status === 409) {
        setErrors(["Someone else changed these defaults. Cancel to see the current ones, then try again."]);
        await queryClient.invalidateQueries({ queryKey: queryKeys.roles });
      } else {
        toast.error(errorMessage(error));
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageContainer>
      <PageHeader
        title="Default permissions"
        documentTitle={`Default permissions · ${target.displayName}`}
        back={{ to: "/roles", label: "Roles" }}
        description={
          <span className="inline-flex flex-wrap items-center gap-2">
            <span className="font-medium text-foreground">{target.displayName}</span>
            <Badge tone="outline">{formatNumber(target.userCount)} accounts</Badge>
            {dirty ? <Badge tone="warning">Unsaved changes</Badge> : null}
          </span>
        }
        actions={
          !readOnly ? (
            <PageActions>
              <Button disabled={!dirty || saving} onClick={discard}>
                Cancel
              </Button>
              <Button variant="primary" disabled={!dirty} loading={saving} loadingText="Saving…" onClick={review}>
                Save defaults
              </Button>
            </PageActions>
          ) : undefined
        }
      />

      {target.superAdmin ? (
        <Alert tone="info">A {target.displayName} always has full access to every module; there are no defaults to set.</Alert>
      ) : (
        <Alert tone={readOnly ? "neutral" : "info"} title="Defaults apply to new accounts only">
          New accounts with this role, and accounts moved to it, start with these permissions. Existing accounts keep theirs; change them one
          by one under Users. Special actions are never part of a default and are granted per user.
          {readOnly ? " Only a Super Admin can change defaults." : ""}
        </Alert>
      )}

      {errors.length > 0 ? (
        <Alert tone="danger" title="The defaults were not saved">
          <ul>
            {errors.map((e) => (
              <li key={e}>{e}</li>
            ))}
          </ul>
        </Alert>
      ) : null}

      {target.superAdmin ? null : (
        <PermissionMatrix
          catalog={catalog}
          draft={draft}
          roleCode={target.code}
          readOnly={readOnly}
          hideActions
          invalidScopes={invalidScopes}
          onChange={(next) => {
            setDraft(next);
            setInvalidScopes((prev) => prev.filter((code) => missingScopes(next, catalog).includes(code)));
          }}
        />
      )}

      <ConfirmDialog
        open={confirming}
        title={`Change the ${target.displayName} defaults?`}
        confirmLabel="Save defaults"
        busy={saving}
        onCancel={() => setConfirming(false)}
        onConfirm={() => void persist()}
      >
        <p>
          Accounts created with this role, or moved to it, from now on start with the new defaults. The {formatNumber(target.userCount)}{" "}
          existing accounts keep their current permissions.
        </p>
        <p className="text-xs text-muted-foreground">The change is recorded in the audit log.</p>
      </ConfirmDialog>

      <UnsavedChangesGuard when={dirty && !saving} message="Your changes to the default permissions have not been saved and will be lost." />
    </PageContainer>
  );
}
