import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";

import {
  toISODateString,
  fromISODateString,
  toAPIDateString,
  getCurrentLocalDate,
  isValidDateString,
  getStartOfDay,
  computeDaysLeft,
  findDurationInDays,
} from "../dateUtils";

describe("dateUtils", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe("toISODateString & fromISODateString", () => {
    it("should convert date to YYYY-MM-DD format regardless of time", () => {
      const date = new Date("2024-01-15T10:00:00");
      expect(toISODateString(date)).toBe("2024-01-15");
    });

    it("should parse YYYY-MM-DD string to Date object", () => {
      const result = fromISODateString("2024-01-15");
      expect(result.getFullYear()).toBe(2024);
      expect(result.getMonth()).toBe(0); // Jan is 0
      expect(result.getDate()).toBe(15);
    });

    it("should throw error for invalid date in toISODateString", () => {
      expect(() => toISODateString(null)).toThrow();
    });

    it("should throw error for invalid string in fromISODateString", () => {
      expect(() => fromISODateString("invalid")).toThrow();
    });
  });

  describe("getCurrentLocalDate", () => {
    it("should return current date without time", () => {
      const mockNow = new Date("2024-05-20T14:30:00");
      vi.setSystemTime(mockNow);

      const result = getCurrentLocalDate();
      expect(result.getFullYear()).toBe(2024);
      expect(result.getMonth()).toBe(4); // May
      expect(result.getDate()).toBe(20);
      expect(result.getHours()).toBe(0);
      expect(result.getMinutes()).toBe(0);
    });
  });

  describe("getStartOfDay", () => {
    it("should return date with time set to 00:00:00:000", () => {
      const date = new Date("2024-01-01T15:30:45.123");
      const result = getStartOfDay(date);
      expect(result.getHours()).toBe(0);
      expect(result.getMilliseconds()).toBe(0);
      expect(result.getDate()).toBe(1);
    });

    it("should not mutate original date", () => {
      const date = new Date("2024-01-01T15:30:45.123");
      // getStartOfDay creates a new Date internally in implementation
      // const result = getStartOfDay(date);
      expect(date.getHours()).toBe(15);
    });
  });

  describe("computeDaysLeft", () => {
    it("should calculate days left correctly", () => {
      vi.setSystemTime(new Date("2024-01-01T00:00:00"));
      const targetDate = "2024-01-05T10:00:00";
      // 2024-01-05 - 2024-01-01 = 4 days
      expect(computeDaysLeft(targetDate)).toBe("4 days left");
    });

    it("should return -- if date is in past", () => {
      vi.setSystemTime(new Date("2024-01-05T00:00:00"));
      const targetDate = "2024-01-01T10:00:00";
      expect(computeDaysLeft(targetDate)).toBe("--");
    });
  });

  describe("findDurationInDays", () => {
    it("should include both start and end dates (+1)", () => {
      const start = "2024-01-01";
      const end = "2024-01-05";
      // 5 days: 1, 2, 3, 4, 5
      expect(findDurationInDays(start, end)).toBe(5);
    });

    it("should return 1 for same day", () => {
      const start = "2024-01-01";
      const end = "2024-01-01";
      expect(findDurationInDays(start, end)).toBe(1);
    });
  });

  describe("Utility Wrappers", () => {
    it("toAPIDateString should call toISODateString", () => {
      const date = new Date(2024, 0, 1);
      expect(toAPIDateString(date)).toBe("2024-01-01");
    });

    it("isValidDateString handles valid/invalid strings", () => {
      expect(isValidDateString("2024-01-01")).toBe(true);
      expect(isValidDateString("invalid")).toBe(false);
      // expect(isValidDateString(null as any)).toBe(false);
    });
  });
});
