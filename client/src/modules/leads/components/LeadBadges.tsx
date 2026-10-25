import { Badge } from "@/components/ui/Badge";
import { formatPercent } from "@/utils/format";
import { protectionText } from "../utils/leadDisplay";
import type { LeadSummary, SlaStatus } from "../types/lead.types";

/** Source SLA column: acknowledgement always, estimate once there is one or the clock is late (§4.1). */
export function SlaPills({ sla }: { sla: SlaStatus }) {
  return (
    <div className="flex flex-wrap gap-1">
      {sla.ackBreach ? (
        <Badge tone="danger" title={sla.ackHours !== null ? `${sla.ackHours} h to acknowledge (target ${sla.ackTargetHours} h)` : undefined}>
          Ack late
        </Badge>
      ) : (
        <Badge tone="success">Ack ok</Badge>
      )}
      {sla.estimateBreach ? (
        <Badge tone="danger" title={sla.estimateDays !== null ? `${sla.estimateDays} days to the first estimate (target ${sla.estimateTargetDays})` : undefined}>
          Estimate late
        </Badge>
      ) : sla.hasEstimate ? (
        <Badge tone="success">Estimate ok</Badge>
      ) : null}
    </div>
  );
}

/** Acknowledged registrations: the locked fee rate and the protection window. */
export function RegistrationSummary({ lead }: { lead: LeadSummary }) {
  return (
    <div className="space-y-0.5">
      <Badge tone="success">Acknowledged</Badge>
      <div className="whitespace-nowrap text-xs text-muted-foreground">
        {formatPercent(lead.lockedFeePercent)} locked · {protectionText(lead)}
      </div>
    </div>
  );
}

export function DuplicateBadge() {
  return (
    <Badge tone="warning" title="Same phone is protected for another partner">
      Possible duplicate
    </Badge>
  );
}
