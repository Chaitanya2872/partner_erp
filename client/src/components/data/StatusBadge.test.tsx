import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { StatusBadge } from "./StatusBadge";
import { STATUS_REGISTRY, statusDefinition } from "@/lib/status";

describe("status badges", () => {
  it("shows the label as text, so meaning never depends on colour", () => {
    render(<StatusBadge domain="lead" status="SITE_VISIT" />);
    const badge = screen.getByText("Site visit").closest("[data-status]");
    expect(badge).toHaveAttribute("data-status", "SITE_VISIT");
  });

  it("marks derived statuses for assistive technology", () => {
    render(<StatusBadge domain="invoice" status="OVERDUE" />);
    expect(screen.getByText("Overdue")).toBeInTheDocument();
    expect(screen.getByText("(calculated)")).toHaveClass("sr-only");
  });

  it("appends an optional qualifier", () => {
    render(<StatusBadge domain="user" status="ACTIVE" suffix="password reset pending" />);
    expect(screen.getByText(/password reset pending/)).toBeInTheDocument();
  });

  it("shows unknown codes neutrally instead of hiding them", () => {
    expect(statusDefinition("estimate", "ON_HOLD")).toEqual({ label: "On hold", tone: "neutral" });
  });

  it("covers exactly the backend lifecycle states", () => {
    expect(Object.keys(STATUS_REGISTRY.lead)).toEqual(["NEW", "REGISTERED", "PRESALES", "SITE_VISIT", "ESTIMATE_SENT", "NEGOTIATION", "WON", "LOST"]);
    expect(Object.keys(STATUS_REGISTRY.estimate).filter((k) => !STATUS_REGISTRY.estimate[k].derived)).toEqual([
      "DRAFT",
      "SENT",
      "ACCEPTED",
      "REJECTED",
      "INVOICED",
    ]);
    expect(Object.keys(STATUS_REGISTRY.payout).filter((k) => !STATUS_REGISTRY.payout[k].derived)).toEqual(["ACCRUED", "PAID"]);
    expect(Object.keys(STATUS_REGISTRY.invoice).filter((k) => !STATUS_REGISTRY.invoice[k].derived)).toEqual(["CANCELLED"]);
  });
});
