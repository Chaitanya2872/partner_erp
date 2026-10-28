import { useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { History, KeyRound, LockOpen, Pencil, Plus, ShieldCheck, UserCheck, UserX } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";
import { useDebounce } from "@/hooks/useDebounce";
import { DataTable, type ColumnDef } from "@/components/tables/DataTable";
import { toSortParam, type SortState } from "@/components/tables/sorting";
import { EmptyState } from "@/components/feedback/States";
import { ConfirmDialog } from "@/components/feedback/ConfirmDialog";
import { PageActions, PageContainer, PageHeader, Toolbar } from "@/components/layout/Page";
import { SearchInput } from "@/components/forms/SearchInput";
import { StatusBadge } from "@/components/data/StatusBadge";
import { Avatar } from "@/components/ui/Avatar";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Input";
import { formatDateTime } from "@/utils/format";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { useRoles } from "@/modules/settings/hooks/useRoles";
import { userService } from "../services/userService";
import { UserFormDialog, type UserFormValues } from "../components/UserFormDialog";
import { ResetPasswordDialog } from "../components/ResetPasswordDialog";
import { UserActivityDialog } from "../components/UserActivityDialog";
import type { User } from "../types/user.types";

/** Account status filter. "LOCKED" means a sign-in lockout in force (locked=true), not a stored status. */
type StatusFilter = "" | "ACTIVE" | "DISABLED" | "LOCKED";

export function UsersPage() {
  const { user: me, canWrite, canPerform } = useAuth();
  const queryClient = useQueryClient();
  const [params] = useSearchParams();
  const [search, setSearch] = useState("");
  const [roleId, setRoleId] = useState("");
  const [status, setStatus] = useState<StatusFilter>(params.get("locked") === "true" ? "LOCKED" : "");
  const [unlockTarget, setUnlockTarget] = useState<User | null>(null);
  const [resetTarget, setResetTarget] = useState<User | null>(null);
  const [activityTarget, setActivityTarget] = useState<User | null>(null);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState<SortState | null>({ key: "fullName", direction: "asc" });
  const [editing, setEditing] = useState<User | null>(null);
  const [creating, setCreating] = useState(false);
  const [statusTarget, setStatusTarget] = useState<User | null>(null);
  const q = useDebounce(search.trim());

  const roles = useRoles();
  const filters = {
    q,
    roleId,
    status: status === "LOCKED" ? "" : status,
    locked: status === "LOCKED" ? true : undefined,
    page,
    size,
    sort: toSortParam(sort),
  };
  const users = useQuery({
    queryKey: queryKeys.users.list(filters),
    queryFn: ({ signal }) => userService.list(filters, signal),
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
  /** Non-Super-Admins manage accounts with the User role only (enforced by the server too). Uses the role code. */
  const manageable = (target: User) => isSuperAdmin || target.role.code === "USER";

  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.users.all });

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
    await queryClient.invalidateQueries({ queryKey: queryKeys.roles });
  };

  const statusMutation = useMutation({
    mutationFn: (target: User) => userService.setStatus(target.id, target.status === "ACTIVE" ? "DISABLED" : "ACTIVE", target.version),
    onSuccess: (updated) => {
      toast.success(updated.status === "ACTIVE" ? "User activated." : "User deactivated and signed out.");
      setStatusTarget(null);
      invalidate();
    },
    onError: (error) => {
      toast.error(errorMessage(error));
      setStatusTarget(null);
    },
  });

  const unlockMutation = useMutation({
    mutationFn: (target: User) => userService.unlock(target.id, target.version),
    onSuccess: () => {
      toast.success("Account unlocked. The user can sign in again.");
      setUnlockTarget(null);
      invalidate();
      queryClient.invalidateQueries({ queryKey: queryKeys.settings.security });
    },
    onError: (error) => {
      toast.error(errorMessage(error));
      setUnlockTarget(null);
      invalidate();
    },
  });

  const resetPassword = async (temporaryPassword: string) => {
    if (!resetTarget) return;
    await userService.resetPassword(resetTarget.id, temporaryPassword, resetTarget.version);
    toast.success("Password reset. Share the temporary password with the user securely.");
    setResetTarget(null);
    await invalidate();
    await queryClient.invalidateQueries({ queryKey: queryKeys.settings.security });
  };

  const columns: ColumnDef<User>[] = [
    {
      id: "name",
      header: "Name",
      sortKey: "fullName",
      cell: (u) => (
        <div className="flex min-w-52 items-center gap-2.5">
          <Avatar name={u.fullName} size="sm" />
          <div className="min-w-0 max-w-56">
            <div className="truncate font-medium">
              {u.fullName}
              {u.id === me?.id ? <span className="ml-1.5 text-xs font-normal text-muted-foreground">(you)</span> : null}
            </div>
            <div className="truncate text-xs text-muted-foreground" title={u.email}>
              {u.email}
            </div>
          </div>
        </div>
      ),
    },
    { id: "role", header: "Role", cell: (u) => <Badge tone="outline">{u.role.displayName}</Badge> },
    {
      id: "status",
      header: "Status",
      sortKey: "status",
      cell: (u) => (
        <div className="flex flex-wrap items-center gap-1.5">
          <StatusBadge domain="user" status={u.status} suffix={u.mustChangePassword ? "password reset pending" : undefined} />
          {u.locked ? (
            <Badge tone="warning" title={u.lockedUntil ? `Locked until ${formatDateTime(u.lockedUntil)}` : undefined}>
              Locked{u.lockedUntil ? ` until ${formatDateTime(u.lockedUntil)}` : ""}
            </Badge>
          ) : null}
        </div>
      ),
    },
    {
      id: "last",
      header: "Last sign-in",
      sortKey: "lastLoginAt",
      cell: (u) => (u.lastLoginAt ? <span className="whitespace-nowrap">{formatDateTime(u.lastLoginAt)}</span> : <span className="text-muted-foreground">Never</span>),
    },
    { id: "created", header: "Created", sortKey: "createdAt", defaultHidden: true, cell: (u) => <span className="whitespace-nowrap">{formatDateTime(u.createdAt)}</span> },
  ];

  const filtered = !!q || !!roleId || !!status;

  return (
    <PageContainer>
      <PageHeader
        title="Users"
        description="Accounts, roles and access."
        actions={
          canEditUsers ? (
            <PageActions>
              <Button variant="primary" onClick={() => setCreating(true)} disabled={assignable.length === 0}>
                <Plus aria-hidden="true" /> New user
              </Button>
            </PageActions>
          ) : undefined
        }
      />

      <DataTable
        caption="Users"
        itemLabel="users"
        columns={columns}
        rows={users.data?.content}
        rowKey={(u) => u.id}
        loading={users.isPending}
        fetching={users.isFetching && !users.isPending}
        error={users.error}
        onRetry={() => users.refetch()}
        sort={sort}
        onSortChange={(next) => {
          setSort(next);
          setPage(0);
        }}
        page={users.data?.meta}
        onPageChange={setPage}
        pageSizeOptions={[20, 50, 100]}
        onPageSizeChange={(next) => {
          setSize(next);
          setPage(0);
        }}
        empty={<EmptyState title="No users found">{filtered ? "Try other filters." : null}</EmptyState>}
        rowActions={(u) => (
          <>
            <Button asChild size="sm" variant="ghost">
              <Link to={`/users/${u.id}/permissions`}>
                <ShieldCheck aria-hidden="true" /> Permissions
              </Link>
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setActivityTarget(u)} aria-label={`Activity of ${u.fullName}`}>
              <History aria-hidden="true" /> Activity
            </Button>
            {canEditUsers && manageable(u) ? (
              <Button size="sm" variant="outline" onClick={() => setEditing(u)}>
                <Pencil aria-hidden="true" /> Edit
              </Button>
            ) : null}
            {canToggleStatus && manageable(u) && u.id !== me?.id && u.locked ? (
              <Button size="sm" variant="outline" onClick={() => setUnlockTarget(u)}>
                <LockOpen aria-hidden="true" /> Unlock
              </Button>
            ) : null}
            {canToggleStatus && manageable(u) && u.id !== me?.id && u.status === "ACTIVE" ? (
              <Button size="sm" variant="outline" onClick={() => setResetTarget(u)}>
                <KeyRound aria-hidden="true" /> Reset password
              </Button>
            ) : null}
            {canToggleStatus && manageable(u) && u.id !== me?.id ? (
              u.status === "ACTIVE" ? (
                <Button size="sm" variant="destructive-outline" onClick={() => setStatusTarget(u)}>
                  <UserX aria-hidden="true" /> Deactivate
                </Button>
              ) : (
                <Button size="sm" variant="outline" onClick={() => setStatusTarget(u)}>
                  <UserCheck aria-hidden="true" /> Activate
                </Button>
              )
            ) : null}
          </>
        )}
        toolbar={
          <Toolbar label="User filters">
            <SearchInput
              placeholder="Search name or email"
              aria-label="Search users"
              value={search}
              onValueChange={(value) => {
                setSearch(value);
                setPage(0);
              }}
            />
            <Select
              aria-label="Filter by role"
              value={roleId}
              wrapperClassName="sm:w-44"
              onChange={(e) => {
                setRoleId(e.target.value);
                setPage(0);
              }}
            >
              <option value="">All roles</option>
              {(roles.data ?? []).map((r) => (
                <option key={r.id} value={r.id}>
                  {r.displayName}
                </option>
              ))}
            </Select>
            <Select
              aria-label="Filter by status"
              value={status}
              wrapperClassName="sm:w-40"
              onChange={(e) => {
                setStatus(e.target.value as StatusFilter);
                setPage(0);
              }}
            >
              <option value="">All statuses</option>
              <option value="ACTIVE">Active</option>
              <option value="DISABLED">Disabled</option>
              <option value="LOCKED">Locked out</option>
            </Select>
          </Toolbar>
        }
      />

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
            <strong>{statusTarget.fullName}</strong> will be signed out of every session immediately and cannot sign in until reactivated.
          </p>
        ) : (
          <p>
            <strong>{statusTarget?.fullName}</strong> will be able to sign in again. Failed sign-in counters are reset.
          </p>
        )}
      </ConfirmDialog>

      <ConfirmDialog
        open={!!unlockTarget}
        title="Unlock account?"
        confirmLabel="Unlock"
        busy={unlockMutation.isPending}
        onCancel={() => setUnlockTarget(null)}
        onConfirm={() => unlockTarget && unlockMutation.mutate(unlockTarget)}
      >
        <p>
          <strong>{unlockTarget?.fullName}</strong> can sign in again straight away. Failed sign-in counters are reset; the password is not
          changed.
        </p>
      </ConfirmDialog>

      <ResetPasswordDialog user={resetTarget} onClose={() => setResetTarget(null)} onSubmit={resetPassword} />
      <UserActivityDialog user={activityTarget} onClose={() => setActivityTarget(null)} />
    </PageContainer>
  );
}
