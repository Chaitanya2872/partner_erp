import type { ReactNode } from "react";

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <div style={{ fontWeight: 600, color: "var(--ink)" }}>{title}</div>
      {children ? <div style={{ marginTop: 4 }}>{children}</div> : null}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof Error ? error.message : "Something went wrong.";
  return (
    <div className="notice bad" role="alert" style={{ display: "flex", gap: 12, alignItems: "center", justifyContent: "space-between" }}>
      <span>{message}</span>
      {onRetry ? (
        <button type="button" className="btn sm" onClick={onRetry}>
          Retry
        </button>
      ) : null}
    </div>
  );
}
