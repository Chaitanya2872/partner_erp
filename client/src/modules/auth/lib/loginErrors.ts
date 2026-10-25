import { ApiRequestError } from "@/services/api";

const MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: "Invalid email or password.",
  ACCOUNT_LOCKED: "Too many failed attempts. The account is locked for a few minutes.",
  ACCOUNT_DISABLED: "This account is disabled. Contact your administrator.",
  NETWORK_ERROR: "The server could not be reached. Check your connection and try again.",
};

export function loginErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (MESSAGES[error.code]) return MESSAGES[error.code];
    if (error.status >= 500) return "Sign-in is unavailable right now. Try again in a moment.";
    return error.message;
  }
  return "Sign-in failed. Try again.";
}
