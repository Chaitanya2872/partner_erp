import type { ReactNode } from "react";
import { Link } from "react-router";
import { CheckCircle2, Clock, Inbox, Loader2, RefreshCw, SearchX, ShieldAlert, WifiOff, XCircle, type LucideIcon } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { describeError } from "@/lib/errors";
import { cn } from "@/lib/cn";

interface StateFrameProps {
  icon: LucideIcon;
  iconClassName?: string;
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
  role?: "status" | "alert";
  /** Heading level for full-page states (h1) versus inline states (plain text). */
  as?: "h1" | "h2" | "div";
}

function StateFrame({ icon: Icon, iconClassName, title, description, action, className, role, as = "div" }: StateFrameProps) {
  const Title = as;
  return (
    <div role={role} className={cn("flex flex-col items-center px-6 py-10 text-center", className)}>
      <span className={cn("mb-3 inline-flex size-11 items-center justify-center rounded-full bg-secondary", iconClassName)}>
        <Icon className="size-5" aria-hidden="true" />
      </span>
      <Title className={cn("font-semibold text-foreground", as === "div" ? "text-sm" : "font-display text-xl")}>{title}</Title>
      {description ? <div className="mt-1 max-w-md text-sm text-muted-foreground">{description}</div> : null}
      {action ? <div className="mt-4 flex flex-wrap justify-center gap-2">{action}</div> : null}
    </div>
  );
}

/** Nothing to show (no records, or no matches for the current filters). */
export function EmptyState({
  title,
  children,
  action,
  icon = Inbox,
  onClearFilters,
}: {
  title: string;
  children?: ReactNode;
  action?: ReactNode;
  icon?: LucideIcon;
  /** For "no matches" states: adds a Clear filters button so the user is never stuck on an empty result. */
  onClearFilters?: () => void;
}) {
  return (
    <StateFrame
      icon={onClearFilters ? SearchX : icon}
      iconClassName="text-muted-foreground"
      title={title}
      description={children ?? (onClearFilters ? "Try a different search, or clear the filters." : undefined)}
      action={
        onClearFilters ? (
          <Button size="sm" onClick={onClearFilters}>
            Clear filters
          </Button>
        ) : (
          action
        )
      }
    />
  );
}

/** A failed request, described without internal details; offers Retry when given. */
export function ErrorState({ error, onRetry, className }: { error: unknown; onRetry?: () => void; className?: string }) {
  const described = describeError(error);
  const Icon = described.kind === "network" ? WifiOff : described.kind === "forbidden" ? ShieldAlert : XCircle;
  return (
    <StateFrame
      role="alert"
      icon={Icon}
      iconClassName="bg-danger-bg text-danger"
      title={described.title}
      className={className}
      description={
        <>
          {described.message}
          {described.requestId ? <div className="mt-1 font-mono text-xs">Reference: {described.requestId}</div> : null}
        </>
      }
      action={
        onRetry && described.kind !== "forbidden" ? (
          <Button size="sm" onClick={onRetry}>
            <RefreshCw aria-hidden="true" /> Retry
          </Button>
        ) : undefined
      }
    />
  );
}

/** Announced loading indicator. Use skeletons inside tables; this is for whole sections and pages. */
export function LoadingState({ label = "Loading…", fullPage, className }: { label?: string; fullPage?: boolean; className?: string }) {
  return (
    <div
      role="status"
      aria-live="polite"
      className={cn("flex items-center justify-center gap-2.5 p-8 text-sm text-muted-foreground", fullPage && "min-h-dvh", className)}
    >
      <Loader2 className="size-4 animate-spin text-accent-ink" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function SuccessState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return <StateFrame role="status" icon={CheckCircle2} iconClassName="bg-success-bg text-success" title={title} description={children} action={action} />;
}

/** Shown when the signed-in user lacks the module permission for a page (the API would refuse it anyway). */
export function PermissionDenied() {
  return (
    <StateFrame
      as="h1"
      icon={ShieldAlert}
      iconClassName="bg-warning-bg text-warning"
      title="You don't have access to this page"
      description="Ask an administrator to grant the permission you need."
      action={
        <Button asChild>
          <Link to="/">Go to your home page</Link>
        </Button>
      }
      className="py-16"
    />
  );
}

/** Clearly labelled placeholder for functionality delivered in a later phase; never presented as working. */
export function ComingSoon({ title, phase, children }: { title: string; phase: number; children?: ReactNode }) {
  return (
    <StateFrame
      role="status"
      icon={Clock}
      iconClassName="bg-info-bg text-info"
      title={`${title}: not yet available`}
      description={children ?? `This is delivered in Phase ${phase}.`}
    />
  );
}
