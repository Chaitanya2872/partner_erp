import { ApiRequestError } from "@/services/api";

export type ErrorKind = "network" | "validation" | "unauthorized" | "forbidden" | "not-found" | "conflict" | "server" | "unknown";

export interface DescribedError {
  kind: ErrorKind;
  title: string;
  message: string;
  /** Server correlation id for support, when the server returned one. */
  requestId?: string;
}

/**
 * Turns any thrown value into a user-facing title and message. Server messages are used for validation and
 * conflict errors (they are written for users); everything else gets a fixed message, so stack traces and
 * internal details never reach the screen.
 */
export function describeError(error: unknown): DescribedError {
  if (!(error instanceof ApiRequestError)) {
    return { kind: "unknown", title: "Something went wrong", message: "An unexpected error occurred. Try again." };
  }
  const requestId = error.requestId;
  if (error.code === "NETWORK_ERROR" || error.status === 0) {
    return {
      kind: "network",
      title: "Can't reach the server",
      message: "Check your connection and try again.",
    };
  }
  if (error.status === 401) {
    return { kind: "unauthorized", title: "Session ended", message: "Sign in again to continue.", requestId };
  }
  if (error.status === 403) {
    return {
      kind: "forbidden",
      title: "You don't have permission",
      message: "Your account is not allowed to do this. Ask an administrator if you need access.",
      requestId,
    };
  }
  if (error.status === 404) {
    return { kind: "not-found", title: "Not found", message: "The record does not exist or was removed.", requestId };
  }
  if (error.status === 409) {
    return { kind: "conflict", title: "Changed by someone else", message: error.message, requestId };
  }
  if (error.status === 400 || error.status === 422) {
    return { kind: "validation", title: "Check the highlighted fields", message: error.message, requestId };
  }
  if (error.status >= 500) {
    return {
      kind: "server",
      title: "The server had a problem",
      message: "Your change may not have been saved. Try again in a moment.",
      requestId,
    };
  }
  return { kind: "unknown", title: "Request failed", message: error.message, requestId };
}

/** One-line message for toasts. */
export function errorMessage(error: unknown): string {
  const described = describeError(error);
  return described.kind === "validation" || described.kind === "conflict" || described.kind === "unknown"
    ? described.message
    : `${described.title}. ${described.message}`;
}

/** A failed lazy chunk usually means a new version was deployed while the tab was open. */
export function isChunkLoadError(error: unknown): boolean {
  return (
    error instanceof Error &&
    /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module/i.test(error.message)
  );
}
