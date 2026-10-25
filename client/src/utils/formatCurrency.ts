const inr = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 });

/** Formats an INR amount for display; the backend remains the calculation authority. */
export function formatCurrency(value: number | null | undefined): string {
  return value === null || value === undefined ? "—" : inr.format(value);
}
