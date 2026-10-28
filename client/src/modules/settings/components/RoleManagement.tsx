import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router";
import { Lock, Pencil, ShieldCheck } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";
import { Dialog } from "@/components/ui/Dialog";
import { Button } from "@/components/ui/Button";
import { Input, Textarea } from "@/components/ui/Input";
import { Alert } from "@/components/ui/Alert";
import { FormError, FormField } from "@/components/forms/FormField";
import { DataTable, type ColumnDef } from "@/components/tables/DataTable";
import { ApiRequestError } from "@/services/api";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { formatNumber } from "@/utils/format";
import { roleService, type Role } from "../services/roleService";
import { useRoles } from "../hooks/useRoles";

/**
 * Role Management. Only display names and descriptions are editable (Super Admin); the internal codes are fixed
 * and are what authorization uses, so renaming never changes anyone's access.
 */
export function RoleManagement() {
  const { canWrite, refreshUser } = useAuth();
  const roles = useRoles();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<Role | null>(null);
  const editable = canWrite("ROLE_MANAGEMENT");

  const columns: ColumnDef<Role>[] = [
    {
      id: "name",
      header: "Role name",
      cell: (role) => (
        <div className="min-w-48">
          <div className="font-medium">{role.displayName}</div>
          <div className="text-xs text-muted-foreground">{role.description || "No description"}</div>
        </div>
      ),
    },
    {
      id: "code",
      header: "Internal code",
      cell: (role) => (
        <span className="inline-flex items-center gap-1.5 font-mono text-[0.8125rem] text-muted-foreground">
          <Lock className="size-3" aria-hidden="true" />
          {role.code}
        </span>
      ),
    },
    { id: "users", header: "Users", numeric: true, cell: (role) => (role.userCount === undefined ? "—" : formatNumber(role.userCount)) },
  ];

  return (
    <>
      <DataTable
        caption="Roles"
        itemLabel="roles"
        columns={columns}
        columnMenu={false}
        rows={roles.data}
        rowKey={(role) => role.id}
        loading={roles.isPending}
        error={roles.error}
        onRetry={() => roles.refetch()}
        skeletonRows={3}
        rowActions={(role) => (
          <>
            <Button asChild size="sm" variant="ghost">
              <Link to={`/roles/${role.id}/permissions`} aria-label={`Default permissions of ${role.displayName}`}>
                <ShieldCheck aria-hidden="true" /> Default permissions
              </Link>
            </Button>
            {editable ? (
              <Button size="sm" variant="outline" onClick={() => setEditing(role)} aria-label={`Edit ${role.displayName}`}>
                <Pencil aria-hidden="true" /> Edit
              </Button>
            ) : null}
          </>
        )}
      />
      {editing ? (
        <EditRoleDialog
          key={editing.id}
          role={editing}
          onClose={() => setEditing(null)}
          onSaved={async (updated) => {
            setEditing(null);
            toast.success(`Role renamed to “${updated.displayName}”. The new name is shown everywhere.`);
            await queryClient.invalidateQueries({ queryKey: queryKeys.roles });
            await queryClient.invalidateQueries({ queryKey: queryKeys.users.all });
            await refreshUser().catch(() => undefined);
          }}
        />
      ) : null}
    </>
  );
}

const normalise = (value: string) => value.trim().replace(/\s+/g, " ");

/** Mounted per role (keyed), so its fields start from that role's current values. */
function EditRoleDialog({ role, onClose, onSaved }: { role: Role; onClose: () => void; onSaved: (role: Role) => void }) {
  const [name, setName] = useState(role.displayName);
  const [description, setDescription] = useState(role.description ?? "");
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const trimmed = normalise(name);
  const validation = trimmed.length < 2 ? "Enter at least 2 characters." : trimmed.length > 60 ? "Use at most 60 characters." : null;
  const renamed = trimmed !== role.displayName && !validation;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (validation) {
      setError(validation);
      return;
    }
    setSaving(true);
    setError(null);
    setFormError(null);
    try {
      const updated = await roleService.update(role.id, {
        displayName: trimmed,
        description: description.trim() || null,
        version: role.version,
      });
      onSaved(updated);
    } catch (err) {
      if (err instanceof ApiRequestError && err.code === "DUPLICATE_ROLE_NAME") setError("Another role already uses this name.");
      else if (err instanceof ApiRequestError && err.fieldErrors.length > 0) setError(err.fieldErrors[0].message);
      else setFormError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog
      open
      onOpenChange={(next) => !next && !saving && onClose()}
      title="Edit role"
      description={
        <>
          Internal code <span className="font-mono">{role.code}</span> cannot be changed.
        </>
      }
      footer={
        <>
          <Button onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" form="role-form" variant="primary" loading={saving} loadingText="Saving…">
            Save
          </Button>
        </>
      }
    >
      <form id="role-form" onSubmit={submit} noValidate className="space-y-4">
        <FormError>{formError}</FormError>
        <FormField label="Role name" error={error ?? undefined} required hint="2–60 characters, unique across roles.">
          <Input type="text" value={name} maxLength={80} onChange={(e) => setName(e.target.value)} />
        </FormField>
        <FormField label="Description">
          <Textarea rows={2} maxLength={255} value={description} onChange={(e) => setDescription(e.target.value)} />
        </FormField>
        {renamed ? (
          <Alert tone="info" role="status">
            Everyone with this role will see <strong>{trimmed}</strong> instead of <strong>{role.displayName}</strong>. Access does not change.
          </Alert>
        ) : null}
      </form>
    </Dialog>
  );
}
