export function formatDateTime(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" }) : "—";
}
