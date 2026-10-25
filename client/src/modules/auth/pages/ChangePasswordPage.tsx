import { useState } from "react";
import { useNavigate } from "react-router";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Check, Circle, ShieldAlert } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";
import { FormActions, FormError, FormField } from "@/components/forms/FormField";
import { PasswordInput } from "@/components/forms/inputs";
import { PageContainer, PageHeader } from "@/components/layout/Page";
import { Card, CardContent } from "@/components/ui/Card";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { applyServerErrors, PASSWORD_RULES, passwordPolicySchema } from "@/lib/validation";
import { errorMessage } from "@/lib/errors";
import { ApiRequestError } from "@/services/api";
import { cn } from "@/lib/cn";

/** Mirrors the server policy (12–128 characters, a letter and a digit); the server re-validates. */
const schema = z
  .object({
    currentPassword: z.string().min(1, "Current password is required."),
    newPassword: passwordPolicySchema,
    confirmPassword: z.string().min(1, "Confirm the new password."),
  })
  .refine((v) => v.newPassword === v.confirmPassword, { path: ["confirmPassword"], message: "Passwords do not match." })
  .refine((v) => v.newPassword !== v.currentPassword, {
    path: ["newPassword"],
    message: "Choose a password different from the current one.",
  });

type Values = z.infer<typeof schema>;

export function ChangePasswordPage() {
  const { user, changePassword, logout } = useAuth();
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const forced = !!user?.mustChangePassword;
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { currentPassword: "", newPassword: "", confirmPassword: "" } });
  const newPassword = useWatch({ control, name: "newPassword" });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await changePassword(values.currentPassword, values.newPassword);
      toast.success("Password changed. Other sessions were signed out.");
      navigate("/", { replace: true });
    } catch (error) {
      const hasFieldErrors = error instanceof ApiRequestError && error.fieldErrors.length > 0;
      const unmatched = applyServerErrors(error, setError, ["currentPassword", "newPassword"]);
      if (!hasFieldErrors) setFormError(errorMessage(error));
      else if (unmatched.length > 0) setFormError(unmatched.join(" "));
    }
  });

  return (
    <PageContainer width="narrow">
      <PageHeader
        title="Change password"
        description={forced ? "You must set a new password before continuing." : "Changing your password signs out your other sessions."}
      />
      {forced ? (
        <Alert tone="warning" title="Password change required">
          Your account was set up with a temporary password. Choose your own to continue.
        </Alert>
      ) : null}
      <Card>
        <CardContent>
          <form onSubmit={submit} noValidate className="flex flex-col gap-4">
            <FormError>{formError}</FormError>
            <FormField label="Current password" error={errors.currentPassword?.message} required>
              <PasswordInput autoComplete="current-password" {...register("currentPassword")} />
            </FormField>
            <FormField label="New password" error={errors.newPassword?.message} required>
              <PasswordInput autoComplete="new-password" {...register("newPassword")} />
            </FormField>
            <ul className="-mt-1 grid gap-1 text-xs" aria-label="Password requirements">
              {PASSWORD_RULES.map((rule) => {
                const met = rule.test(newPassword ?? "");
                return (
                  <li key={rule.label} className={cn("flex items-center gap-1.5", met ? "text-success" : "text-muted-foreground")}>
                    {met ? <Check className="size-3.5" aria-hidden="true" /> : <Circle className="size-3" aria-hidden="true" />}
                    {rule.label}
                    <span className="sr-only">{met ? " (met)" : " (not met)"}</span>
                  </li>
                );
              })}
            </ul>
            <FormField label="Confirm new password" error={errors.confirmPassword?.message} required>
              <PasswordInput autoComplete="new-password" {...register("confirmPassword")} />
            </FormField>
            <FormActions>
              {forced ? (
                <Button onClick={() => logout().then(() => navigate("/login", { replace: true }))}>Sign out</Button>
              ) : (
                <Button onClick={() => navigate(-1)}>Cancel</Button>
              )}
              <Button type="submit" variant="primary" loading={isSubmitting} loadingText="Saving…">
                Change password
              </Button>
            </FormActions>
          </form>
        </CardContent>
      </Card>
      <p className="flex items-start gap-1.5 text-xs text-muted-foreground">
        <ShieldAlert className="mt-px size-3.5 shrink-0" aria-hidden="true" />
        Never share your password. IOTIQ administrators will never ask for it.
      </p>
    </PageContainer>
  );
}
