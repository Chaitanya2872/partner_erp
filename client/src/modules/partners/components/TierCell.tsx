import { ArrowRight } from "lucide-react";
import { Badge } from "@/components/ui/Badge";
import type { TierRef } from "../types/partner.types";

/**
 * Assigned tier, plus "→ calculated" when the revenue points elsewhere (BR-P11). The assigned tier is what the
 * fees use until a tier review changes it.
 */
export function TierCell({ tier, calculated, programme, grace }: { tier: TierRef; calculated: TierRef; programme: boolean; grace: boolean }) {
  const differs = tier.level !== calculated.level;
  return (
    <div className="flex flex-wrap items-center gap-1.5 whitespace-nowrap">
      <span className="font-medium">{tier.name}</span>
      {differs ? (
        <span className="inline-flex items-center gap-1 text-muted-foreground" title="Tier the last 365 days of revenue qualifies for">
          <ArrowRight className="size-3.5" aria-hidden="true" />
          <span className="sr-only">calculated</span>
          {calculated.name}
        </span>
      ) : null}
      {programme ? <Badge tone="outline">Programme</Badge> : null}
      {grace ? <Badge tone="warning">Grace</Badge> : null}
    </div>
  );
}
