import { formatDate } from "@/utils/format";
import type { LeadSummary } from "../types/lead.types";

/** BR-L7: "to <date>" while protection lasts, otherwise "expired". */
export function protectionText(lead: Pick<LeadSummary, "protectionUntil" | "protectionActive">): string {
  if (!lead.protectionUntil) return "—";
  return lead.protectionActive ? `to ${formatDate(lead.protectionUntil)}` : "expired";
}
