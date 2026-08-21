import { describe, it, expect } from "vitest";

import {
  applySchedulePattern,
  countUniqueScheduledDays,
  countUniqueScheduledDaysFromRange,
  detectSchedulePattern,
  formatScheduleHours,
  extractOperationTimes,
  getHoursPerDayFromSelectedHours,
  getMondayBasedDay,
  hasAnyDayBelowMinHours,
  hasHoursOutsideOperationTime,
  getDayOperationTimes,
  OperationHours,
  formatDateToYYYYMMDD,
} from "../schedule.utils";

describe("schedule.utils", () => {
  describe("getMondayBasedDay", () => {
    it("returns 0 for Monday", () => {
      expect(getMondayBasedDay(new Date("2024-01-01"))).toBe(0);
    });
    it("returns 6 for Sunday", () => {
      expect(getMondayBasedDay(new Date("2024-01-07"))).toBe(6);
    });
    it("returns 1 for Tuesday", () => {
      expect(getMondayBasedDay(new Date("2024-01-02"))).toBe(1);
    });
  });

  describe("applySchedulePattern", () => {
    const from = new Date("2024-01-01"); // Monday
    const to = new Date("2024-01-07"); // Sunday
    const scheduleDate = { from, to };
    const allDays = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

    it("should generate commuter schedule (Mon-Fri, 6-8 & 17-19)", () => {
      const result = applySchedulePattern("commuter", scheduleDate, allDays);
      expect(result.days).toEqual(["Mon", "Tue", "Wed", "Thu", "Fri"]);

      const monDate = "2024-01-01";
      expect(result.hours.has(`${monDate}-6`)).toBe(true);
      expect(result.hours.has(`${monDate}-8`)).toBe(true);
      expect(result.hours.has(`${monDate}-9`)).toBe(false);
      expect(result.hours.has(`${monDate}-17`)).toBe(true);
      expect(result.hours.has(`${monDate}-19`)).toBe(true);
    });

    it("should generate business schedule (Mon-Fri, 9-17)", () => {
      const result = applySchedulePattern("business", scheduleDate, allDays);
      expect(result.days).toEqual(["Mon", "Tue", "Wed", "Thu", "Fri"]);

      const monDate = "2024-01-01";
      expect(result.hours.has(`${monDate}-9`)).toBe(true);
      expect(result.hours.has(`${monDate}-17`)).toBe(true);
      expect(result.hours.has(`${monDate}-8`)).toBe(false);
      expect(result.hours.has(`${monDate}-18`)).toBe(false);
    });

    it("should generate nightlife schedule (All days, 0-1 & 18-23)", () => {
      const result = applySchedulePattern("nightlife", scheduleDate, allDays);
      expect(result.days).toEqual(allDays);

      const monDate = "2024-01-01";
      expect(result.hours.has(`${monDate}-0`)).toBe(true);
      expect(result.hours.has(`${monDate}-1`)).toBe(true);
      expect(result.hours.has(`${monDate}-17`)).toBe(false);
      expect(result.hours.has(`${monDate}-18`)).toBe(true);
      expect(result.hours.has(`${monDate}-23`)).toBe(true);
    });

    it("should generate weekend schedule (Sat-Sun, 10-23)", () => {
      const result = applySchedulePattern("weekend", scheduleDate, allDays);
      expect(result.days).toEqual(["Sat", "Sun"]);

      const satDate = "2024-01-06";
      expect(result.hours.has(`${satDate}-10`)).toBe(true);
      expect(result.hours.has(`${satDate}-23`)).toBe(true);
      expect(result.hours.has(`${satDate}-9`)).toBe(false);
    });

    it("should generate 24/7 schedule (All days, 0-23)", () => {
      const result = applySchedulePattern("24/7", scheduleDate, allDays);
      expect(result.days).toEqual(allDays);
      expect(result.hours.size).toBe(7 * 24); // 7 days * 24 hours
    });

    it("should return empty for custom pattern", () => {
      const result = applySchedulePattern("custom", scheduleDate, allDays);
      expect(result.days).toEqual([]);
      expect(result.hours.size).toBe(0);
    });

    it("should handle partial available days", () => {
      const limitedDays = ["Mon", "Wed", "Fri"];
      const result = applySchedulePattern(
        "business",
        scheduleDate,
        limitedDays,
      );
      expect(result.days).toEqual(limitedDays);

      // Tuesday should have no hours
      const tueDate = "2024-01-02";
      expect(result.hours.has(`${tueDate}-9`)).toBe(false);
    });

    it("should handle empty date range gracefully", () => {
      const result = applySchedulePattern(
        "business",
        { from: null, to: null },
        allDays,
      );
      expect(result.days).toEqual([]);
      expect(result.hours.size).toBe(0);
    });
  });

  describe("detectSchedulePattern", () => {
    const from = new Date("2024-01-01"); // Monday
    const to = new Date("2024-01-07"); // Sunday
    const scheduleDate = { from, to };
    const allDays = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

    it.each(["commuter", "business", "nightlife", "weekend", "24/7"] as const)(
      "detects the %s pattern from its generated days/hours",
      (pattern) => {
        const { days, hours } = applySchedulePattern(
          pattern,
          scheduleDate,
          allDays,
        );
        expect(detectSchedulePattern(scheduleDate, days, hours, allDays)).toBe(
          pattern,
        );
      },
    );

    it("returns custom when hours match no known pattern", () => {
      const hours = new Set(["2024-01-01-3", "2024-01-02-4"]);
      const days = ["Mon", "Tue"];
      expect(detectSchedulePattern(scheduleDate, days, hours, allDays)).toBe(
        "custom",
      );
    });

    it("returns custom when no hours are selected", () => {
      expect(detectSchedulePattern(scheduleDate, [], new Set(), allDays)).toBe(
        "custom",
      );
    });

    it("returns custom when the date range is missing", () => {
      const { days, hours } = applySchedulePattern(
        "business",
        scheduleDate,
        allDays,
      );
      expect(
        detectSchedulePattern({ from: null, to: null }, days, hours, allDays),
      ).toBe("custom");
    });
  });

  describe("formatScheduleHours", () => {
    it("should format single continuous range", () => {
      const matrix = {
        "2024-01-01": [9, 10, 11, 12],
      };
      // 9 is 09:00 AM, 12 is 12:00 PM. Range is 9 to 12.
      // Wait, let's check implementation.
      // If ranges are 9, 10, 11, 12.
      // 9 is start. 12 is end.
      // It formats as "09:00 AM to 12:00 PM".
      const result = formatScheduleHours(matrix);
      expect(result).toEqual(["09:00 AM to 12:00 PM"]);
    });

    it("should format multiple ranges", () => {
      const matrix = {
        "2024-01-01": [9, 10, 14, 15],
      };
      // Expectation based on current implementation (start to end of range)
      // 9, 10 -> 09:00 AM to 10:00 AM
      // 14, 15 -> 02:00 PM to 03:00 PM
      const result = formatScheduleHours(matrix);
      expect(result).toEqual(["09:00 AM to 10:00 AM", "02:00 PM to 03:00 PM"]);
    });

    it("should handle empty matrix", () => {
      expect(formatScheduleHours({})).toEqual([]);
      expect(formatScheduleHours(null)).toEqual([]);
    });
  });

  describe("extractOperationTimes", () => {
    it("should extract min start and max end times", () => {
      const ops: OperationHours = {
        MONDAY: [{ start: "09:00:00", end: "17:00:00" }],
        TUESDAY: [{ start: "08:00:00", end: "18:00:00" }],
        WEDNESDAY: [],
        THURSDAY: [],
        FRIDAY: [],
        SATURDAY: [],
        SUNDAY: [],
      };
      const result = extractOperationTimes(ops);
      expect(result).toEqual({ startTime: "08:00:00", endTime: "18:00:00" });
    });

    it("should return null for empty/invalid input", () => {
      expect(extractOperationTimes(null)).toBeNull();
      expect(extractOperationTimes({} as unknown as OperationHours)).toBeNull();
    });
  });

  describe("hasHoursOutsideOperationTime", () => {
    const operations = {
      operatingTimes: {
        MONDAY: [{ start: "09:00:00", end: "17:00:00" }],
        TUESDAY: [{ start: "09:00:00", end: "17:00:00" }],
        // Wed is not in operating times
      } as OperationHours,
    };

    it("should return false if all selected hours are within operation times", () => {
      const selected = new Set(["2024-01-01-10", "2024-01-01-11"]); // Mon
      expect(hasHoursOutsideOperationTime(operations, selected)).toBe(false);
    });

    it("should return true if hour is outside operation times", () => {
      const selected = new Set(["2024-01-01-8"]); // Mon 8am (starts 9am)
      expect(hasHoursOutsideOperationTime(operations, selected)).toBe(true);
    });

    it("should return true if day is not in operation days", () => {
      const selected = new Set(["2024-01-03-10"]); // Wed
      expect(hasHoursOutsideOperationTime(operations, selected)).toBe(true);
    });
  });

  describe("getDayOperationTimes", () => {
    it("should return times from operatingTimes if available", () => {
      const ops = {
        operatingTimes: {
          MONDAY: [{ start: "09:00:00", end: "17:00:00" }],
        } as OperationHours,
      };
      const result = getDayOperationTimes(ops, "MONDAY");
      expect(result).toEqual({ startTime: "09:00:00", endTime: "17:00:00" });
    });

    it("should fallback to root startTime/endTime", () => {
      const ops = {
        startTime: "08:00:00",
        endTime: "18:00:00",
      };
      const result = getDayOperationTimes(ops, "MONDAY");
      expect(result).toEqual({ startTime: "08:00:00", endTime: "18:00:00" });
    });

    it("should return null if no times available", () => {
      const result = getDayOperationTimes({}, "MONDAY");
      expect(result).toBeNull();
    });
  });

  describe("formatDateToYYYYMMDD", () => {
    it("should format date correctly", () => {
      const date = new Date(2024, 0, 5); // Jan 5 2024
      expect(formatDateToYYYYMMDD(date)).toBe("2024-01-05");
    });
  });

  describe("getHoursPerDayFromSelectedHours", () => {
    it("groups selected hours by date", () => {
      const selectedHours = new Set([
        "2024-01-01-9",
        "2024-01-01-10",
        "2024-01-02-9",
      ]);
      expect(getHoursPerDayFromSelectedHours(selectedHours)).toEqual({
        "2024-01-01": 2,
        "2024-01-02": 1,
      });
    });

    it("returns an empty object for an empty set", () => {
      expect(getHoursPerDayFromSelectedHours(new Set())).toEqual({});
    });
  });

  describe("hasAnyDayBelowMinHours", () => {
    it("returns false when minHours is not set", () => {
      const selectedHours = new Set(["2024-01-01-9"]);
      expect(hasAnyDayBelowMinHours(selectedHours, undefined)).toBe(false);
      expect(hasAnyDayBelowMinHours(selectedHours, 0)).toBe(false);
    });

    it("returns false when no hours are selected", () => {
      expect(hasAnyDayBelowMinHours(new Set(), 3)).toBe(false);
    });

    it("returns false when every scheduled day meets minHours", () => {
      const selectedHours = new Set([
        "2024-01-01-9",
        "2024-01-01-10",
        "2024-01-01-11",
        "2024-01-02-9",
        "2024-01-02-10",
        "2024-01-02-11",
      ]);
      expect(hasAnyDayBelowMinHours(selectedHours, 3)).toBe(false);
    });

    it("returns true when at least one scheduled day is below minHours", () => {
      const selectedHours = new Set([
        "2024-01-01-9",
        "2024-01-01-10",
        "2024-01-01-11",
        "2024-01-02-9", // only 1 hour on this day
      ]);
      expect(hasAnyDayBelowMinHours(selectedHours, 3)).toBe(true);
    });
  });

  describe("countUniqueScheduledDays", () => {
    it("counts the union of dates across multiple schedules", () => {
      const schedules: { bookingMatrix: Record<string, number[]> }[] = [
        { bookingMatrix: { "2024-01-01": [9, 10], "2024-01-02": [9] } },
        { bookingMatrix: { "2024-01-02": [10], "2024-01-03": [9] } },
      ];
      expect(countUniqueScheduledDays(schedules)).toBe(3);
    });

    it("ignores dates with an empty hours array", () => {
      const schedules = [{ bookingMatrix: { "2024-01-01": [] } }];
      expect(countUniqueScheduledDays(schedules)).toBe(0);
    });

    it("returns 0 for an empty or null schedule list", () => {
      expect(countUniqueScheduledDays([])).toBe(0);
      expect(countUniqueScheduledDays([null, undefined])).toBe(0);
    });
  });

  describe("countUniqueScheduledDaysFromRange", () => {
    it("counts only days matching scheduleDays within the date range", () => {
      // 2024-01-01 is a Monday; range covers one full week.
      const schedules = [
        {
          scheduleDays: ["MONDAY", "WEDNESDAY"],
          startDate: "2024-01-01",
          endDate: "2024-01-07",
        },
      ];
      // Mon 1/1 and Wed 1/3 — 2 unique days.
      expect(countUniqueScheduledDaysFromRange(schedules)).toBe(2);
    });

    it("treats missing/empty scheduleDays as no restriction (every day in range counts)", () => {
      const schedules = [
        { scheduleDays: [], startDate: "2024-01-01", endDate: "2024-01-05" },
      ];
      expect(countUniqueScheduledDaysFromRange(schedules)).toBe(5);
    });

    it("unions days across multiple schedules for the same inventory", () => {
      const schedules = [
        {
          scheduleDays: ["MONDAY"],
          startDate: "2024-01-01",
          endDate: "2024-01-07",
        },
        {
          scheduleDays: ["TUESDAY"],
          startDate: "2024-01-08",
          endDate: "2024-01-14",
        },
      ];
      expect(countUniqueScheduledDaysFromRange(schedules)).toBe(2);
    });

    it("returns 0 for schedules with no startDate/endDate", () => {
      const schedules = [{ scheduleDays: ["MONDAY"] }];
      expect(countUniqueScheduledDaysFromRange(schedules)).toBe(0);
      expect(countUniqueScheduledDaysFromRange([])).toBe(0);
      expect(countUniqueScheduledDaysFromRange([null, undefined])).toBe(0);
    });
  });
});
