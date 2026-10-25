import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from "axios";
import type { ApiEnvelope, ApiErrorBody, FieldError, Page } from "@/types/api.types";
import { tokenStore } from "./storage.service";

export const API_BASE = "/api/v1";

/** Cookie-authenticated endpoints require this header (cross-site forms cannot set it). */
const CSRF_HEADERS = { "X-Requested-With": "XMLHttpRequest" };

/** Error raised for every failed API call, carrying the backend's error code and field errors. */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldError[];
  /** Correlation id from the server (also in the X-Request-Id header), shown so support can find the log entry. */
  readonly requestId?: string;

  constructor(status: number, code: string, message: string, fieldErrors: FieldError[] = [], requestId?: string) {
    super(message);
    this.name = "ApiRequestError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.requestId = requestId;
  }
}

export const http = axios.create({ baseURL: API_BASE, withCredentials: true, timeout: 30_000 });

type SessionListener = (reason: "expired") => void;
const sessionListeners = new Set<SessionListener>();

/** Notified when the session cannot be renewed (refresh rejected); the auth context signs the user out. */
export function onSessionEnded(listener: SessionListener): () => void {
  sessionListeners.add(listener);
  return () => sessionListeners.delete(listener);
}

/** Clears the access token and tells listeners the session is over (the server rejected the refresh cookie). */
export function endSession(): void {
  tokenStore.clear();
  sessionListeners.forEach((listener) => listener("expired"));
}

interface RefreshPayload {
  accessToken: string;
}

let inFlightRefresh: Promise<string | null> | null = null;

/**
 * Exchanges the refresh cookie for a new access token. Single-flight within the tab, and serialised across
 * tabs with the Web Locks API so two tabs never present the same (rotating) refresh token: a concurrent
 * replay would be treated as token theft by the server and end the session.
 */
export function refreshAccessToken(): Promise<string | null> {
  if (!inFlightRefresh) {
    const run = async (): Promise<string | null> => {
      try {
        const res = await axios.post<ApiEnvelope<RefreshPayload>>(`${API_BASE}/auth/refresh`, null, {
          withCredentials: true,
          headers: CSRF_HEADERS,
        });
        const token = res.data.data?.accessToken ?? null;
        tokenStore.set(token);
        return token;
      } catch {
        tokenStore.clear();
        return null;
      }
    };
    const locks = typeof navigator !== "undefined" ? navigator.locks : undefined;
    const exec = locks ? locks.request("iotiq-session-refresh", run) : run();
    inFlightRefresh = exec.finally(() => {
      inFlightRefresh = null;
    });
  }
  return inFlightRefresh;
}

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStore.get();
  if (token) {
    config.headers.set("Authorization", `Bearer ${token}`);
  }
  config.headers.set("X-Requested-With", "XMLHttpRequest");
  return config;
});

const NO_RETRY = ["/auth/login", "/auth/refresh", "/auth/logout"];

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiEnvelope<unknown>>) => {
    const original = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined;
    const status = error.response?.status;
    const isAuthCall = NO_RETRY.some((path) => original?.url?.endsWith(path));
    if (status === 401 && original && !original._retried && !isAuthCall) {
      original._retried = true;
      const token = await refreshAccessToken();
      if (token) {
        return http(original);
      }
      endSession();
    }
    throw toApiError(error);
  },
);

export function toApiError(error: unknown): ApiRequestError {
  if (error instanceof ApiRequestError) {
    return error;
  }
  if (axios.isCancel(error)) {
    return new ApiRequestError(0, "CANCELLED", "The request was cancelled.");
  }
  if (axios.isAxiosError(error)) {
    const body = (error.response?.data as ApiEnvelope<unknown> | undefined)?.error as ApiErrorBody | undefined;
    if (body) {
      return new ApiRequestError(body.status, body.code, body.detail ?? body.title ?? "Request failed.", body.fieldErrors ?? [], body.requestId);
    }
    if (!error.response) {
      return new ApiRequestError(0, "NETWORK_ERROR", "The server could not be reached. Check your connection.");
    }
    return new ApiRequestError(error.response.status, "HTTP_" + error.response.status, "Request failed.");
  }
  return new ApiRequestError(0, "UNKNOWN", error instanceof Error ? error.message : "Unexpected error.");
}

/**
 * Unwraps `data` from the envelope. Pass the `signal` TanStack Query provides so superseded requests (e.g. typing
 * in a search box) are cancelled.
 */
export async function apiGet<T>(url: string, params?: Record<string, unknown>, signal?: AbortSignal): Promise<T> {
  const res = await http.get<ApiEnvelope<T>>(url, { params: cleanParams(params), signal });
  return res.data.data as T;
}

export async function apiPage<T>(url: string, params?: Record<string, unknown>, signal?: AbortSignal): Promise<Page<T>> {
  const res = await http.get<ApiEnvelope<T[]>>(url, { params: cleanParams(params), signal });
  return {
    content: res.data.data ?? [],
    meta: res.data.meta ?? { page: 0, size: 0, totalElements: 0, totalPages: 0 },
  };
}

export async function apiSend<T>(method: "post" | "put" | "patch", url: string, body?: unknown): Promise<T> {
  const res = await http.request<ApiEnvelope<T>>({ method, url, data: body });
  return res.data.data as T;
}

function cleanParams(params?: Record<string, unknown>): Record<string, unknown> | undefined {
  if (!params) {
    return undefined;
  }
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ""));
}

export { CSRF_HEADERS };
