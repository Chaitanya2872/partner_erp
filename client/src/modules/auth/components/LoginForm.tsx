import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Eye, EyeOff } from "lucide-react";
import { FormField } from "@/components/forms/FormField";
import { ApiRequestError } from "@/services/api";

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const schema = z.object({
  email: z.string().trim().min(1, "Email is required.").regex(EMAIL, "Enter a valid email address."),
  password: z.string().min(1, "Password is required."),
});

export type LoginValues = z.infer<typeof schema>;

const MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: "Invalid email or password.",
  ACCOUNT_LOCKED: "Too many failed attempts. The account is locked for a few minutes.",
  ACCOUNT_DISABLED: "This account is disabled. Contact your administrator.",
  NETWORK_ERROR: "The server could not be reached. Check your connection and try again.",
};

export function loginErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    return MESSAGES[error.code] ?? error.message;
  }
  return "Sign-in failed. Try again.";
}

export function LoginForm({ onSubmit }: { onSubmit: (values: LoginValues) => Promise<void> }) {
  const [showPassword, setShowPassword] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({ resolver: zodResolver(schema), defaultValues: { email: "", password: "" } });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      setFormError(loginErrorMessage(error));
    }
  });

  return (
    <form onSubmit={submit} noValidate style={{ display: "flex", flexDirection: "column", gap: 12 }}>
      {formError ? (
        <div className="notice bad" role="alert">
          {formError}
        </div>
      ) : null}
      <FormField label="Email" error={errors.email?.message}>
        <input type="email" autoComplete="username" autoFocus {...register("email")} />
      </FormField>
      <div className="fld">
        <label htmlFor="login-password">Password</label>
        <div className="pw">
          <input
            id="login-password"
            type={showPassword ? "text" : "password"}
            autoComplete="current-password"
            aria-invalid={errors.password ? true : undefined}
            aria-describedby={errors.password ? "login-password-error" : undefined}
            style={{ width: "100%" }}
            {...register("password")}
          />
          <button
            type="button"
            onClick={() => setShowPassword((v) => !v)}
            aria-label={showPassword ? "Hide password" : "Show password"}
            aria-pressed={showPassword}
          >
            {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
          </button>
        </div>
        {errors.password ? (
          <span id="login-password-error" className="ferr" role="alert">
            {errors.password.message}
          </span>
        ) : null}
      </div>
      <button type="submit" className="btn primary" disabled={isSubmitting} style={{ justifyContent: "center", padding: "9px 12px" }}>
        {isSubmitting ? (
          <>
            <span className="spinner" aria-hidden="true" style={{ width: 14, height: 14 }} /> Signing in…
          </>
        ) : (
          "Sign in"
        )}
      </button>
    </form>
  );
}
