import { Component, type ErrorInfo, type ReactNode } from "react";
import { useLocation, useRouteError } from "react-router";
import { AlertOctagon, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { isChunkLoadError } from "@/lib/errors";

export function CrashFallback({ error, onReset }: { error: unknown; onReset?: () => void }) {
  const chunk = isChunkLoadError(error);
  return (
    <div role="alert" className="mx-auto flex max-w-lg flex-col items-center px-6 py-16 text-center">
      <span className="mb-3 inline-flex size-11 items-center justify-center rounded-full bg-danger-bg text-danger">
        <AlertOctagon className="size-5" aria-hidden="true" />
      </span>
      <h1 className="font-display text-xl font-semibold">{chunk ? "A new version is available" : "This page failed to load"}</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        {chunk
          ? "Reload to get the latest version of the application."
          : "Something unexpected went wrong while showing this page. Reload, and if it happens again, contact support."}
      </p>
      <div className="mt-4 flex gap-2">
        {onReset && !chunk ? <Button onClick={onReset}>Try again</Button> : null}
        <Button variant="primary" onClick={() => window.location.reload()}>
          <RefreshCw aria-hidden="true" /> Reload
        </Button>
      </div>
    </div>
  );
}

interface BoundaryProps {
  children: ReactNode;
  /** Changing this value clears the error (the shell passes the current path, so navigating away recovers). */
  resetKey?: string;
}

interface BoundaryState {
  error: unknown;
  resetKey?: string;
}

/** Catches rendering errors below it and shows a recoverable message instead of a blank screen or stack trace. */
export class ErrorBoundary extends Component<BoundaryProps, BoundaryState> {
  state: BoundaryState = { error: null, resetKey: this.props.resetKey };

  static getDerivedStateFromError(error: unknown): Partial<BoundaryState> {
    return { error: error ?? new Error("Unknown error") };
  }

  static getDerivedStateFromProps(props: BoundaryProps, state: BoundaryState): Partial<BoundaryState> | null {
    return props.resetKey !== state.resetKey ? { error: null, resetKey: props.resetKey } : null;
  }

  componentDidCatch(error: unknown, info: ErrorInfo): void {
    // Logged for developers; users see the fallback only.
    console.error("Unhandled UI error", error, info.componentStack);
  }

  render() {
    if (this.state.error) {
      return <CrashFallback error={this.state.error} onReset={() => this.setState({ error: null })} />;
    }
    return this.props.children;
  }
}

/** Error boundary that resets when the route changes (wraps page content inside the shell). */
export function RouteErrorBoundary({ children }: { children: ReactNode }) {
  const location = useLocation();
  return <ErrorBoundary resetKey={location.pathname}>{children}</ErrorBoundary>;
}

/** Router `errorElement`: errors thrown while matching or loading a route. */
export function RouterErrorPage() {
  const error = useRouteError();
  return <CrashFallback error={error} />;
}
