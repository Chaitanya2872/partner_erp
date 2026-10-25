import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { LogIn } from "lucide-react";
import { FormError, FormField } from "@/components/forms/FormField";
import { PasswordInput } from "@/components/forms/inputs";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { emailSchema } from "@/lib/validation";
import { loginErrorMessage } from "../lib/loginErrors";

const schema = z.object({
  email: emailSchema,
  password: z.string().min(1, "Password is required."),
});

export type LoginValues = z.infer<typeof schema>;

export function LoginForm({ onSubmit }: { onSubmit: (values: LoginValues) => Promise<void> }) {
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
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      <FormError>{formError}</FormError>
      <FormField label="Email" error={errors.email?.message}>
        <Input type="email" autoComplete="username" autoFocus inputMode="email" {...register("email")} />
      </FormField>
      <FormField label="Password" error={errors.password?.message}>
        <PasswordInput autoComplete="current-password" {...register("password")} />
      </FormField>
      <Button type="submit" variant="primary" size="lg" loading={isSubmitting} loadingText="Signing in…" className="mt-1 w-full">
        <LogIn aria-hidden="true" /> Sign in
      </Button>
    </form>
  );
}
