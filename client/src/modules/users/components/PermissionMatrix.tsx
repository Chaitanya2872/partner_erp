import { Fragment } from "react";
import { Lock } from "lucide-react";
import type { ModuleInfo } from "@/modules/auth/types/auth.types";
import type { DataScope } from "@/types/common.types";
import { Checkbox } from "@/components/ui/Checkbox";
import { Select } from "@/components/ui/Input";
import { cn } from "@/lib/cn";
import type { PermissionDraft } from "../types/user.types";
import { grantable, groupOf, MATRIX_GROUP_ORDER, setAction, setFlag, setScope, supports, type Flag } from "../lib/permissionMatrix";

const FLAGS: { flag: Flag; label: string }[] = [
  { flag: "read", label: "Read" },
  { flag: "write", label: "Write" },
  { flag: "delete", label: "Delete" },
  { flag: "export", label: "Export" },
  { flag: "approve", label: "Approve" },
];

const SCOPES: { value: DataScope; label: string }[] = [
  { value: "SELF", label: "Self" },
  { value: "TEAM", label: "Team" },
  { value: "ALL", label: "All" },
];

interface PermissionMatrixProps {
  catalog: ModuleInfo[];
  draft: PermissionDraft;
  roleCode: string;
  readOnly: boolean;
  onChange: (draft: PermissionDraft) => void;
  /** Module codes highlighted because their scope still has to be chosen. */
  invalidScopes?: string[];
  /** Hides the special-actions column (role defaults hold modules only). */
  hideActions?: boolean;
  /** Module codes marked as differing from the role default. */
  overrides?: string[];
}

/**
 * Module × permission grid. Dependencies are applied as boxes change (see lib/permissionMatrix): any permission
 * turns Read on, Delete turns Write on, clearing Read clears the row. Modules or actions the role may never hold
 * are disabled with the reason shown; the server enforces the same rules.
 */
