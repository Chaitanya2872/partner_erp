import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Dialog } from "@/components/ui/Dialog";
import { Button } from "@/components/ui/Button";
import { FormError, FormField } from "@/components/forms/FormField";
import { PasswordInput } from "@/components/forms/inputs";
import { applyServerErrors, passwordPolicySchema } from "@/lib/validation";
import { errorMessage } from "@/lib/errors";
import { ApiRequestError } from "@/services/api";
import type { User } from "../types/user.types";

const schema = z.object({ temporaryPassword: passwordPolicySchema });
type Values = z.infer<typeof schema>;

interface ResetPasswordDialogProps {
  user: User | null;
  onClose: () => void;
  onSubmit: (temporaryPassword: string) => Promise<void>;
}

/**
 * Sets a temporary password chosen by the administrator. There is no email delivery: the administrator shares it
 * with the user securely. The user must change it at the next sign-in, and every session is signed out at once.
 */
export function ResetPasswordDialog({ user, onClose, onSubmit }: ResetPasswordDialogProps) {
  if (!user) return null;
  return <ResetPasswordForm key={user.id} user={user} onClose={onClose} onSubmit={onSubmit} />;
}

function ResetPasswordForm({ user, onClose, onSubmit }: { user: User; onClose: () => void; onSubmit: (password: string) => Promise<void> }) {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { temporaryPassword: "" } });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values.temporaryPassword);
    } catch (error) {
      const unmatched = applyServerErrors(error, setError, ["temporaryPassword"] as const);
      const hasFieldErrors = error instanceof ApiRequestError && error.fieldErrors.length > 0;
      if (!hasFieldErrors) setFormError(errorMessage(error));
      else if (unmatched.length > 0) setFormError(unmatched.join(" "));
    }
  });

  return (
    <Dialog
      open
      onOpenChange={(next) => !next && !isSubmitting && onClose()}
      title="Reset password"
      description={
        <>
          <strong>{user.fullName}</strong> is signed out everywhere and must choose a new password at the next sign-in.
        </>
      }
      footer={
        <>
          <Button onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button type="submit" form="reset-password-form" variant="destructive" loading={isSubmitting} loadingText="Resetting…">
            Reset password
          </Button>
        </>
      }
    >
      <form id="reset-password-form" onSubmit={submit} noValidate className="space-y-4">
        <FormError>{formError}</FormError>
        <FormField
          label="Temporary password"
          error={errors.temporaryPassword?.message}
          required
          hint="12–128 characters with a letter and a digit. Share it with the user securely; it is never shown again."
        >
          <PasswordInput autoComplete="new-password" {...register("temporaryPassword")} />
        </FormField>
        {user.locked ? <p className="text-sm text-muted-foreground">The sign-in lockout is also lifted.</p> : null}
      </form>
    </Dialog>
  );
}
