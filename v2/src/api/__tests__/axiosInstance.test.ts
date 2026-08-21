import type { InternalAxiosRequestConfig } from "axios";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

import { createAxiosInstance } from "../axiosInstance";

function getRequestInterceptor(
  instance: ReturnType<typeof createAxiosInstance>,
) {
  // Axios stores interceptor handlers in an internal array; grabbing the
  // fulfilled callback directly lets us exercise it without a live request.
  const handlers = (
    instance.interceptors.request as unknown as {
      handlers: Array<{
        fulfilled: (
          config: InternalAxiosRequestConfig,
        ) => InternalAxiosRequestConfig;
      }>;
    }
  ).handlers;
  return handlers[0].fulfilled;
}

function fakeConfig(
  headers: Record<string, string> = {},
): InternalAxiosRequestConfig {
  return { headers } as unknown as InternalAxiosRequestConfig;
}

describe("createAxiosInstance — X-Company-Id injection", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it("does not add X-Company-Id when injectActiveCompanyId is not set", () => {
    localStorage.setItem(
      "user_profile",
      JSON.stringify({ activeCompanyId: "company-1" }),
    );
    const instance = createAxiosInstance("https://example.com");
    const config = getRequestInterceptor(instance)(fakeConfig());
    expect(config.headers["X-Company-Id"]).toBeUndefined();
  });

  it("adds X-Company-Id from the stored profile's activeCompanyId when enabled", () => {
    localStorage.setItem(
      "user_profile",
      JSON.stringify({ activeCompanyId: "company-1" }),
    );
    const instance = createAxiosInstance("https://example.com", {
      injectActiveCompanyId: true,
    });
    const config = getRequestInterceptor(instance)(fakeConfig());
    expect(config.headers["X-Company-Id"]).toBe("company-1");
  });

  it("falls back to current_company.id when activeCompanyId is absent", () => {
    localStorage.setItem(
      "user_profile",
      JSON.stringify({ current_company: { id: "company-2" } }),
    );
    const instance = createAxiosInstance("https://example.com", {
      injectActiveCompanyId: true,
    });
    const config = getRequestInterceptor(instance)(fakeConfig());
    expect(config.headers["X-Company-Id"]).toBe("company-2");
  });

  it("does not set the header when no profile is stored", () => {
    const instance = createAxiosInstance("https://example.com", {
      injectActiveCompanyId: true,
    });
    const config = getRequestInterceptor(instance)(fakeConfig());
    expect(config.headers["X-Company-Id"]).toBeUndefined();
  });

  it("does not overwrite an already-set X-Company-Id header", () => {
    localStorage.setItem(
      "user_profile",
      JSON.stringify({ activeCompanyId: "company-1" }),
    );
    const instance = createAxiosInstance("https://example.com", {
      injectActiveCompanyId: true,
    });
    const config = getRequestInterceptor(instance)(
      fakeConfig({ "X-Company-Id": "explicit-company" }),
    );
    expect(config.headers["X-Company-Id"]).toBe("explicit-company");
  });

  it("ignores malformed profile JSON without throwing", () => {
    localStorage.setItem("user_profile", "{not-json");
    const instance = createAxiosInstance("https://example.com", {
      injectActiveCompanyId: true,
    });
    expect(() => getRequestInterceptor(instance)(fakeConfig())).not.toThrow();
  });
});

describe("createAxiosInstance — cache headers", () => {
  it("disables browser HTTP caching by default so GET requests always hit the network", () => {
    // Without this, the browser's own HTTP cache (separate from RTK
    // Query's cache) can transparently serve a stale response for an
    // identical GET URL - e.g. navigating away and back to the same list -
    // even when the app explicitly requests a fresh refetch.
    const instance = createAxiosInstance("https://example.com");
    expect(instance.defaults.headers["Cache-Control"]).toBe("no-cache");
    expect(instance.defaults.headers.Pragma).toBe("no-cache");
  });
});

describe("createAxiosInstance — GET params", () => {
  it("preserves existing params on GET requests", () => {
    const instance = createAxiosInstance("https://example.com");
    const config = getRequestInterceptor(instance)({
      method: "get",
      headers: {},
      params: { page: 0, size: 10 },
    } as unknown as InternalAxiosRequestConfig);

    expect(config.params).toMatchObject({ page: 0, size: 10 });
  });
});
