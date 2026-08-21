import type { InternalAxiosRequestConfig } from "axios";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

import { __axiosInstancesForTests } from "../axiosBaseQuery";

/**
 * Tenant-switch regression: the planner-backend instances MUST inject the active
 * company as X-Company-Id. The server resolves both permissions and data scoping
 * (campaign create/list attribution, approval inbox persona) from this single
 * header — without it, a company switch silently falls back to the JWT's primary
 * company and creates/lists plans under the wrong tenant.
 */
function runRequestInterceptor(
  instance: (typeof __axiosInstancesForTests)[string],
): InternalAxiosRequestConfig {
  const handlers = (
    instance.interceptors.request as unknown as {
      handlers: Array<{
        fulfilled: (
          config: InternalAxiosRequestConfig,
        ) => InternalAxiosRequestConfig;
      }>;
    }
  ).handlers;
  const config = { headers: {} } as unknown as InternalAxiosRequestConfig;
  return handlers.reduce((cfg, h) => (h.fulfilled ? h.fulfilled(cfg) : cfg), config);
}

describe("axiosBaseQuery instances — acting-company header wiring", () => {
  beforeEach(() => {
    localStorage.setItem(
      "user_profile",
      JSON.stringify({ activeCompanyId: "switched-co" }),
    );
  });

  afterEach(() => {
    localStorage.clear();
  });

  it.each(["BACKEND_URL", "BACKEND_URL_PROXY"])(
    "%s sends X-Company-Id from the active company",
    (key) => {
      const config = runRequestInterceptor(__axiosInstancesForTests[key]);
      expect(config.headers["X-Company-Id"]).toBe("switched-co");
    },
  );
});
