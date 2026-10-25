import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRight, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { Dialog } from "@/components/ui/Dialog";
import { Alert } from "@/components/ui/Alert";
import { Badge, type BadgeTone } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Checkbox } from "@/components/ui/Checkbox";
import { ErrorState, LoadingState } from "@/components/feedback/States";
import { ApiRequestError } from "@/services/api";
import { errorMessage } from "@/lib/errors";
import { queryKeys } from "@/lib/queryKeys";
import { formatCurrency, formatDate } from "@/utils/format";
import { partnerService } from "../services/partnerService";
import type { TierReviewAction, TierReviewItem } from "../types/partner.types";

const ACTION_TONE: Record<TierReviewAction, BadgeTone> = {
  UPGRADE: "success",
  AUTO_UPGRADE: "success",
  DOWNGRADE: "danger",
  GRACE: "warning",
  RECOVERED: "info",
};

interface TierReviewDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** One partner (from its detail page); omitted reviews every partner that isn't inactive. */
  partnerIds?: string[];
}

/**
 * Manual tier review (BUSINESS_RULES §3.1, D-07): the server previews the changes, the user picks which to apply,
 * and the server recomputes each one before saving. If the figures moved in between it rejects the whole batch.
 */
export function TierReviewDialog({ open, onOpenChange, partnerIds }: TierReviewDialogProps) {
  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      wide
      title="Tier review"
      description="Compares each partner's paid revenue over the last 365 days with the tier thresholds."
    >
      {open ? <TierReviewBody partnerIds={partnerIds} onDone={() => onOpenChange(false)} /> : null}
    </Dialog>
  );
}

function TierReviewBody({ partnerIds, onDone }: { partnerIds?: string[]; onDone: () => void }) {
  const queryClient = useQueryClient();
  const preview = useQuery({
    queryKey: queryKeys.partners.tierReview(partnerIds ?? []),
    queryFn: () => partnerService.previewTierReview(partnerIds),
    gcTime: 0,
    staleTime: 0,
  });
  /** Partner ids left out of the apply; everything is selected by default. */
  const [excluded, setExcluded] = useState<Set<string>>(new Set());
  const [applying, setApplying] = useState(false);
  const [stale, setStale] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (preview.isPending) return <LoadingState label="Calculating tier changes…" />;
  if (preview.isError) return <ErrorState error={preview.error} onRetry={() => preview.refetch()} />;

  const { changes, reviewed, reviewDate } = preview.data;
  const selected = changes.filter((c) => !excluded.has(c.partnerId));

  const toggle = (id: string, include: boolean) =>
    setExcluded((prev) => {
      const next = new Set(prev);
      if (include) next.delete(id);
      else next.add(id);
      return next;
    });

  const refresh = async () => {
    setStale(false);
    setError(null);
    setExcluded(new Set());
    await preview.refetch();
  };

  const apply = async () => {
    setApplying(true);
    setError(null);
    try {
      const result = await partnerService.applyTierReview(
        selected.map((c) => ({ partnerId: c.partnerId, action: c.action!, toLevel: c.toLevel })),
      );
      toast.success(result.applied.length === 1 ? "Tier review applied to 1 partner." : `Tier review applied to ${result.applied.length} partners.`);
      await queryClient.invalidateQueries({ queryKey: queryKeys.partners.all });
      onDone();
    } catch (e) {
      if (e instanceof ApiRequestError && e.code === "TIER_REVIEW_STALE") setStale(true);
      else setError(errorMessage(e));
    } finally {
      setApplying(false);
    }
  };

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Review date {formatDate(reviewDate)} · {reviewed === 1 ? "1 partner reviewed" : `${reviewed} partners reviewed`} ·{" "}
        {changes.length === 1 ? "1 change proposed" : `${changes.length} changes proposed`}
      </p>

      {stale ? (
        <Alert
          tone="warning"
          title="The figures changed since this preview"
          action={
            <Button size="sm" onClick={refresh}>
              <RefreshCw aria-hidden="true" /> Preview again
            </Button>
          }
        >
          Nothing was applied. Run the preview again and check the new figures.
        </Alert>
      ) : null}
      {error ? <Alert tone="danger" title="Couldn't apply the review">{error}</Alert> : null}

      {changes.length === 0 ? (
        <Alert tone="success" title="No tier changes">
          Every reviewed partner is already on the right tier.
        </Alert>
      ) : (
        <ul className="divide-y divide-border rounded-lg border border-border" aria-label="Proposed tier changes">
          {changes.map((c) => (
            <ChangeRow key={c.partnerId} item={c} checked={!excluded.has(c.partnerId)} onCheckedChange={(v) => toggle(c.partnerId, v)} />
          ))}
        </ul>
      )}

      <div className="text-xs text-muted-foreground">
        Upgrades apply at once. A partner below their tier first gets a grace period; a second review below the tier drops them one step.
      </div>

      <div className="flex flex-col-reverse gap-2 border-t border-border pt-3 sm:flex-row sm:justify-end">
        <Button onClick={onDone} disabled={applying}>
          {changes.length === 0 ? "Close" : "Cancel"}
        </Button>
        {changes.length > 0 ? (
          <Button variant="primary" onClick={apply} disabled={selected.length === 0 || stale} loading={applying} loadingText="Applying…">
            Apply {selected.length === 1 ? "1 change" : `${selected.length} changes`}
          </Button>
        ) : null}
      </div>
    </div>
  );
}

function ChangeRow({ item, checked, onCheckedChange }: { item: TierReviewItem; checked: boolean; onCheckedChange: (checked: boolean) => void }) {
  const id = `tier-change-${item.partnerId}`;
  const moves = item.fromLevel !== item.toLevel;
  return (
    <li className="flex items-start gap-3 px-3 py-2.5">
      <Checkbox id={id} checked={checked} onCheckedChange={(v) => onCheckedChange(v === true)} className="mt-0.5" />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <label htmlFor={id} className="truncate font-medium">
            {item.partnerName}
          </label>
          {item.action ? <Badge tone={ACTION_TONE[item.action]}>{item.actionLabel}</Badge> : null}
          {item.programme ? <Badge tone="outline">Programme</Badge> : null}
        </div>
        <div className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
          <span className="inline-flex items-center gap-1">
            {item.fromTierName}
            {moves ? (
              <>
                <ArrowRight className="size-3.5" aria-label="to" /> <span className="font-medium text-foreground">{item.toTierName}</span>
              </>
            ) : (
              <span>(unchanged)</span>
            )}
          </span>
          <span className="font-mono tabular">{formatCurrency(item.revenue12m)} in 12 months</span>
        </div>
      </div>
    </li>
  );
}
