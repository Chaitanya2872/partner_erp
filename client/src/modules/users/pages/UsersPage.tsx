import { useMemo, useState } from "react";
import { Link } from "react-router";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Search, ShieldCheck } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";
import { useDebounce } from "@/hooks/useDebounce";
import { DataTable, Pager, type Column } from "@/components/tables/DataTable";
import { ErrorState, EmptyState } from "@/components/common/EmptyState";
import { LoadingSpinner } from "@/components/common/LoadingSpinner";
import { ConfirmDialog } from "@/components/modals/ConfirmDialog";
import { useRoles } from "@/modules/settings/hooks/useRoles";
import { userService } from "../services/userService";
import { UserFormDialog, type UserFormValues } from "../components/UserFormDialog";
import type { User } from "../types/user.types";
import type { UserStatus } from "@/types/common.types";

const STATUS_PILL: Record<UserStatus, string> = { ACTIVE: "good", DISABLED: "bad", LOCKED: "warn" };
const STATUS_LABEL: Record<UserStatus, string> = { ACTIVE: "Active", DISABLED: "Disabled", LOCKED: "Locked" };

export function UsersPage() {
  const { user: me, canWrite, canPerform } = useAuth();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const [roleId, setRoleId] = useState("");
  const [status, setStatus] = useState<UserStatus | "">("");
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<User | null>(null);
  const [creating, setCreating] = useState(false);
  const [statusTarget, setStatusTarget] = useState<User | null>(null);
  const q = useDebounce(search.trim());

  const roles = useRoles();
  const users = useQuery({
    queryKey: ["users", { q, roleId, status, page }],
    queryFn: () => userService.list({ q, roleId, status, page, size: 20, sort: "fullName,asc" }),
    placeholderData: keepPreviousData,
  });

  const canEditUsers = canWrite("USER_MANAGEMENT");
  const canToggleStatus = canPerform("USER_MANAGEMENT", "MANAGE_USERS");
  const isSuperAdmin = !!me?.superAdmin;
  const userRole = roles.data?.find((r) => r.code === "USER");
  const assignable = useMemo(
    () => (isSuperAdmin ? (roles.data ?? []).map((r) => r.id) : userRole ? [userRole.id] : []),
    [isSuperAdmin, roles.data, userRole],
  );
  /** Non-Super-Admins manage accounts with the User role only (enforced by the server too). */
  const manageable = (target: User) => isSuperAdmin || target.role.code === "USER";

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["users"] });

  const save = async (values: UserFormValues) => {
    if (editing) {
      await userService.update(editing.id, {
        fullName: values.fullName,
        email: values.email,
        roleId: values.roleId,
        version: editing.version,
      });
      toast.success("User updated.");
    } else {
      await userService.create(values);
      toast.success("User created. Share the temporary password securely.");
    }
    setEditing(null);
    setCreating(false);
    await invalidate();
    await queryClient.invalidateQueries({ queryKey: ["roles"] });
  };

  const statusMutation = useMutation({
    mutationFn: (target: User) => userService.setStatus(target.id, target.status === "ACTIVE" ? "DISABLED" : "ACTIVE", target.version),
    onSuccess: (updated) => {
      toast.success(updated.status === "ACTIVE" ? "User activated." : "User deactivated and signed out.");
      setStatusTarget(null);
      invalidate();
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : "Status change failed.");
      setStatusTarget(null);
    },
  });

  const columns: Column<User>[] = [
    {
      key: "name",
      header: "Name",
      cell: (u) => (
        <div>
          <div style={{ fontWeight: 600 }}>{u.fullName}</div>
          <div className="muted small">{u.email}</div>
        </div>
      ),
    },
    { key: "role", header: "Role", cell: (u) => <span className="pill">{u.role.displayName}</span> },
    {
      key: "status",
      header: "Status",
      cell: (u) => (
        <span className={`pill ${STATUS_PILL[u.status]}`}>
          {STATUS_LABEL[u.status]}
          {u.mustChangePassword ? " · password reset pending" : ""}
        </span>
      ),
    },
    {
      key: "last",
      header: "Last sign-in",
      cell: (u) => (u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString("en-IN") : <span className="muted">Never</span>),
    },
    {
      key: "actions",
      header: <span className="sr-only">Actions</span>,
      className: "ra",
      cell: (u) => (
        <div className="acts" style={{ justifyContent: "flex-end" }}>
          <Link to={`/users/${u.id}/permissions`} className="btn sm">
            <ShieldCheck size={13} aria-hidden="true" /> Permissions
          </Link>
          {canEditUsers && manageable(u) ? (
            <button type="button" className="btn sm" onClick={() => setEditing(u)}>
              Edit
            </button>
          ) : null}
          {canToggleStatus && manageable(u) && u.id !== me?.id ? (
            <button type="button" className={u.status === "ACTIVE" ? "btn sm danger" : "btn sm"} onClick={() => setStatusTarget(u)}>
              {u.status === "ACTIVE" ? "Deactivate" : "Activate"}
            </button>
          ) : null}
        </div>
      ),
    },
  ];

  return (
    <>
      <div className="top">
        <div>
          <h1>Users</h1>
          <p>Accounts, roles and access.</p>
        </div>
        {canEditUsers ? (
          <div className="acts">
            <button type="button" className="btn primary" onClick={() => setCreating(true)} disabled={assignable.length === 0}>
              <Plus size={14} aria-hidden="true" /> New user
            </button>
          </div>
        ) : null}
      </div>

      <div className="tools" role="search">
        <Search size={16} aria-hidden="true" className="muted" />
        <input
          type="search"
          placeholder="Search name or email"
          aria-label="Search users"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
        />
        <select aria-label="Filter by role" value={roleId} onChange={(e) => { setRoleId(e.target.value); setPage(0); }}>
          <option value="">All roles</option>
          {(roles.data ?? []).map((r) => (
            <option key={r.id} value={r.id}>
              {r.displayName}
            </option>
          ))}
        </select>
        <select aria-label="Filter by status" value={status} onChange={(e) => { setStatus(e.target.value as UserStatus | ""); setPage(0); }}>
          <option value="">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="DISABLED">Disabled</option>
          <option value="LOCKED">Locked</option>
        </select>
      </div>

      {users.isPending ? (
        <LoadingSpinner label="Loading users…" />
      ) : users.isError ? (
        <ErrorState error={users.error} onRetry={() => users.refetch()} />
      ) : (
        <>
          <DataTable
            caption="Users"
            columns={columns}
            rows={users.data.content}
            rowKey={(u) => u.id}
            empty={<EmptyState title="No users found">{q || roleId || status ? "Try other filters." : null}</EmptyState>}
          />
          <Pager meta={users.data.meta} onPage={setPage} />
        </>
      )}

      <UserFormDialog
        open={creating || !!editing}
        user={editing}
        roles={roles.data ?? []}
        assignableRoleIds={assignable}
        canChangeRole={isSuperAdmin && editing?.id !== me?.id}
        onClose={() => {
          setCreating(false);
          setEditing(null);
        }}
        onSubmit={save}
      />

      <ConfirmDialog
        open={!!statusTarget}
        title={statusTarget?.status === "ACTIVE" ? "Deactivate user?" : "Activate user?"}
        confirmLabel={statusTarget?.status === "ACTIVE" ? "Deactivate" : "Activate"}
        danger={statusTarget?.status === "ACTIVE"}
        busy={statusMutation.isPending}
        onCancel={() => setStatusTarget(null)}
        onConfirm={() => statusTarget && statusMutation.mutate(statusTarget)}
      >
        {statusTarget?.status === "ACTIVE" ? (
          <p>
            <strong>{statusTarget.fullName}</strong> will be signed out of every session immediately and cannot sign in until
            reactivated.
          </p>
        ) : (
          <p>
            <strong>{statusTarget?.fullName}</strong> will be able to sign in again. Failed sign-in counters are reset.
          </p>
        )}
      </ConfirmDialog>
    </>
  );
}
