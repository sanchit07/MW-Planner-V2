import type { BaseQueryApi } from "@reduxjs/toolkit/query";
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

describe("axiosBaseQuery – timeout parameter", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockRequest.mockResolvedValue({ data: { success: true, data: {} } });
  });

  it("forwards timeout to the axios request config when provided", async () => {
    const query = axiosBaseQuery();
    await query(
      { url: "/api/test", method: "GET", timeout: 180000 },
      dummyApi,
      {},
    );
    expect(mockRequest).toHaveBeenCalledWith(
      expect.objectContaining({ timeout: 180000 }),
    );
  });

  it("leaves timeout undefined when not specified", async () => {
    const query = axiosBaseQuery();
    await query({ url: "/api/test", method: "GET" }, dummyApi, {});
    const requestConfig = mockRequest.mock.calls[0][0];
    expect(requestConfig.timeout).toBeUndefined();
  });

  it("correctly forwards a custom timeout value (300 s)", async () => {
    const query = axiosBaseQuery();
    await query(
      { url: "/slow-op", method: "POST", data: {}, timeout: 300000 },
      dummyApi,
      {},
    );
    expect(mockRequest).toHaveBeenCalledWith(
      expect.objectContaining({ timeout: 300000 }),
    );
  });

  it("still forwards url, method, data and params alongside timeout", async () => {
    const query = axiosBaseQuery();
    await query(
      {
        url: "/api/bulk",
        method: "POST",
        data: { ids: [1, 2] },
        params: { op: "SELECT" },
        timeout: 180000,
      },
      dummyApi,
      {},
    );
    expect(mockRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        url: "/api/bulk",
        method: "POST",
        data: { ids: [1, 2] },
        params: { op: "SELECT" },
        timeout: 180000,
      }),
    );
  });

  it("returns success data when request resolves", async () => {
    const payload = { success: true, data: { id: 42 } };
    mockRequest.mockResolvedValue({ data: payload });
    const query = axiosBaseQuery();
    const result = await query(
      { url: "/api/test", method: "GET", timeout: 60000 },
      dummyApi,
      {},
    );
    expect(result).toEqual({ data: payload });
  });

  it("returns an error object when ECONNABORTED is thrown (timeout exceeded)", async () => {
    const timeoutError = Object.assign(
      new Error("timeout of 180000ms exceeded"),
      {
        isAxiosError: true,
        code: "ECONNABORTED",
        response: undefined,
        config: {},
        request: {},
      },
    );
    mockRequest.mockRejectedValue(timeoutError);
    const query = axiosBaseQuery();
    const result = (await query(
      {
        url: "/bulk-op",
        method: "POST",
        timeout: 180000,
      },
      dummyApi,
      {},
    )) as { error: unknown };
    expect(result).toHaveProperty("error");
  });
});
