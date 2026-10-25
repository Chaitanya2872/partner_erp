export function LoadingSpinner({ label = "Loading…", fullPage }: { label?: string; fullPage?: boolean }) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="muted"
      style={{
        display: "flex",
        gap: 10,
        alignItems: "center",
        justifyContent: "center",
        padding: 28,
        minHeight: fullPage ? "100vh" : undefined,
      }}
    >
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}
