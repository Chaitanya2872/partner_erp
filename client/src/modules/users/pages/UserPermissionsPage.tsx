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
import { roleService } from "@/modules/settings/services/roleService";
import type { ModuleInfo } from "@/modules/auth/types/auth.types";
import { userService } from "../services/userService";
import { PermissionMatrix } from "../components/PermissionMatrix";
import { missingScopes, sameDraft, sensitiveAdditions, toDraft, toRequest } from "../lib/permissionMatrix";
import type { PermissionDraft, PermissionPreview } from "../types/user.types";

const LEVELS: Record<string, string> = { READ: "Read", WRITE: "Write", DELETE: "Delete", EXPORT: "Export", APPROVE: "Approve" };

/** "PARTNERS:WRITE" -> "Partners: Write"; "PAYOUTS:MARK_PAID" -> "Payouts: Mark payout paid". */
function describe(authority: string, catalog: ModuleInfo[]): string {
  const [moduleCode, part] = authority.split(":");
  const def = catalog.find((m) => m.code === moduleCode);
  const label = def?.displayName ?? moduleCode;
  if (!part) return label;
  return `${label}: ${LEVELS[part] ?? def?.actions.find((a) => a.code === part)?.displayName ?? part}`;
}

const SCOPE_LABELS: Record<string, string> = { SELF: "Self", TEAM: "Team", ALL: "All" };

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
  /** The server's preview of the pending save, with the sensitive grants it contains. */
  const [confirming, setConfirming] = useState<{ preview: PermissionPreview; sensitive: string[] } | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const [resetting, setResetting] = useState(false);
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

  /** Validates locally, then asks the server what the save would change (nothing is stored) and shows it for confirmation. */
  const save = async () => {
    const missing = missingScopes(draft, catalog);
    setInvalidScopes(missing);
    if (missing.length > 0) {
      setErrors(missing.map((code) => `${modules.labelOf(code)}: choose a data scope.`));
      return;
    }
    setErrors([]);
    setPreviewing(true);
    try {
      const preview = await userService.previewPermissions(id, toRequest(draft, catalog));
      if (!preview.changed) {
        toast.info("Nothing changes: these are the user's current permissions.");
        setDraft(null);
        return;
      }
      setConfirming({ preview, sensitive: sensitiveAdditions(original, draft, catalog) });
    } catch (error) {
      if (error instanceof ApiRequestError && error.fieldErrors.length > 0) {
        setErrors(error.fieldErrors.map((fe) => fe.message));
      } else {
        toast.error(errorMessage(error));
      }
    } finally {
      setPreviewing(false);
    }
  };

  /** Loads the role's default matrix into the editor; nothing is saved until Save. Special actions are cleared. */
  const resetToRole = async () => {
    setResetting(true);
    try {
      const defaults = await roleService.permissions(target.role.id);
      setDraft(toDraft(catalog, defaults.modules, []));
      setErrors([]);
      setInvalidScopes([]);
      toast.info("Role defaults loaded. Review them, then save.");
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setResetting(false);
    }
  };

  const overrides = target.differsFromRole ?? [];
  const actionOverrides = overrides.filter((o) => o.includes(":"));

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
              <Button disabled={saving || resetting} loading={resetting} loadingText="Loading…" onClick={() => void resetToRole()}>
                Reset to role defaults
              </Button>
              <Button disabled={!dirty || saving} onClick={discard}>
                Cancel
              </Button>
              <Button variant="primary" disabled={!dirty} loading={saving || previewing} loadingText={previewing ? "Checking…" : "Saving…"} onClick={() => void save()}>
                Save permissions
              </Button>
            </PageActions>
          ) : undefined
        }
      />

      {target.superAdmin ? (
        <Alert tone="info">A {target.role.displayName} always has full access to every module; these permissions cannot be edited.</Alert>
      ) : readOnly ? (
        <Alert tone="neutral">Read-only view. You cannot change your own permissions, and a Super Admin always has full access.</Alert>
      ) : (
        <Alert tone="info" title="How permissions combine">
          Write includes Read. Delete, Export, Approve and special actions must be granted separately. Self and Team scopes show no records
          until record ownership and teams are introduced; choose All for full visibility.
        </Alert>
      )}

      {!target.superAdmin && overrides.length > 0 ? (
        <Alert tone="neutral" title={`${overrides.length} difference${overrides.length === 1 ? "" : "s"} from the ${target.role.displayName} defaults`}>
          Modules marked &ldquo;Differs from role&rdquo; were changed for this user.
          {actionOverrides.length > 0 ? ` Special actions granted: ${actionOverrides.map((a) => describe(a, catalog)).join(", ")}.` : ""}
        </Alert>
      ) : null}

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
        overrides={overrides}
        onChange={(next) => {
          setDraft(next);
          setInvalidScopes((prev) => prev.filter((code) => missingScopes(next, catalog).includes(code)));
        }}
      />

      <ConfirmDialog
        open={!!confirming}
        title={confirming && confirming.sensitive.length > 0 ? "Grant sensitive permissions?" : "Save permission changes?"}
        confirmLabel="Save permissions"
        busy={saving}
        onCancel={() => setConfirming(null)}
        onConfirm={() => void persist()}
      >
        {confirming ? (
          <>
            <p>What changes for {user.data!.fullName}:</p>
            {confirming.preview.granted.length > 0 ? (
              <div>
                <p className="font-medium">Gains</p>
                <ul>
                  {confirming.preview.granted.map((a) => (
                    <li key={a}>{describe(a, catalog)}</li>
                  ))}
                </ul>
              </div>
            ) : null}
            {confirming.preview.revoked.length > 0 ? (
              <div>
                <p className="font-medium">Loses</p>
                <ul>
                  {confirming.preview.revoked.map((a) => (
                    <li key={a}>{describe(a, catalog)}</li>
                  ))}
                </ul>
              </div>
            ) : null}
            {confirming.preview.scopeChanges.length > 0 ? (
              <div>
                <p className="font-medium">Data scope</p>
                <ul>
                  {confirming.preview.scopeChanges.map((c) => (
                    <li key={c.moduleCode}>
                      {modules.labelOf(c.moduleCode)}: {c.from ? SCOPE_LABELS[c.from] : "none"} → {c.to ? SCOPE_LABELS[c.to] : "none"}
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}
            {confirming.sensitive.length > 0 ? (
              <div>
                <p className="font-medium">Sensitive grants</p>
                <ul>
                  {confirming.sensitive.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              </div>
            ) : null}
            <p className="text-xs text-muted-foreground">They apply to the user&apos;s next request. The change is recorded in the audit log.</p>
          </>
        ) : null}
      </ConfirmDialog>

      <UnsavedChangesGuard when={dirty && !saving} message="Your permission changes have not been saved and will be lost." />
    </PageContainer>
  );
}
