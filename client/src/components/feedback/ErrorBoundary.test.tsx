import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { ErrorBoundary } from "./ErrorBoundary";
import { ErrorState } from "./States";
import { ApiRequestError } from "@/services/api";

let shouldThrow = true;
function Bomb({ message = "kaboom: internal detail" }: { message?: string }) {
  if (shouldThrow) throw new Error(message);
  return <p>Recovered content</p>;
}

beforeEach(() => {
  shouldThrow = true;
  // React logs caught render errors; keep the test output readable.
  vi.spyOn(console, "error").mockImplementation(() => undefined);
});
afterEach(() => vi.restoreAllMocks());

describe("error boundary", () => {
  it("replaces a crashed subtree with a recoverable message, without internals", () => {
    render(
      <ErrorBoundary>
        <Bomb />
      </ErrorBoundary>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("This page failed to load");
    expect(screen.queryByText(/kaboom/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Reload/ })).toBeInTheDocument();
  });

  it("retries rendering with Try again", async () => {
    render(
      <ErrorBoundary>
        <Bomb />
      </ErrorBoundary>,
    );
    shouldThrow = false;
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(screen.getByText("Recovered content")).toBeInTheDocument();
  });

  it("resets when the reset key changes (navigation)", async () => {
    function Harness() {
      const [key, setKey] = useState("/a");
      return (
        <>
          <button type="button" onClick={() => setKey("/b")}>
            Navigate
          </button>
          <ErrorBoundary resetKey={key}>
            <Bomb />
          </ErrorBoundary>
        </>
      );
    }
    render(<Harness />);
    expect(screen.getByRole("alert")).toBeInTheDocument();
    shouldThrow = false;
    await userEvent.click(screen.getByRole("button", { name: "Navigate" }));
    expect(screen.getByText("Recovered content")).toBeInTheDocument();
  });

  it("explains a failed chunk download as a new version", () => {
    render(
      <ErrorBoundary>
        <Bomb message="Failed to fetch dynamically imported module: /assets/UsersPage.js" />
      </ErrorBoundary>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("A new version is available");
  });
});

describe("API error presentation", () => {
  it.each([
    [new ApiRequestError(0, "NETWORK_ERROR", "x"), "Can't reach the server"],
    [new ApiRequestError(403, "FORBIDDEN", "x"), "You don't have permission"],
    [new ApiRequestError(404, "NOT_FOUND", "x"), "Not found"],
    [new ApiRequestError(500, "INTERNAL", "NullPointerException at Foo.java:12", [], "req-123"), "The server had a problem"],
  ])("describes %s for users", (error, title) => {
    render(<ErrorState error={error} />);
    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent(title);
    expect(alert).not.toHaveTextContent("NullPointerException");
  });

  it("shows the request reference for support", () => {
    render(<ErrorState error={new ApiRequestError(500, "INTERNAL", "x", [], "req-123")} />);
    expect(screen.getByText("Reference: req-123")).toBeInTheDocument();
  });
});
