import type { ModuleInfo } from "@/modules/auth/types/auth.types";
import type { DataScope } from "@/types/common.types";
import type { PermissionDraft } from "../types/user.types";
import { grantable, setAction, setFlag, setScope, supports, type Flag } from "../lib/permissionMatrix";

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
}

export function PermissionMatrix({ catalog, draft, roleCode, readOnly, onChange, invalidScopes = [] }: PermissionMatrixProps) {
  const defs = new Map(catalog.map((m) => [m.code, m]));
  return (
    <div className="scroll">
      <table className="tbl matrix" aria-label="Permission matrix">
        <thead>
          <tr>
            <th scope="col">Module</th>
            {FLAGS.map((f) => (
              <th key={f.flag} scope="col">
                {f.label}
              </th>
            ))}
            <th scope="col">Scope</th>
            <th scope="col" style={{ textAlign: "left" }}>
              Special actions
            </th>
          </tr>
        </thead>
        <tbody>
          {draft.modules.map((row) => {
            const def = defs.get(row.moduleCode);
            if (!def) return null;
            const allowed = grantable(roleCode, def);
            const label = def.displayName;
            const moduleActions = def.actions.filter((a) => a.kind === "MODULE");
            return (
              <tr key={row.moduleCode}>
                <td>
                  <div style={{ fontWeight: 600 }}>{label}</div>
                  {!allowed ? (
                    <div className="muted small">{def.superAdminOnly ? "Super Admin only" : "Not available for this role"}</div>
                  ) : null}
                </td>
                {FLAGS.map(({ flag, label: flagLabel }) =>
                  supports(def, flag) ? (
                    <td key={flag}>
                      <input
                        type="checkbox"
                        aria-label={`${label} ${flagLabel}`}
                        checked={row[flag]}
                        disabled={readOnly || !allowed}
                        onChange={(e) => onChange(setFlag(draft, catalog, row.moduleCode, flag, e.target.checked))}
                      />
                    </td>
                  ) : (
                    <td key={flag} className="na" aria-label={`${flagLabel} not applicable`}>
                      —
                    </td>
                  ),
                )}
                <td>
                  {def.supportsScope ? (
                    <select
                      aria-label={`${label} scope`}
                      value={row.dataScope ?? ""}
                      disabled={readOnly || !allowed || !row.read}
                      aria-invalid={invalidScopes.includes(row.moduleCode) ? true : undefined}
                      style={invalidScopes.includes(row.moduleCode) ? { borderColor: "var(--bad)" } : undefined}
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
                    </select>
                  ) : (
                    <span className="na">—</span>
                  )}
                </td>
                <td style={{ textAlign: "left" }}>
                  {moduleActions.length === 0 ? (
                    <span className="na">—</span>
                  ) : (
                    <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
                      {moduleActions.map((action) => (
                        <label key={action.code} className="chk" title={action.description ?? undefined}>
                          <input
                            type="checkbox"
                            aria-label={action.displayName}
                            checked={draft.actions.includes(action.code)}
                            disabled={readOnly || !allowed || action.superAdminOnly}
                            onChange={(e) => onChange(setAction(draft, catalog, action.code, e.target.checked))}
                          />
                          <span>
                            {action.displayName}
                            {action.superAdminOnly ? <span className="muted small"> (Super Admin only)</span> : null}
                          </span>
                        </label>
                      ))}
                    </div>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
