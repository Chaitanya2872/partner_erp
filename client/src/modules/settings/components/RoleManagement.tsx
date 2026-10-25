import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Pencil } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";
import { Dialog } from "@/components/ui/Dialog";
import { FormField } from "@/components/forms/FormField";
import { LoadingSpinner } from "@/components/common/LoadingSpinner";
import { ErrorState } from "@/components/common/EmptyState";
import { ApiRequestError } from "@/services/api";
import { roleService, type Role } from "../services/roleService";
import { useRoles } from "../hooks/useRoles";

/**
 * Settings → Role Management. Only display names and descriptions are editable (Super Admin); internal codes
 * are fixed and drive authorization.
 */
export function RoleManagement() {
  const { canWrite, refreshUser } = useAuth();
  const roles = useRoles();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<Role | null>(null);
  const editable = canWrite("ROLE_MANAGEMENT");

  if (roles.isPending) return <LoadingSpinner label="Loading roles…" />;
  if (roles.isError) return <ErrorState error={roles.error} onRetry={() => roles.refetch()} />;

  return (
    <section className="card" aria-labelledby="roles-heading">
      <h3 id="roles-heading">Role management</h3>
      <p className="muted small" style={{ marginTop: -4 }}>
        The three system roles cannot be added or removed. Renaming a role changes its label everywhere; users and
        permissions are unaffected.
      </p>
      <div className="scroll">
        <table className="tbl">
          <thead>
            <tr>
              <th scope="col">Role name</th>
              <th scope="col">Internal code</th>
              <th scope="col" className="r">
                Users
              </th>
              {editable ? (
                <th scope="col">
                  <span className="sr-only">Edit</span>
                </th>
              ) : null}
            </tr>
          </thead>
          <tbody>
            {roles.data.map((role) => (
              <tr key={role.id}>
                <td>
                  <div style={{ fontWeight: 600 }}>{role.displayName}</div>
                  {role.description ? <div className="muted small">{role.description}</div> : null}
                </td>
                <td className="mono">{role.code}</td>
                <td className="r">{role.userCount ?? "—"}</td>
                {editable ? (
                  <td className="ra">
                    <button type="button" className="btn sm" onClick={() => setEditing(role)} aria-label={`Edit ${role.displayName}`}>
                      <Pencil size={13} aria-hidden="true" /> Edit
                    </button>
                  </td>
                ) : null}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {editing ? (
        <EditRoleDialog
          key={editing.id}
          role={editing}
          onClose={() => setEditing(null)}
          onSaved={async (updated) => {
            setEditing(null);
            toast.success(`Role renamed to “${updated.displayName}”.`);
            await queryClient.invalidateQueries({ queryKey: ["roles"] });
            await queryClient.invalidateQueries({ queryKey: ["users"] });
            await refreshUser().catch(() => undefined);
          }}
        />
      ) : null}
    </section>
  );
}

/** Mounted per role (keyed), so its fields start from that role's current values. */
function EditRoleDialog({ role, onClose, onSaved }: { role: Role; onClose: () => void; onSaved: (role: Role) => void }) {
  const [name, setName] = useState(role.displayName);
  const [description, setDescription] = useState(role.description ?? "");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const trimmed = name.trim().replace(/\s+/g, " ");
  const validation = trimmed.length < 2 ? "Enter at least 2 characters." : trimmed.length > 60 ? "Use at most 60 characters." : null;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (validation) {
      setError(validation);
      return;
    }
    setSaving(true);
    setError(null);
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
      else setError(err instanceof Error ? err.message : "Saving failed.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog
      open
      onOpenChange={(next) => !next && onClose()}
      title="Edit role"
      description={`Internal code ${role.code} cannot be changed.`}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose} disabled={saving}>
            Cancel
          </button>
          <button type="submit" form="role-form" className="btn primary" disabled={saving}>
            {saving ? "Saving…" : "Save"}
          </button>
        </>
      }
    >
      <form id="role-form" onSubmit={submit} noValidate className="fgrid">
        <FormField label="Role name" error={error ?? undefined} wide>
          <input type="text" value={name} maxLength={80} onChange={(e) => setName(e.target.value)} />
        </FormField>
        <FormField label="Description" wide>
          <textarea rows={2} maxLength={255} value={description} onChange={(e) => setDescription(e.target.value)} />
        </FormField>
      </form>
    </Dialog>
  );
}
