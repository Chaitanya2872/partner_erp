import { describe, expect, it } from "vitest";
import { catalog, row } from "@/test/fixtures";
import {
  grantable,
  missingScopes,
  sensitiveAdditions,
  setAction,
  setFlag,
  toDraft,
  toggleFlag,
  toRequest,
} from "./permissionMatrix";
import { emptyRow } from "./permissionMatrix";

const def = (code: string) => catalog.find((m) => m.code === code)!;

describe("permission matrix dependencies", () => {
  it("enabling Write enables Read but nothing sensitive", () => {
    const next = toggleFlag(emptyRow("LEADS"), "write", true);
    expect(next).toMatchObject({ read: true, write: true, delete: false, export: false, approve: false });
  });

  it("enabling Delete enables Write and Read", () => {
    expect(toggleFlag(emptyRow("LEADS"), "delete", true)).toMatchObject({ read: true, write: true, delete: true });
  });

  it("disabling Read clears every dependent flag and the scope", () => {
    const full = row("LEADS", { write: true, delete: true, export: true, approve: true, dataScope: "ALL" });
    expect(toggleFlag(full, "read", false)).toEqual(emptyRow("LEADS"));
  });

  it("disabling Write clears Delete only", () => {
    const r = toggleFlag(row("LEADS", { write: true, delete: true, export: true }), "write", false);
    expect(r).toMatchObject({ read: true, write: false, delete: false, export: true });
  });

  it("removing module read drops its special actions; granting an action enables module read", () => {
    let draft = toDraft(catalog, [row("PAYOUTS", { dataScope: "ALL" })], ["MARK_PAYOUT_PAID"]);
    draft = setFlag(draft, catalog, "PAYOUTS", "read", false);
    expect(draft.actions).toEqual([]);
    draft = setAction(draft, catalog, "VIEW_INTERNAL_B2C", true);
    expect(draft.modules.find((m) => m.moduleCode === "PRODUCTS")?.read).toBe(true);
    expect(draft.actions).toEqual(["VIEW_INTERNAL_B2C"]);
  });

  it("never picks a data scope silently", () => {
    let draft = toDraft(catalog, [], []);
    draft = setFlag(draft, catalog, "LEADS", "read", true);
    expect(missingScopes(draft, catalog)).toEqual(["LEADS"]);
    expect(toRequest(draft, catalog).modules).toEqual([{ ...emptyRow("LEADS"), read: true, dataScope: null }]);
  });

  it("mirrors the server's role ceilings", () => {
    expect(grantable("ADMIN", def("ROLE_MANAGEMENT"))).toBe(true);
    expect(grantable("USER", def("USER_MANAGEMENT"))).toBe(false);
    expect(grantable("ADMIN", def("USER_MANAGEMENT"))).toBe(true);
  });

  it("lists sensitive additions for confirmation", () => {
    const before = toDraft(catalog, [row("LEADS", { dataScope: "SELF" })], []);
    let after = setFlag(before, catalog, "LEADS", "delete", true);
    after = setAction(after, catalog, "MARK_PAYOUT_PAID", true);
    expect(sensitiveAdditions(before, after, catalog)).toEqual(["Leads: delete", "Mark payout paid"]);
  });
});