export function PermissionMatrix({ catalog, draft, roleCode, readOnly, onChange, invalidScopes = [], hideActions = false, overrides = [] }: PermissionMatrixProps) {
  const defs = new Map(catalog.map((m) => [m.code, m]));
  const groups = MATRIX_GROUP_ORDER.map((group) => ({
    group,
    rows: draft.modules.filter((row) => defs.has(row.moduleCode) && groupOf(row.moduleCode) === group),
  })).filter((g) => g.rows.length > 0);
  const colCount = FLAGS.length + (hideActions ? 2 : 3);

  return (
    <div role="region" aria-label="Permission matrix, scrolls horizontally" tabIndex={0} className="relative overflow-x-auto rounded-lg border border-border bg-surface shadow-xs focus-visible:outline-2 focus-visible:outline-ring">
      <table className="w-full min-w-[54rem] border-collapse text-sm" aria-label="Permission matrix">
        <thead>
          <tr className="border-b border-border">
            <th scope="col" className="sticky left-0 z-10 bg-surface px-4 py-2.5 text-left text-2xs font-semibold uppercase tracking-wider text-muted-foreground">
              Module
            </th>
            {FLAGS.map((f) => (
              <th key={f.flag} scope="col" className="w-20 px-2 py-2.5 text-center text-2xs font-semibold uppercase tracking-wider text-muted-foreground">
                {f.label}
              </th>
            ))}
            <th scope="col" className="w-32 px-2 py-2.5 text-left text-2xs font-semibold uppercase tracking-wider text-muted-foreground">
              Scope
            </th>
            {hideActions ? null : (
              <th scope="col" className="px-3 py-2.5 text-left text-2xs font-semibold uppercase tracking-wider text-muted-foreground">
                Special actions
              </th>
            )}
          </tr>
        </thead>
        <tbody>
          {groups.map(({ group, rows }) => (
            <Fragment key={group}>
              <tr className="border-b border-border bg-surface-sunken">
                <th
                  scope="colgroup"
                  colSpan={colCount}
                  className="sticky left-0 px-4 py-1.5 text-left text-2xs font-semibold uppercase tracking-[0.14em] text-muted-foreground"
                >
                  {group}
                </th>
              </tr>
              {rows.map((row) => {
                const def = defs.get(row.moduleCode)!;
                const allowed = grantable(roleCode, def);
                const label = def.displayName;
                const moduleActions = def.actions.filter((a) => a.kind === "MODULE");
                const scopeInvalid = invalidScopes.includes(row.moduleCode);
                return (
                  <tr key={row.moduleCode} className={cn("border-b border-border last:border-b-0", !allowed && "text-muted-foreground")}>
                    <th scope="row" className="sticky left-0 z-10 bg-surface px-4 py-2.5 text-left font-normal">
                      <div className="font-medium text-foreground">
                        {label}
                        {overrides.includes(row.moduleCode) ? (
                          <span className="ml-1.5 rounded-sm bg-warning-bg px-1 py-0.5 text-2xs font-medium text-warning">Differs from role</span>
                        ) : null}
                      </div>
                      {!allowed ? (
                        <div className="mt-0.5 flex items-center gap-1 text-xs text-muted-foreground">
                          <Lock className="size-3" aria-hidden="true" />
                          {def.superAdminOnly ? "Super Admin only" : "Not available for this role"}
                        </div>
                      ) : null}
                    </th>
                    {FLAGS.map(({ flag, label: flagLabel }) =>
                      supports(def, flag) ? (
                        <td key={flag} className="px-2 py-2.5 text-center">
                          <Checkbox
                            aria-label={`${label} ${flagLabel}`}
                            checked={row[flag]}
                            disabled={readOnly || !allowed}
                            onCheckedChange={(checked) => onChange(setFlag(draft, catalog, row.moduleCode, flag, checked === true))}
                            className="mx-auto"
                          />
                        </td>
                      ) : (
                        <td key={flag} className="px-2 py-2.5 text-center text-muted-foreground">
                          <span aria-hidden="true">—</span>
                          <span className="sr-only">{flagLabel} not applicable</span>
                        </td>
                      ),
                    )}
                    <td className="px-2 py-2">
                      {def.supportsScope ? (
                        <Select
                          aria-label={`${label} scope`}
                          value={row.dataScope ?? ""}
                          disabled={readOnly || !allowed || !row.read}
                          aria-invalid={scopeInvalid ? true : undefined}
                          className="h-8 text-sm"
                          onChange={(e) => onChange(setScope(draft, row.moduleCode, e.target.value as DataScope))}
                        >
                          <option value="" disabled>
                            {row.read ? "Choose…" : "—"}
                          </option>
                          {SCOPES.map((s) => (
                            <option key={s.value} value={s.value}>
                              {s.label}
                            </option>
                          ))}
                        </Select>
                      ) : (
                        <span className="px-2 text-muted-foreground" aria-hidden="true">
                          —
                        </span>
                      )}
                    </td>
                    {hideActions ? null : (
                      <td className="px-3 py-2.5">
                        {moduleActions.length === 0 ? (
                          <span className="text-muted-foreground" aria-hidden="true">
                            —
                          </span>
                        ) : (
                          <div className="flex flex-col gap-1.5">
                            {moduleActions.map((action) => {
                              const disabled = readOnly || !allowed || action.superAdminOnly;
                              return (
                                <label
                                  key={action.code}
                                  className={cn("inline-flex items-center gap-2 text-sm", disabled ? "cursor-not-allowed" : "cursor-pointer")}
                                  title={action.description ?? undefined}
                                >
                                  <Checkbox
                                    aria-label={action.displayName}
                                    checked={draft.actions.includes(action.code)}
                                    disabled={disabled}
                                    onCheckedChange={(checked) => onChange(setAction(draft, catalog, action.code, checked === true))}
                                  />
                                  <span className={cn(disabled && "text-muted-foreground")}>
                                    {action.displayName}
                                    {action.superAdminOnly ? <span className="text-xs"> (Super Admin only)</span> : null}
                                  </span>
                                </label>
                              );
                            })}
                          </div>
                        )}
                      </td>
                    )}
                  </tr>
                );
              })}
            </Fragment>
          ))}
        </tbody>
      </table>
    </div>
  );
}
