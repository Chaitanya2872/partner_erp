import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Dialog } from "@/components/ui/Dialog";
import { FormField } from "@/components/forms/FormField";
import { ApiRequestError } from "@/services/api";
import type { Role } from "@/modules/settings/services/roleService";
import type { User } from "../types/user.types";

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const base = {
  fullName: z.string().trim().min(2, "Enter the full name.").max(120, "Use at most 120 characters."),
  email: z.string().trim().min(1, "Email is required.").regex(EMAIL, "Enter a valid email address.").max(254),
  roleId: z.string().min(1, "Select a role."),
};

const createSchema = z.object({
  ...base,
  temporaryPassword: z
    .string()
    .min(12, "Use at least 12 characters.")
    .max(128, "Use at most 128 characters.")
    .regex(/[A-Za-z]/, "Include at least one letter.")
    .regex(/\d/, "Include at least one digit."),
});
const editSchema = z.object({ ...base, temporaryPassword: z.string().optional() });

export type UserFormValues = z.infer<typeof createSchema>;

interface UserFormDialogProps {
  open: boolean;
  user: User | null;
  roles: Role[];
  /** Roles the current user may assign (non-Super-Admins: User only). */
  assignableRoleIds: string[];
  /** Role changes are reserved for Super Admins and never allowed on one's own account. */
  canChangeRole: boolean;
  onClose: () => void;
  onSubmit: (values: UserFormValues) => Promise<void>;
}

export function UserFormDialog({ open, user, roles, assignableRoleIds, canChangeRole, onClose, onSubmit }: UserFormDialogProps) {
  const editing = !!user;
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    reset,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<UserFormValues>({ resolver: zodResolver(editing ? editSchema : createSchema) as never });

  useEffect(() => {
    if (open) {
      setFormError(null);
      reset({
        fullName: user?.fullName ?? "",
        email: user?.email ?? "",
        roleId: user?.role.id ?? (assignableRoleIds.length === 1 ? assignableRoleIds[0] : ""),
        temporaryPassword: "",
      });
    }
  }, [open, user, reset, assignableRoleIds]);

  const roleChanged = editing && watch("roleId") !== user?.role.id;
  const selectable = roles.filter((r) => assignableRoleIds.includes(r.id) || r.id === user?.role.id);

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      if (error instanceof ApiRequestError && error.fieldErrors.length > 0) {
        error.fieldErrors.forEach((fe) => setError(fe.field as keyof UserFormValues, { message: fe.message }));
      } else {
        setFormError(error instanceof Error ? error.message : "Saving failed.");
      }
    }
  });

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => !next && onClose()}
      title={editing ? "Edit user" : "Create user"}
      description={editing ? undefined : "The user must change the temporary password at first sign-in."}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose} disabled={isSubmitting}>
            Cancel
          </button>
          <button type="submit" form="user-form" className="btn primary" disabled={isSubmitting}>
            {isSubmitting ? "Saving…" : editing ? "Save changes" : "Create user"}
          </button>
        </>
      }
    >
      <form id="user-form" onSubmit={submit} noValidate>
        {formError ? (
          <div className="notice bad" role="alert" style={{ marginBottom: 12 }}>
            {formError}
          </div>
        ) : null}
        <div className="fgrid">
          <FormField label="Full name" error={errors.fullName?.message} wide>
            <input type="text" autoComplete="off" {...register("fullName")} />
          </FormField>
          <FormField label="Email" error={errors.email?.message} wide>
            <input type="email" autoComplete="off" {...register("email")} />
          </FormField>
          <FormField
            label="Role"
            error={errors.roleId?.message}
            wide
            hint={roleChanged ? "Changing the role resets this user's permissions to the role's defaults." : undefined}
          >
            <select {...register("roleId")} disabled={editing && !canChangeRole}>
              <option value="">Select a role…</option>
              {selectable.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.displayName}
                </option>
              ))}
            </select>
          </FormField>
          {!editing ? (
            <FormField label="Temporary password" error={errors.temporaryPassword?.message} wide hint="12–128 characters with a letter and a digit. Share it securely.">
              <input type="password" autoComplete="new-password" {...register("temporaryPassword")} />
            </FormField>
          ) : null}
        </div>
      </form>
    </Dialog>
  );
}
