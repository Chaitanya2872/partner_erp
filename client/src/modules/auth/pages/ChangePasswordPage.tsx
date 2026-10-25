import { useState } from "react";
import { useNavigate } from "react-router";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";
import { FormField } from "@/components/forms/FormField";
import { ApiRequestError } from "@/services/api";

/** Mirrors the server policy (12-128 characters, a letter and a digit); the server re-validates. */
const schema = z
  .object({
    currentPassword: z.string().min(1, "Current password is required."),
    newPassword: z
      .string()
      .min(12, "Use at least 12 characters.")
      .max(128, "Use at most 128 characters.")
      .regex(/[A-Za-z]/, "Include at least one letter.")
      .regex(/\d/, "Include at least one digit."),
    confirmPassword: z.string(),
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
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema) });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await changePassword(values.currentPassword, values.newPassword);
      toast.success("Password changed. Other sessions were signed out.");
      navigate("/", { replace: true });
    } catch (error) {
      if (error instanceof ApiRequestError && error.fieldErrors.length > 0) {
        error.fieldErrors.forEach((fe) => {
          if (fe.field === "currentPassword" || fe.field === "newPassword") {
            setError(fe.field, { message: fe.message });
          }
        });
      } else {
        setFormError(error instanceof Error ? error.message : "Password change failed.");
      }
    }
  });

  return (
    <div style={{ maxWidth: 460 }}>
      <div className="top">
        <div>
          <h1>Change password</h1>
          <p>{forced ? "You must set a new password before continuing." : "Changing your password signs out your other sessions."}</p>
        </div>
      </div>
      <form className="card" onSubmit={submit} noValidate style={{ display: "flex", flexDirection: "column", gap: 12 }}>
        {formError ? (
          <div className="notice bad" role="alert">
            {formError}
          </div>
        ) : null}
        <FormField label="Current password" error={errors.currentPassword?.message}>
          <input type="password" autoComplete="current-password" {...register("currentPassword")} />
        </FormField>
        <FormField label="New password" error={errors.newPassword?.message} hint="12–128 characters with at least one letter and one digit.">
          <input type="password" autoComplete="new-password" {...register("newPassword")} />
        </FormField>
        <FormField label="Confirm new password" error={errors.confirmPassword?.message}>
          <input type="password" autoComplete="new-password" {...register("confirmPassword")} />
        </FormField>
        <div className="acts" style={{ justifyContent: "flex-end" }}>
          {forced ? (
            <button type="button" className="btn" onClick={() => logout().then(() => navigate("/login", { replace: true }))}>
              Sign out
            </button>
          ) : (
            <button type="button" className="btn" onClick={() => navigate(-1)}>
              Cancel
            </button>
          )}
          <button type="submit" className="btn primary" disabled={isSubmitting}>
            {isSubmitting ? "Saving…" : "Change password"}
          </button>
        </div>
      </form>
    </div>
  );
}
