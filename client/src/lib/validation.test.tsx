import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { applyServerErrors, compareDecimal, decimalString, emailSchema, passwordPolicySchema } from "./validation";
import { FormField } from "@/components/forms/FormField";
import { Input } from "@/components/ui/Input";
import { ApiRequestError } from "@/services/api";

describe("shared schemas", () => {
  it("validates decimal strings without floating point", () => {
    const amount = decimalString({ min: "1", max: "100000", label: "Amount" });
    expect(amount.safeParse("125.50").success).toBe(true);
    expect(amount.safeParse("125.505").error?.issues[0].message).toBe("Enter a number with up to 2 decimals.");
    expect(amount.safeParse("").error?.issues[0].message).toBe("Amount is required.");
    expect(amount.safeParse("0.5").error?.issues[0].message).toBe("Amount must be at least 1.");
    expect(amount.safeParse("100000.01").error?.issues[0].message).toBe("Amount must be at most 100000.");
    expect(decimalString({ required: false }).safeParse("").success).toBe(true);
  });

  it("compares decimal strings exactly", () => {
    expect(compareDecimal("10", "9.99")).toBe(1);
    expect(compareDecimal("0010.50", "10.5")).toBe(0);
    expect(compareDecimal("99999999999999999.01", "99999999999999999.02")).toBe(-1);
  });

  it("mirrors the server email and password policies", () => {
    expect(emailSchema.safeParse(" someone@example.test ").success).toBe(true);
    expect(emailSchema.safeParse("nope").success).toBe(false);
    expect(passwordPolicySchema.safeParse("short1").success).toBe(false);
    expect(passwordPolicySchema.safeParse("no-digits-here-at-all").success).toBe(false);
    expect(passwordPolicySchema.safeParse("Long-enough-pass-1").success).toBe(true);
  });
});

const schema = z.object({ fullName: z.string().min(2, "Enter the full name."), email: emailSchema });
type Values = z.infer<typeof schema>;

function DemoForm({ onSubmit }: { onSubmit: (values: Values, setError: ReturnType<typeof useForm<Values>>["setError"]) => void }) {
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { fullName: "", email: "" } });
  return (
    <form onSubmit={handleSubmit((values) => onSubmit(values, setError))} noValidate>
      <FormField label="Full name" error={errors.fullName?.message} required hint="As it should appear on documents.">
        <Input {...register("fullName")} />
      </FormField>
      <FormField label="Email" error={errors.email?.message} required>
        <Input {...register("email")} />
      </FormField>
      <button type="submit">Save</button>
    </form>
  );
}

describe("form pattern", () => {
  it("shows accessible inline errors and focuses the first invalid field", async () => {
    render(<DemoForm onSubmit={vi.fn()} />);
    const name = screen.getByLabelText("Full name");
    expect(name).toHaveAttribute("aria-required", "true");
    expect(name).toHaveAccessibleDescription("As it should appear on documents.");

    await userEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(await screen.findByText("Enter the full name.")).toBeInTheDocument();
    expect(name).toHaveAttribute("aria-invalid", "true");
    expect(name).toHaveAccessibleDescription(/Enter the full name\./);
    expect(name).toHaveFocus();
  });

  it("maps server field errors back onto the form", async () => {
    const unmatched: string[][] = [];
    render(
      <DemoForm
        onSubmit={(_values, setError) => {
          const error = new ApiRequestError(400, "VALIDATION_FAILED", "Invalid", [
            { field: "email", message: "Email is already in use." },
            { field: "unknownField", message: "Something else." },
          ]);
          unmatched.push(applyServerErrors(error, setError, ["fullName", "email"]));
        }}
      />,
    );
    await userEvent.type(screen.getByLabelText("Full name"), "Asha Rao");
    await userEvent.type(screen.getByLabelText("Email"), "asha@example.test");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(await screen.findByText("Email is already in use.")).toBeInTheDocument();
    expect(unmatched[0]).toEqual(["Something else."]);
  });
});
