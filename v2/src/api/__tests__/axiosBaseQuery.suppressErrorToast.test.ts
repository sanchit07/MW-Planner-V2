import type { BaseQueryApi } from "@reduxjs/toolkit/query";
import { toast } from "react-toastify";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mockRequest = vi.hoisted(() => vi.fn());

vi.mock("react-toastify", () => ({
  toast: { error: vi.fn(), success: vi.fn(), warning: vi.fn(), info: vi.fn() },
}));

vi.mock("@config/index", () => ({
  CONFIG: {
    BACKEND_URL: "https://test-backend",
    ACCOUNT_URL: "https://test-account",
    ACCOUNT_PROXY_URL: "https://test-proxy",
  },
}));

vi.mock("../axiosInstance", () => ({
  createAxiosInstance: () => ({ request: mockRequest }),
}));

import axiosBaseQuery from "../axiosBaseQuery";

const dummyApi: BaseQueryApi = {
  signal: new AbortController().signal,
  abort: vi.fn(),
  dispatch: vi.fn(),
  getState: vi.fn(),
  extra: undefined,
  endpoint: "test",
  type: "query",
};

const serverError = Object.assign(new Error("boom"), {
  isAxiosError: true,
  code: "ERR_BAD_RESPONSE",
  response: { data: { message: "boom" } },
  config: {},
  request: {},
});

describe("axiosBaseQuery – suppressErrorToast", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockRequest.mockRejectedValue(serverError);
  });

  it("does not show an error toast when suppressErrorToast is true", async () => {
    const query = axiosBaseQuery();
    const result = (await query(
      { url: "/api/test", method: "GET", suppressErrorToast: true },
      dummyApi,
      {},
    )) as { error: unknown };
    expect(toast.error).not.toHaveBeenCalled();
    // Error is still returned so RTK Query's isError fires.
    expect(result).toHaveProperty("error");
  });

  it("shows an error toast when the flag is omitted", async () => {
    const query = axiosBaseQuery();
    await query({ url: "/api/test", method: "GET" }, dummyApi, {});
    expect(toast.error).toHaveBeenCalledTimes(1);
  });

  it("suppresses network-error toast too", async () => {
    const networkError = Object.assign(new Error("Network Error"), {
      isAxiosError: true,
      code: "ERR_NETWORK",
      response: undefined,
      config: {},
      request: {},
    });
    mockRequest.mockRejectedValue(networkError);
    const query = axiosBaseQuery();
    await query(
      { url: "/api/test", method: "GET", suppressErrorToast: true },
      dummyApi,
      {},
    );
    expect(toast.error).not.toHaveBeenCalled();
  });
});
