import { afterEach, describe, expect, it, vi } from "vitest";
import axios, { AxiosHeaders, type AxiosAdapter, type InternalAxiosRequestConfig } from "axios";
import { http, apiGet, onSessionEnded } from "./api";
import { tokenStore } from "./storage.service";

function respond(config: InternalAxiosRequestConfig, status: number, data: unknown) {
  const response = { data, status, statusText: String(status), headers: {}, config };
  if (status >= 400) {
    return Promise.reject(new axios.AxiosError("failed", "ERR", config, null, response as never));
  }
  return Promise.resolve(response);
}

const originalHttpAdapter = http.defaults.adapter;
const originalAxiosAdapter = axios.defaults.adapter;

afterEach(() => {
  http.defaults.adapter = originalHttpAdapter;
  axios.defaults.adapter = originalAxiosAdapter;
  tokenStore.clear();
});

describe("API client session handling", () => {
  it("refreshes once for concurrent 401s and retries with the new token", async () => {
    tokenStore.set("expired-token");
    let refreshCalls = 0;
    const refreshAdapter: AxiosAdapter = async (config) => {
      refreshCalls += 1;
      expect(new AxiosHeaders(config.headers).get("X-Requested-With")).toBe("XMLHttpRequest");
      expect(config.withCredentials).toBe(true);
      return respond(config, 200, { success: true, data: { accessToken: "fresh-token" } });
    };
    axios.defaults.adapter = refreshAdapter;
    http.defaults.adapter = async (config) => {
      const auth = new AxiosHeaders(config.headers).get("Authorization");
      return auth === "Bearer fresh-token"
        ? respond(config, 200, { success: true, data: "ok" })
        : respond(config, 401, { success: false, error: { status: 401, code: "UNAUTHENTICATED" } });
    };

    const results = await Promise.all([apiGet<string>("/a"), apiGet<string>("/b"), apiGet<string>("/c")]);
    expect(results).toEqual(["ok", "ok", "ok"]);
    expect(refreshCalls).toBe(1);
    expect(tokenStore.get()).toBe("fresh-token");
  });

  it("ends the session when the refresh cookie is rejected", async () => {
    tokenStore.set("expired-token");
    axios.defaults.adapter = async (config) =>
      respond(config, 401, { success: false, error: { status: 401, code: "SESSION_EXPIRED" } });
    http.defaults.adapter = async (config) =>
      respond(config, 401, { success: false, error: { status: 401, code: "UNAUTHENTICATED", detail: "Authentication is required." } });
    const ended = vi.fn();
    const off = onSessionEnded(ended);
    await expect(apiGet("/a")).rejects.toMatchObject({ status: 401, code: "UNAUTHENTICATED" });
    expect(ended).toHaveBeenCalledWith("expired");
    expect(tokenStore.get()).toBeNull();
    off();
  });

  it("never stores tokens in web storage", async () => {
    tokenStore.set("in-memory-only");
    expect(JSON.stringify({ ...localStorage })).not.toContain("in-memory-only");
    expect(JSON.stringify({ ...sessionStorage })).not.toContain("in-memory-only");
  });
});
