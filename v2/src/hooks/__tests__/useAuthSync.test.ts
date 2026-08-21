import { renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mockDispatch = vi.fn();

vi.mock("@store", () => ({
  useAppDispatch: () => mockDispatch,
}));

vi.mock("@services/auth/authSlice", () => ({
  logout: () => ({ type: "auth/logout" }),
}));

vi.mock("@config/index", () => ({
  CONFIG: { BACKEND_URL: "https://test-api.example.com" },
}));

const mockStorage = {
  getItem: vi.fn(),
  setItem: vi.fn(),
  removeItem: vi.fn(),
  removeAll: vi.fn(),
};

vi.mock("@utils/storage", () => ({ default: mockStorage }));

const TOKEN = JSON.stringify({
  access_token: "access-token",
  refresh_token: "refresh-token",
  expires_in: Date.now() + 3_600_000,
  token_type: "Bearer",
  scope: "openid",
  state: "",
});

describe("useAuthSync", () => {
  beforeEach(async () => {
    vi.resetModules();
    vi.clearAllMocks();
    mockDispatch.mockReset();
    global.fetch = vi.fn();
    Object.defineProperty(window, "location", {
      writable: true,
      value: { href: "" },
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("cross-tab logout (storage event)", () => {
    it("dispatches logout when auth_token removed in another tab", async () => {
      const { useAuthSync } = await import("../useAuthSync");
      renderHook(() => useAuthSync());

      window.dispatchEvent(
        new StorageEvent("storage", { key: "auth_token", newValue: null }),
      );

      expect(mockDispatch).toHaveBeenCalledWith({ type: "auth/logout" });
    });

    it("ignores storage events for other keys", async () => {
      const { useAuthSync } = await import("../useAuthSync");
      renderHook(() => useAuthSync());

      window.dispatchEvent(
        new StorageEvent("storage", {
          key: "some_other_key",
          newValue: null,
        }),
      );

      expect(mockDispatch).not.toHaveBeenCalled();
    });

    it("ignores storage event when auth_token is set (not removed)", async () => {
      const { useAuthSync } = await import("../useAuthSync");
      renderHook(() => useAuthSync());

      window.dispatchEvent(
        new StorageEvent("storage", {
          key: "auth_token",
          newValue: TOKEN,
        }),
      );

      expect(mockDispatch).not.toHaveBeenCalled();
    });
  });

  describe("tab focus session check (visibilitychange)", () => {
    it("calls refresh endpoint when tab becomes visible", async () => {
      mockStorage.getItem.mockReturnValue(TOKEN);
      vi.mocked(global.fetch).mockResolvedValue({
        ok: true,
        json: async () => ({
          access_token: "new-token",
          refresh_token: "new-refresh",
        }),
      } as Response);

      const { useAuthSync } = await import("../useAuthSync");
      renderHook(() => useAuthSync());

      Object.defineProperty(document, "visibilityState", {
        value: "visible",
        configurable: true,
      });
      document.dispatchEvent(new Event("visibilitychange"));

      await vi.waitFor(() => {
        expect(global.fetch).toHaveBeenCalledWith(
          "https://test-api.example.com/api/v1/auth/refresh",
          expect.objectContaining({ method: "POST" }),
        );
      });
    });

    it("redirects to /login when refresh token rejected", async () => {
      mockStorage.getItem.mockReturnValue(TOKEN);
      vi.mocked(global.fetch).mockResolvedValue({
        ok: false,
        status: 401,
      } as Response);

      const { useAuthSync } = await import("../useAuthSync");
      renderHook(() => useAuthSync());

      Object.defineProperty(document, "visibilityState", {
        value: "visible",
        configurable: true,
      });
      document.dispatchEvent(new Event("visibilitychange"));

      await vi.waitFor(() => {
        expect(mockStorage.removeAll).toHaveBeenCalled();
        expect(window.location.href).toBe("/login");
      });
    });

    it("updates stored token on successful refresh", async () => {
      mockStorage.getItem.mockReturnValue(TOKEN);
      const newToken = {
        access_token: "new-access",
        refresh_token: "new-refresh",
        expires_in: Date.now() + 7_200_000,
        token_type: "Bearer",
        scope: "openid",
        state: "",
      };
      vi.mocked(global.fetch).mockResolvedValue({
        ok: true,
        json: async () => ({ data: newToken }),
      } as Response);

      const { useAuthSync } = await import("../useAuthSync");
      renderHook(() => useAuthSync());

      Object.defineProperty(document, "visibilityState", {
        value: "visible",
        configurable: true,
      });
      document.dispatchEvent(new Event("visibilitychange"));

      await vi.waitFor(() => {
        expect(mockStorage.setItem).toHaveBeenCalledWith(
          "auth_token",
          JSON.stringify(newToken),
        );
      });
    });

    it("does not logout on network error", async () => {
      mockStorage.getItem.mockReturnValue(TOKEN);
      vi.mocked(global.fetch).mockRejectedValue(new Error("Network error"));

      const { useAuthSync } = await import("../useAuthSync");
      renderHook(() => useAuthSync());

      Object.defineProperty(document, "visibilityState", {
        value: "visible",
        configurable: true,
      });
      document.dispatchEvent(new Event("visibilitychange"));

      await vi.waitFor(() => {
        expect(mockStorage.removeAll).not.toHaveBeenCalled();
        expect(window.location.href).toBe("");
      });
    });

    it("skips check when no token in storage", async () => {
      mockStorage.getItem.mockReturnValue(null);

      const { useAuthSync } = await import("../useAuthSync");
      renderHook(() => useAuthSync());

      Object.defineProperty(document, "visibilityState", {
        value: "visible",
        configurable: true,
      });
      document.dispatchEvent(new Event("visibilitychange"));

      await vi.waitFor(() => {
        expect(global.fetch).not.toHaveBeenCalled();
      });
    });

    it("calls refresh on each visibility change", async () => {
      mockStorage.getItem.mockReturnValue(TOKEN);
      vi.mocked(global.fetch).mockResolvedValue({
        ok: true,
        json: async () => ({}),
      } as Response);

      const { useAuthSync } = await import("../useAuthSync");
      renderHook(() => useAuthSync());

      Object.defineProperty(document, "visibilityState", {
        value: "visible",
        configurable: true,
      });

      document.dispatchEvent(new Event("visibilitychange"));
      await vi.waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(1));

      document.dispatchEvent(new Event("visibilitychange"));
      await vi.waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(2));
    });
  });

  describe("cleanup", () => {
    it("removes event listeners on unmount", async () => {
      const addSpy = vi.spyOn(window, "addEventListener");
      const removeSpy = vi.spyOn(window, "removeEventListener");

      const { useAuthSync } = await import("../useAuthSync");
      const { unmount } = renderHook(() => useAuthSync());
      unmount();

      const storageAdded = addSpy.mock.calls.some(([e]) => e === "storage");
      const storageRemoved = removeSpy.mock.calls.some(
        ([e]) => e === "storage",
      );
      expect(storageAdded).toBe(true);
      expect(storageRemoved).toBe(true);
    });
  });
});
