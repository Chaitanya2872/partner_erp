import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderApp } from "@/test/renderApp";
import { catalog, makeMe } from "@/test/fixtures";
import { authService } from "@/services/auth.service";
import { ApiRequestError } from "@/services/api";

vi.mock("@/services/auth.service", () => ({
  authService: { restore: vi.fn(), me: vi.fn(), modules: vi.fn(), login: vi.fn(), logout: vi.fn(), changePassword: vi.fn() },
}));

beforeEach(() => {
  vi.mocked(authService.restore).mockResolvedValue(null);
  vi.mocked(authService.modules).mockResolvedValue(catalog);
});

describe("login page", () => {
  it("validates the form before calling the server", async () => {
    renderApp("/login");
    await userEvent.click(await screen.findByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("Email is required.")).toBeInTheDocument();
    expect(screen.getByText("Password is required.")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Email"), "not-an-email");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("Enter a valid email address.")).toBeInTheDocument();
    expect(authService.login).not.toHaveBeenCalled();
  });

  it("toggles password visibility", async () => {
    renderApp("/login");
    const password = await screen.findByLabelText("Password");
    expect(password).toHaveAttribute("type", "password");
    await userEvent.click(screen.getByRole("button", { name: "Show password" }));
    expect(password).toHaveAttribute("type", "text");
    await userEvent.click(screen.getByRole("button", { name: "Hide password" }));
    expect(password).toHaveAttribute("type", "password");
  });

  it("shows the generic error for rejected credentials", async () => {
    vi.mocked(authService.login).mockRejectedValue(new ApiRequestError(401, "INVALID_CREDENTIALS", "Invalid email or password."));
    renderApp("/login");
    await userEvent.type(await screen.findByLabelText("Email"), "someone@example.test");
    await userEvent.type(screen.getByLabelText("Password"), "wrong-password-1");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid email or password.");
  });

  it("explains a locked account", async () => {
    vi.mocked(authService.login).mockRejectedValue(new ApiRequestError(423, "ACCOUNT_LOCKED", "locked"));
    renderApp("/login");
    await userEvent.type(await screen.findByLabelText("Email"), "someone@example.test");
    await userEvent.type(screen.getByLabelText("Password"), "whatever-1");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/locked/i);
  });

  it("signs in and lands on the first permitted module", async () => {
    vi.mocked(authService.login).mockResolvedValue(makeMe("USER"));
    renderApp("/login");
    await userEvent.type(await screen.findByLabelText("Email"), "user@example.test");
    await userEvent.type(screen.getByLabelText("Password"), "Correct-pass-12");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByRole("heading", { name: /Welcome, Test/ })).toBeInTheDocument();
    expect(authService.login).toHaveBeenCalledWith("user@example.test", "Correct-pass-12");
  });
});
