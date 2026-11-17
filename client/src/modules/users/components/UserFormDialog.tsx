import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Dialog } from "@/components/ui/Dialog";
import { Button } from "@/components/ui/Button";
import { Input, Select } from "@/components/ui/Input";
import { FormError, FormField, FormGrid } from "@/components/forms/FormField";
import { PasswordInput } from "@/components/forms/inputs";
import { applyServerErrors, emailSchema, passwordPolicySchema } from "@/lib/validation";
import { errorMessage } from "@/lib/errors";
import { ApiRequestError } from "@/services/api";
import type { Role } from "@/modules/settings/services/roleService";
import type { User } from "../types/user.types";

const base = {
  fullName: z.string().trim().min(2, "Enter the full name.").max(120, "Use at most 120 characters."),
  email: emailSchema,
  roleId: z.string().min(1, "Select a role."),
};

const createSchema = z.object({ ...base, temporaryPassword: passwordPolicySchema });
const editSchema = z.object({ ...base, temporaryPassword: z.string().optional() });

export type UserFormValues = z.infer<typeof createSchema>;

const FIELDS = ["fullName", "email", "roleId", "temporaryPassword"] as const;

interface UserFormDialogProps {
  open: boolean;
  user: User | null;
  roles: Role[];
  /** Roles the current user may assign (non-Super-Admins: User only). */
  assignableRoleIds: string[];
  /** Role changes are never allowed on one's own account. */
  canChangeRole: boolean;
  onClose: () => void;
  onSubmit: (values: UserFormValues) => Promise<void>;
}

/** Mounts a fresh form each time the dialog opens (keyed by the user), so fields start from current values. */
export function UserFormDialog(props: UserFormDialogProps) {
  return props.open ? <UserFormDialogBody key={props.user?.id ?? "new"} {...props} /> : null;
}

function UserFormDialogBody({ user, roles, assignableRoleIds, canChangeRole, onClose, onSubmit }: UserFormDialogProps) {
  const editing = !!user;
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<UserFormValues>({
    resolver: zodResolver(editing ? editSchema : createSchema) as never,
    defaultValues: {
      fullName: user?.fullName ?? "",
      email: user?.email ?? "",
      roleId: user?.role.id ?? (assignableRoleIds.length === 1 ? assignableRoleIds[0] : ""),
      temporaryPassword: "",
    },
  });

  const selectedRoleId = useWatch({ control, name: "roleId" });
  const roleChanged = editing && selectedRoleId !== user?.role.id;
  const selectable = roles.filter((r) => assignableRoleIds.includes(r.id) || r.id === user?.role.id);

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      const unmatched = applyServerErrors(error, setError, FIELDS);
      const hasFieldErrors = error instanceof ApiRequestError && error.fieldErrors.length > 0;
      if (!hasFieldErrors) setFormError(errorMessage(error));
      else if (unmatched.length > 0) setFormError(unmatched.join(" "));
    }
  });

  return (
    <Dialog
      open
      onOpenChange={(next) => !next && !isSubmitting && onClose()}
      title={editing ? "Edit user" : "Create user"}
      description={editing ? undefined : "The user must change the temporary password at first sign-in."}
      footer={
        <>
          <Button onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button type="submit" form="user-form" variant="primary" loading={isSubmitting} loadingText="Saving…">
            {editing ? "Save changes" : "Create user"}
          </Button>
        </>
      }
    >
      <form id="user-form" onSubmit={submit} noValidate className="space-y-4">
        <FormError>{formError}</FormError>
        <FormGrid>
          <FormField label="Full name" error={errors.fullName?.message} required wide>
            <Input type="text" autoComplete="off" {...register("fullName")} />
          </FormField>
          <FormField label="Email" error={errors.email?.message} required wide>
            <Input type="email" autoComplete="off" inputMode="email" {...register("email")} />
          </FormField>
          <FormField
            label="Role"
            error={errors.roleId?.message}
            required
            wide
            hint={
              roleChanged
                ? "Changing the role resets this user's permissions to the role's defaults."
                : editing && !canChangeRole
                  ? "You cannot change your own role."
                  : undefined
            }
          >
            <Select {...register("roleId")} disabled={editing && !canChangeRole}>
              <option value="">Select a role…</option>
              {selectable.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.displayName}
                </option>
              ))}
            </Select>
          </FormField>
          {!editing ? (
            <FormField
              label="Temporary password"
              error={errors.temporaryPassword?.message}
              required
              wide
              hint="12–128 characters with a letter and a digit. Share it with the user securely."
            >
              <PasswordInput autoComplete="new-password" {...register("temporaryPassword")} />
            </FormField>
          ) : null}
        </FormGrid>
      </form>
    </Dialog>
  );
}
