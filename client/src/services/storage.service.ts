/**
 * In-memory access-token holder. The access token is never written to localStorage/sessionStorage; a page
 * reload restores the session through the HttpOnly refresh cookie instead.
 */
let accessToken: string | null = null;

export const tokenStore = {
  get: (): string | null => accessToken,
  set: (token: string | null): void => {
    accessToken = token;
  },
  clear: (): void => {
    accessToken = null;
  },
};
