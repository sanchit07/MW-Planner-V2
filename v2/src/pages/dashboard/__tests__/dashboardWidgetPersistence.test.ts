import { describe, it, expect, vi, beforeEach } from "vitest";

import {
  loadWidgetVisibilityFromStorage,
  saveWidgetVisibilityToStorage,
  type WidgetVisibility,
} from "../dashboardWidgetPersistence";

const mockGetItem = vi.fn();
const mockSetItem = vi.fn();

vi.mock("@utils/storage", () => ({
  default: {
    getItem: (key: string) => mockGetItem(key),
    setItem: (key: string, value: string) => mockSetItem(key, value),
  },
}));

describe("dashboardWidgetPersistence", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("loadWidgetVisibilityFromStorage", () => {
    it("returns null when nothing is stored", () => {
      mockGetItem.mockReturnValue(null);
      expect(loadWidgetVisibilityFromStorage()).toBeNull();
      expect(mockGetItem).toHaveBeenCalledWith("dashboard_widget_visibility");
    });

    it("returns null when stored value is empty string", () => {
      mockGetItem.mockReturnValue("");
      expect(loadWidgetVisibilityFromStorage()).toBeNull();
    });

    it("returns parsed object when valid JSON object with boolean values", () => {
      const visibility: WidgetVisibility = {
        "campaign-overview": true,
        "budget-tracker": false,
      };
      mockGetItem.mockReturnValue(JSON.stringify(visibility));
      expect(loadWidgetVisibilityFromStorage()).toEqual(visibility);
    });

    it("returns null when parsed value is not an object", () => {
      mockGetItem.mockReturnValue("true");
      expect(loadWidgetVisibilityFromStorage()).toBeNull();
    });

    it("returns null when parsed value is null", () => {
      mockGetItem.mockReturnValue("null");
      expect(loadWidgetVisibilityFromStorage()).toBeNull();
    });

    it("returns null when parsed value is an array", () => {
      mockGetItem.mockReturnValue("[true,false]");
      expect(loadWidgetVisibilityFromStorage()).toBeNull();
    });

    it("returns null when object has non-boolean value", () => {
      mockGetItem.mockReturnValue(JSON.stringify({ a: "true" }));
      expect(loadWidgetVisibilityFromStorage()).toBeNull();
    });

    it("returns null when object has numeric value", () => {
      mockGetItem.mockReturnValue(JSON.stringify({ a: 1 }));
      expect(loadWidgetVisibilityFromStorage()).toBeNull();
    });

    it("returns null when JSON is invalid", () => {
      mockGetItem.mockReturnValue("{ invalid json }");
      expect(loadWidgetVisibilityFromStorage()).toBeNull();
    });

    it("returns null when object has more than MAX_STORED_KEYS (100)", () => {
      const many: Record<string, boolean> = {};
      for (let i = 0; i < 101; i++) many[`key-${i}`] = true;
      mockGetItem.mockReturnValue(JSON.stringify(many));
      expect(loadWidgetVisibilityFromStorage()).toBeNull();
    });

    it("returns object when exactly 100 keys", () => {
      const many: Record<string, boolean> = {};
      for (let i = 0; i < 100; i++) many[`key-${i}`] = true;
      mockGetItem.mockReturnValue(JSON.stringify(many));
      expect(loadWidgetVisibilityFromStorage()).toEqual(many);
    });

    it("returns null when getItem throws", () => {
      mockGetItem.mockImplementation(() => {
        throw new Error("QuotaExceeded");
      });
      expect(loadWidgetVisibilityFromStorage()).toBeNull();
    });
  });

  describe("saveWidgetVisibilityToStorage", () => {
    it("calls setItem with key and stringified visibility", () => {
      const visibility: WidgetVisibility = { a: true, b: false };
      saveWidgetVisibilityToStorage(visibility);
      expect(mockSetItem).toHaveBeenCalledTimes(1);
      expect(mockSetItem).toHaveBeenCalledWith(
        "dashboard_widget_visibility",
        JSON.stringify(visibility),
      );
    });

    it("only includes entries with string keys and boolean values", () => {
      const visibility: WidgetVisibility = {
        valid: true,
        "also-valid": false,
      };
      saveWidgetVisibilityToStorage(visibility);
      const call = mockSetItem.mock.calls[0];
      const stored = JSON.parse(call[1]) as Record<string, boolean>;
      expect(stored).toEqual({ valid: true, "also-valid": false });
    });

    it("caps at MAX_STORED_KEYS entries", () => {
      const visibility: WidgetVisibility = {};
      for (let i = 0; i < 150; i++) visibility[`key-${i}`] = true;
      saveWidgetVisibilityToStorage(visibility);
      const call = mockSetItem.mock.calls[0];
      const stored = JSON.parse(call[1]) as Record<string, boolean>;
      expect(Object.keys(stored)).toHaveLength(100);
    });

    it("does not throw when setItem throws", () => {
      mockSetItem.mockImplementation(() => {
        throw new Error("QuotaExceeded");
      });
      expect(() => saveWidgetVisibilityToStorage({ a: true })).not.toThrow();
    });
  });
});
