import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Dialog } from "@/components/ui/Dialog";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { EmptyState, ErrorState, LoadingState } from "@/components/feedback/States";
import { formatDateTime, humanizeCode } from "@/utils/format";
import { queryKeys } from "@/lib/queryKeys";
import { userService } from "../services/userService";
import type { User } from "../types/user.types";

const PAGE_SIZE = 20;
const WARN = new Set(["LOGIN_FAILED", "ACCOUNT_LOCKED", "REFRESH_TOKEN_REUSED", "PASSWORD_RESET"]);

/**
 * The account's history from the audit log: sign-ins, lockouts, password, role, status and permission changes. The
 * IP address is shown only to viewers who may read the audit log (the server withholds it otherwise).
 */
export function UserActivityDialog({ user, onClose }: { user: User | null; onClose: () => void }) {
  const [page, setPage] = useState(0);
  const id = user?.id ?? "";
  const activity = useQuery({
    queryKey: queryKeys.users.activity(id, page),
    queryFn: ({ signal }) => userService.activity(id, page, PAGE_SIZE, signal),
    enabled: !!user,
    placeholderData: keepPreviousData,
  });
  const meta = activity.data?.meta;

  return (
    <Dialog
      open={!!user}
      onOpenChange={(open) => {
        if (!open) {
          setPage(0);
          onClose();
        }
      }}
      title="Account activity"
      description={user ? `${user.fullName} · ${user.email}` : undefined}
      wide
    >
      {activity.isPending ? (
        <LoadingState label="Loading activity…" />
      ) : activity.isError ? (
        <ErrorState error={activity.error} onRetry={() => activity.refetch()} />
      ) : activity.data.content.length === 0 ? (
        <EmptyState title="No activity recorded" />
      ) : (
        <div className="space-y-3">
          <ol className="divide-y divide-border rounded-md border border-border" aria-label="Activity, newest first">
            {activity.data.content.map((e) => (
              <li key={e.id} className="flex flex-wrap items-start justify-between gap-2 px-3 py-2 text-sm">
                <div className="min-w-0">
                  <Badge tone={WARN.has(e.action) ? "warning" : "neutral"}>{humanizeCode(e.action)}</Badge>
                  <div className="mt-1 text-xs text-muted-foreground">
                    {e.actorName ? `By ${e.actorName}` : "System / anonymous"}
                    {e.ipAddress ? <span className="ml-2 font-mono">{e.ipAddress}</span> : null}
                  </div>
                </div>
                <span className="whitespace-nowrap text-xs text-muted-foreground">{formatDateTime(e.createdAt)}</span>
              </li>
            ))}
          </ol>
          {meta && meta.totalPages > 1 ? (
            <div className="flex items-center justify-between gap-2 text-sm">
              <span className="text-muted-foreground">
                Page {meta.page + 1} of {meta.totalPages}
              </span>
              <div className="flex gap-2">
                <Button size="sm" disabled={page === 0 || activity.isFetching} onClick={() => setPage((p) => p - 1)}>
                  Newer
                </Button>
                <Button size="sm" disabled={page + 1 >= meta.totalPages || activity.isFetching} onClick={() => setPage((p) => p + 1)}>
                  Older
                </Button>
              </div>
            </div>
          ) : null}
        </div>
      )}
    </Dialog>
  );
}
