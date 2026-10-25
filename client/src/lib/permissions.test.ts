import { describe, expect, it } from "vitest";
import { canPerform, canRead, canWrite } from "./permissions";
import { makeMe, row } from "@/test/fixtures";

describe("permission helpers", () => {
  it("reads authorities: write implies read, but not sensitive actions", () => {
    const me = makeMe("ADMIN", { modules: [row("LEADS", { write: true, dataScope: "ALL" })] });
    expect(canRead(me, "LEADS")).toBe(true);
    expect(canWrite(me, "LEADS")).toBe(true);
    expect(canPerform(me, "LEADS", "DELETE")).toBe(false);
    expect(canPerform(me, "LEADS", "EXPORT")).toBe(false);
  });

  it("denies everything without a user or module", () => {
    expect(canRead(null, "DASHBOARD")).toBe(false);
    const me = makeMe("USER");
    expect(canRead(me, "LEADS")).toBe(false);
    expect(canWrite(me, "PRODUCTS")).toBe(false);
    expect(canPerform(me, "PAYOUTS", "MARK_PAYOUT_PAID")).toBe(false);
  });

  it("binds module actions to their module", () => {
    const me = makeMe("ADMIN", {}, ["PAYOUTS:MARK_PAYOUT_PAID"]);
    expect(canPerform(me, "PAYOUTS", "MARK_PAYOUT_PAID")).toBe(true);
    expect(canPerform(me, "PAYMENTS", "MARK_PAYOUT_PAID")).toBe(false);
  });

  it("gives Super Admin every module and action", () => {
    const me = makeMe("SUPER_ADMIN");
    expect(canWrite(me, "ROLE_MANAGEMENT")).toBe(true);
    expect(canPerform(me, "USER_MANAGEMENT", "MANAGE_PERMISSIONS")).toBe(true);
    expect(canPerform(me, "PRODUCTS", "VIEW_INTERNAL_B2C")).toBe(true);
  });
});
