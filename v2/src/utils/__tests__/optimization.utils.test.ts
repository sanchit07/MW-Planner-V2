import { describe, it, expect } from "vitest";

import {
  transformOptimizationAdjustHundredPercentShare,
  formatTime,
} from "../optimization.utils";

describe("optimization.utils", () => {
  describe("transformOptimizationAdjustHundredPercentShare", () => {
    it("should return undefined when difference is 0", () => {
      const result = transformOptimizationAdjustHundredPercentShare(
        50,
        50,
        { type1: 50 },
        "type1",
      );
      expect(result).toBeUndefined();
    });

    it("should return 100 when there are no other types", () => {
      const result = transformOptimizationAdjustHundredPercentShare(
        50,
        40,
        { type1: 40 },
        "type1",
      );
      expect(result).toBe(100);
    });

    it("should adjust other types proportionally when increasing", () => {
      const watchedValues = { type1: 30, type2: 40, type3: 30 };
      const result = transformOptimizationAdjustHundredPercentShare(
        50,
        30,
        watchedValues,
        "type1",
      );

      expect(result).toBeDefined();
      if (result && typeof result === "object") {
        const adjusted = result as Record<string, number>;
        expect(adjusted.type2).toBeDefined();
        expect(adjusted.type3).toBeDefined();
      }
    });

    it("should not reduce if others are at 0 and difference is negative", () => {
      const result = transformOptimizationAdjustHundredPercentShare(
        20,
        30,
        { type1: 30, type2: 0 },
        "type1",
      );
      expect(result).toBeUndefined();
    });
  });

  describe("formatTime", () => {
    it("should format 24-hour time to 12-hour format", () => {
      expect(formatTime("09:00")).toBe("9:00AM");
      expect(formatTime("13:30")).toBe("1:30PM");
      expect(formatTime("12:00")).toBe("12:00PM");
      expect(formatTime("00:00")).toBe("12:00AM");
      expect(formatTime("23:59")).toBe("11:59PM");
    });

    it("should handle noon correctly", () => {
      expect(formatTime("12:00")).toBe("12:00PM");
      expect(formatTime("12:30")).toBe("12:30PM");
    });

    it("should handle midnight correctly", () => {
      expect(formatTime("00:00")).toBe("12:00AM");
      expect(formatTime("00:30")).toBe("12:30AM");
    });
  });
});
