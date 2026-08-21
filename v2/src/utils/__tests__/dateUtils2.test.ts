import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";

import {
  fromAPIDateString,
  createLocalDate,
  addDays,
  subtractDays,
  isSameDay,
  isDateBetween,
  formatDate,
  toAPIDateRange,
  fromAPIDateRange,
  getEndOfDay,
  toTimestamp,
  fromTimestamp,
  formatDisplayDate,
  formatDisplayDateRange,
  getStartOfWeek,
  getDaysInMonth,
  formatMonthYear,
  getWeekOfYear,
  formatWeekRange,
  getFirstDayOfMonth,
} from "../dateUtils";

describe("dateUtils (extended coverage)", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  // ---------------------------------------------------------------------------
  // fromAPIDateString
  // ---------------------------------------------------------------------------
  describe("fromAPIDateString", () => {
    it("should parse a valid API date string to a local Date", () => {
      const result = fromAPIDateString("2024-03-15");
      expect(result.getFullYear()).toBe(2024);
      expect(result.getMonth()).toBe(2); // March = 2
      expect(result.getDate()).toBe(15);
    });

    it("should throw for an invalid date string", () => {
      expect(() => fromAPIDateString("not-a-date")).toThrow();
    });

    it("should handle year boundaries correctly", () => {
      const result = fromAPIDateString("2000-01-01");
      expect(result.getFullYear()).toBe(2000);
      expect(result.getMonth()).toBe(0);
      expect(result.getDate()).toBe(1);
    });
  });

  // ---------------------------------------------------------------------------
  // createLocalDate
  // ---------------------------------------------------------------------------
  describe("createLocalDate", () => {
    it("should create a date from year, month (1-based), and day", () => {
      const result = createLocalDate(2024, 6, 15);
      expect(result.getFullYear()).toBe(2024);
      expect(result.getMonth()).toBe(5); // June = 5 internally
      expect(result.getDate()).toBe(15);
    });

    it("should create January correctly (month 1)", () => {
      const result = createLocalDate(2025, 1, 1);
      expect(result.getMonth()).toBe(0);
      expect(result.getDate()).toBe(1);
    });

    it("should create December correctly (month 12)", () => {
      const result = createLocalDate(2024, 12, 31);
      expect(result.getMonth()).toBe(11);
      expect(result.getDate()).toBe(31);
    });

    it("should set time to midnight by default", () => {
      const result = createLocalDate(2024, 5, 10);
      expect(result.getHours()).toBe(0);
      expect(result.getMinutes()).toBe(0);
      expect(result.getSeconds()).toBe(0);
    });
  });

  // ---------------------------------------------------------------------------
  // addDays
  // ---------------------------------------------------------------------------
  describe("addDays", () => {
    it("should add positive days", () => {
      const base = new Date(2024, 0, 1); // Jan 1
      const result = addDays(base, 5);
      expect(result.getFullYear()).toBe(2024);
      expect(result.getMonth()).toBe(0);
      expect(result.getDate()).toBe(6);
    });

    it("should cross month boundary", () => {
      const base = new Date(2024, 0, 28); // Jan 28
      const result = addDays(base, 5);
      expect(result.getMonth()).toBe(1); // February
      expect(result.getDate()).toBe(2);
    });

    it("should cross year boundary", () => {
      const base = new Date(2024, 11, 30); // Dec 30
      const result = addDays(base, 3);
      expect(result.getFullYear()).toBe(2025);
      expect(result.getMonth()).toBe(0);
      expect(result.getDate()).toBe(2);
    });

    it("should not mutate the original date", () => {
      const base = new Date(2024, 0, 1);
      addDays(base, 10);
      expect(base.getDate()).toBe(1);
    });

    it("should handle zero days", () => {
      const base = new Date(2024, 5, 15);
      const result = addDays(base, 0);
      expect(result.getDate()).toBe(15);
    });

    it("should handle negative days (same as subtractDays)", () => {
      const base = new Date(2024, 0, 10);
      const result = addDays(base, -3);
      expect(result.getDate()).toBe(7);
    });
  });

  // ---------------------------------------------------------------------------
  // subtractDays
  // ---------------------------------------------------------------------------
  describe("subtractDays", () => {
    it("should subtract positive days", () => {
      const base = new Date(2024, 0, 10); // Jan 10
      const result = subtractDays(base, 5);
      expect(result.getDate()).toBe(5);
    });

    it("should cross month boundary going backward", () => {
      const base = new Date(2024, 1, 3); // Feb 3
      const result = subtractDays(base, 5);
      expect(result.getMonth()).toBe(0); // January
      expect(result.getDate()).toBe(29); // 2024 is a leap year
    });

    it("should not mutate the original date", () => {
      const base = new Date(2024, 0, 15);
      subtractDays(base, 10);
      expect(base.getDate()).toBe(15);
    });

    it("should handle zero days", () => {
      const base = new Date(2024, 5, 15);
      const result = subtractDays(base, 0);
      expect(result.getDate()).toBe(15);
    });
  });

  // ---------------------------------------------------------------------------
  // isSameDay
  // ---------------------------------------------------------------------------
  describe("isSameDay", () => {
    it("should return true for the same calendar day regardless of time", () => {
      const d1 = new Date(2024, 2, 15, 0, 0, 0);
      const d2 = new Date(2024, 2, 15, 23, 59, 59);
      expect(isSameDay(d1, d2)).toBe(true);
    });

    it("should return false for different days", () => {
      const d1 = new Date(2024, 2, 15);
      const d2 = new Date(2024, 2, 16);
      expect(isSameDay(d1, d2)).toBe(false);
    });

    it("should return false for same day but different months", () => {
      const d1 = new Date(2024, 2, 15);
      const d2 = new Date(2024, 3, 15);
      expect(isSameDay(d1, d2)).toBe(false);
    });

    it("should return false for same day/month but different years", () => {
      const d1 = new Date(2023, 2, 15);
      const d2 = new Date(2024, 2, 15);
      expect(isSameDay(d1, d2)).toBe(false);
    });

    it("should return false when date1 is null", () => {
      expect(isSameDay(null, new Date())).toBe(false);
    });

    it("should return false when date2 is null", () => {
      expect(isSameDay(new Date(), null)).toBe(false);
    });

    it("should return false when both are null", () => {
      expect(isSameDay(null, null)).toBe(false);
    });
  });

  // ---------------------------------------------------------------------------
  // isDateBetween
  // ---------------------------------------------------------------------------
  describe("isDateBetween", () => {
    const start = new Date(2024, 0, 1);
    const end = new Date(2024, 0, 31);

    it("should return true for a date within the range", () => {
      const mid = new Date(2024, 0, 15);
      expect(isDateBetween(mid, start, end)).toBe(true);
    });

    it("should return true for a date equal to startDate (inclusive)", () => {
      expect(isDateBetween(new Date(2024, 0, 1), start, end)).toBe(true);
    });

    it("should return true for a date equal to endDate (inclusive)", () => {
      expect(isDateBetween(new Date(2024, 0, 31), start, end)).toBe(true);
    });

    it("should return false for a date before the range", () => {
      expect(isDateBetween(new Date(2023, 11, 31), start, end)).toBe(false);
    });

    it("should return false for a date after the range", () => {
      expect(isDateBetween(new Date(2024, 1, 1), start, end)).toBe(false);
    });

    it("should return false when startDate is null", () => {
      expect(isDateBetween(new Date(2024, 0, 15), null, end)).toBe(false);
    });

    it("should return false when endDate is null", () => {
      expect(isDateBetween(new Date(2024, 0, 15), start, null)).toBe(false);
    });
  });

  // ---------------------------------------------------------------------------
  // formatDate
  // ---------------------------------------------------------------------------
  describe("formatDate", () => {
    it("should format with default MM/dd/yyyy pattern", () => {
      const date = new Date(2024, 0, 5); // Jan 5
      expect(formatDate(date)).toBe("01/05/2024");
    });

    it("should format with a custom yyyy-MM-dd pattern", () => {
      const date = new Date(2024, 5, 20); // June 20
      expect(formatDate(date, "yyyy-MM-dd")).toBe("2024-06-20");
    });

    it("should format with MMM pattern for abbreviated month name", () => {
      const date = new Date(2024, 0, 15); // Jan 15
      const result = formatDate(date, "MMM dd, yyyy");
      expect(result).toMatch(/Jan 15, 2024/);
    });

    it("should return empty string for null", () => {
      expect(formatDate(null)).toBe("");
    });

    it("should return empty string for a non-Date value", () => {
      expect(formatDate("2024-01-01" as unknown as Date)).toBe("");
    });

    it("should pad single-digit months and days", () => {
      const date = new Date(2024, 0, 9); // Jan 9
      expect(formatDate(date)).toBe("01/09/2024");
    });

    it("should handle December (month 12)", () => {
      const date = new Date(2024, 11, 31); // Dec 31
      expect(formatDate(date)).toBe("12/31/2024");
    });
  });

  // ---------------------------------------------------------------------------
  // toAPIDateRange
  // ---------------------------------------------------------------------------
  describe("toAPIDateRange", () => {
    it("should convert a date range to API startDate/endDate strings", () => {
      const range = {
        from: new Date(2024, 0, 1),
        to: new Date(2024, 2, 31),
      };
      const result = toAPIDateRange(range);
      expect(result.startDate).toBe("2024-01-01");
      expect(result.endDate).toBe("2024-03-31");
    });

    it("should handle same-day range", () => {
      const date = new Date(2024, 5, 15);
      const result = toAPIDateRange({ from: date, to: date });
      expect(result.startDate).toBe("2024-06-15");
      expect(result.endDate).toBe("2024-06-15");
    });
  });

  // ---------------------------------------------------------------------------
  // fromAPIDateRange
  // ---------------------------------------------------------------------------
  describe("fromAPIDateRange", () => {
    it("should convert startDate/endDate strings to local Date objects", () => {
      const result = fromAPIDateRange("2024-01-01", "2024-03-31");
      expect(result.from.getFullYear()).toBe(2024);
      expect(result.from.getMonth()).toBe(0);
      expect(result.from.getDate()).toBe(1);
      expect(result.to.getFullYear()).toBe(2024);
      expect(result.to.getMonth()).toBe(2);
      expect(result.to.getDate()).toBe(31);
    });

    it("should handle same-day range", () => {
      const result = fromAPIDateRange("2024-06-15", "2024-06-15");
      expect(isSameDay(result.from, result.to)).toBe(true);
    });

    it("should throw for invalid date strings", () => {
      expect(() => fromAPIDateRange("bad", "2024-01-01")).toThrow();
    });
  });

  // ---------------------------------------------------------------------------
  // getEndOfDay
  // ---------------------------------------------------------------------------
  describe("getEndOfDay", () => {
    it("should set time to 23:59:59.999", () => {
      const date = new Date(2024, 0, 15, 8, 30);
      const result = getEndOfDay(date);
      expect(result.getHours()).toBe(23);
      expect(result.getMinutes()).toBe(59);
      expect(result.getSeconds()).toBe(59);
      expect(result.getMilliseconds()).toBe(999);
    });

    it("should preserve the same calendar date", () => {
      const date = new Date(2024, 5, 20, 10, 0, 0);
      const result = getEndOfDay(date);
      expect(result.getFullYear()).toBe(2024);
      expect(result.getMonth()).toBe(5);
      expect(result.getDate()).toBe(20);
    });

    it("should not mutate the original date", () => {
      const date = new Date(2024, 0, 15, 8, 30);
      getEndOfDay(date);
      expect(date.getHours()).toBe(8);
    });

    it("should throw for null", () => {
      expect(() => getEndOfDay(null)).toThrow();
    });
  });

  // ---------------------------------------------------------------------------
  // toTimestamp / fromTimestamp
  // ---------------------------------------------------------------------------
  describe("toTimestamp", () => {
    it("should return the numeric ms timestamp of a date", () => {
      const date = new Date(2024, 0, 1, 0, 0, 0, 0);
      expect(toTimestamp(date)).toBe(date.getTime());
    });

    it("should return different values for different dates", () => {
      const d1 = new Date(2024, 0, 1);
      const d2 = new Date(2024, 0, 2);
      expect(toTimestamp(d1)).not.toBe(toTimestamp(d2));
    });
  });

  describe("fromTimestamp", () => {
    it("should reconstruct the original date from its timestamp", () => {
      const original = new Date(2024, 5, 15, 12, 30, 45);
      const ts = toTimestamp(original);
      const result = fromTimestamp(ts);
      expect(result.getTime()).toBe(original.getTime());
    });

    it("should handle epoch 0", () => {
      const result = fromTimestamp(0);
      expect(result.getTime()).toBe(0);
    });
  });

  describe("toTimestamp / fromTimestamp roundtrip", () => {
    it("should roundtrip correctly", () => {
      const date = new Date(2025, 3, 22, 9, 0, 0);
      expect(fromTimestamp(toTimestamp(date)).getTime()).toBe(date.getTime());
    });
  });

  // ---------------------------------------------------------------------------
  // formatDisplayDate
  // ---------------------------------------------------------------------------
  describe("formatDisplayDate", () => {
    it("should return -- for an empty string", () => {
      expect(formatDisplayDate("")).toBe("--");
    });

    it("should return -- for an invalid date string", () => {
      expect(formatDisplayDate("not-a-date")).toBe("--");
    });

    it("should format with en-US locale when no t function is provided", () => {
      // "2024-01-15" parsed as local ISO gives Jan 15 2024
      const result = formatDisplayDate("2024-01-15");
      expect(result).toMatch(/Jan/);
      expect(result).toMatch(/15/);
      expect(result).toMatch(/2024/);
    });

    it("should use the t function when provided", () => {
      const mockT = vi.fn(
        (key: string, params?: Record<string, string | number>) => {
          if (key.startsWith("calendar.monthNamesShort.")) return "Feb";
          if (key === "calendar.formattedShortDate")
            return `${params?.month} ${params?.day}, ${params?.year}`;
          return key;
        },
      );

      const result = formatDisplayDate("2024-02-10", mockT);
      expect(mockT).toHaveBeenCalledWith(
        expect.stringContaining("calendar.monthNamesShort."),
      );
      expect(mockT).toHaveBeenCalledWith(
        "calendar.formattedShortDate",
        expect.objectContaining({ month: "Feb", year: 2024 }),
      );
      expect(result).toContain("Feb");
      expect(result).toContain("2024");
    });
  });

  // ---------------------------------------------------------------------------
  // formatDisplayDateRange
  // ---------------------------------------------------------------------------
  describe("formatDisplayDateRange", () => {
    const enT = (key: string, params?: Record<string, string | number>) => {
      const months = [
        "Jan",
        "Feb",
        "Mar",
        "Apr",
        "May",
        "Jun",
        "Jul",
        "Aug",
        "Sep",
        "Oct",
        "Nov",
        "Dec",
      ];
      if (key.startsWith("calendar.monthNamesShort.")) {
        return months[Number(key.split(".").pop())];
      }
      if (key === "calendar.formattedShortDate") {
        return `${params?.month} ${params?.day}, ${params?.year}`;
      }
      return key;
    };

    const jaT = (key: string, params?: Record<string, string | number>) => {
      if (key.startsWith("calendar.monthNamesShort.")) {
        return `${Number(key.split(".").pop()) + 1}月`;
      }
      if (key === "calendar.formattedShortDate") {
        return `${params?.year}年${params?.month}${params?.day}日`;
      }
      return key;
    };

    it("drops the redundant year from the start date within the same year", () => {
      expect(formatDisplayDateRange("2022-07-25", "2022-07-31", enT)).toBe(
        "Jul 25 - Jul 31, 2022",
      );
    });

    it("keeps both years when the range spans two years", () => {
      expect(formatDisplayDateRange("2022-12-28", "2023-01-05", enT)).toBe(
        "Dec 28, 2022 - Jan 05, 2023",
      );
    });

    it("keeps both dates in full for locales that lead with the year", () => {
      expect(formatDisplayDateRange("2022-07-25", "2022-07-31", jaT)).toBe(
        "2022年7月25日 - 2022年7月31日",
      );
    });

    it("returns -- when either date is missing or invalid", () => {
      expect(formatDisplayDateRange("", "2022-07-31", enT)).toBe("--");
      expect(formatDisplayDateRange("2022-07-25", "not-a-date", enT)).toBe(
        "--",
      );
    });

    it("formats without a t function", () => {
      const result = formatDisplayDateRange("2022-07-25", "2022-07-31");
      expect(result).toBe("Jul 25 - Jul 31, 2022");
    });
  });

  // ---------------------------------------------------------------------------
  // getStartOfWeek
  // ---------------------------------------------------------------------------
  describe("getStartOfWeek", () => {
    // Jan 15, 2025 is a Wednesday (day = 3)
    const wednesday = new Date(2025, 0, 15);

    it("should return Sunday when weekStartsOn=0 and input is Wednesday", () => {
      const result = getStartOfWeek(wednesday, 0);
      expect(result.getDay()).toBe(0); // Sunday
      expect(result.getDate()).toBe(12); // Jan 12
    });

    it("should return Monday when weekStartsOn=1 and input is Wednesday", () => {
      const result = getStartOfWeek(wednesday, 1);
      expect(result.getDay()).toBe(1); // Monday
      expect(result.getDate()).toBe(13); // Jan 13
    });

    it("should return the same date when input is already the first day of the week", () => {
      const monday = new Date(2025, 0, 13); // Monday
      const result = getStartOfWeek(monday, 1);
      expect(result.getDate()).toBe(13);
    });

    it("should not mutate the original date", () => {
      const date = new Date(2025, 0, 15);
      getStartOfWeek(date, 1);
      expect(date.getDate()).toBe(15);
    });
  });

  // ---------------------------------------------------------------------------
  // getDaysInMonth
  // ---------------------------------------------------------------------------
  describe("getDaysInMonth", () => {
    it("should return 31 for January (month 0)", () => {
      expect(getDaysInMonth(2024, 0)).toBe(31);
    });

    it("should return 29 for February in a leap year (month 1)", () => {
      expect(getDaysInMonth(2024, 1)).toBe(29);
    });

    it("should return 28 for February in a non-leap year", () => {
      expect(getDaysInMonth(2023, 1)).toBe(28);
    });

    it("should return 30 for April (month 3)", () => {
      expect(getDaysInMonth(2024, 3)).toBe(30);
    });

    it("should return 31 for December (month 11)", () => {
      expect(getDaysInMonth(2024, 11)).toBe(31);
    });
  });

  // ---------------------------------------------------------------------------
  // formatMonthYear
  // ---------------------------------------------------------------------------
  describe("formatMonthYear", () => {
    it("should return the full month name and year", () => {
      const date = new Date(2025, 0, 15); // January 2025
      expect(formatMonthYear(date)).toBe("January 2025");
    });

    it("should handle December", () => {
      const date = new Date(2024, 11, 1); // December 2024
      expect(formatMonthYear(date)).toBe("December 2024");
    });

    it("should handle a different year", () => {
      const date = new Date(2000, 5, 1); // June 2000
      expect(formatMonthYear(date)).toBe("June 2000");
    });
  });

  // ---------------------------------------------------------------------------
  // getWeekOfYear
  // ---------------------------------------------------------------------------
  describe("getWeekOfYear", () => {
    it("should return 1 for the first week of the year (weekStartsOn=1)", () => {
      // Jan 1, 2018 is a Monday — so its week start is Jan 1 itself,
      // and the first week of 2018 also starts on Jan 1 → diff = 0 → week 1.
      const jan1 = new Date(2018, 0, 1);
      const week = getWeekOfYear(jan1, 1);
      expect(week).toBe(1);
    });

    it("should return a higher week number later in the year", () => {
      const jan13 = new Date(2025, 0, 13); // 2nd week (Mon Jan 13)
      const jan6 = new Date(2025, 0, 6); // Monday Jan 6
      expect(getWeekOfYear(jan13, 1)).toBeGreaterThan(getWeekOfYear(jan6, 1));
    });

    it("should give consistent results for the same week start (weekStartsOn=0)", () => {
      // For weekStartsOn=0, Jan 5, 2025 (Sunday) is a week boundary
      const sunday = new Date(2025, 0, 5);
      const monday = new Date(2025, 0, 6); // in same week as the Sunday
      expect(getWeekOfYear(sunday, 0)).toBe(getWeekOfYear(monday, 0));
    });
  });

  // ---------------------------------------------------------------------------
  // formatWeekRange
  // ---------------------------------------------------------------------------
  describe("formatWeekRange", () => {
    it("should format a week range with weekStartsOn=1 (Monday start)", () => {
      // Jan 13, 2025 is a Monday; week ends Jan 19
      const monday = new Date(2025, 0, 13);
      const result = formatWeekRange(monday, 1);
      expect(result).toMatch(/^Week \d+/);
      expect(result).toMatch(/Jan 13 2025/);
      expect(result).toMatch(/Jan 19 2025/);
    });

    it("should span a month boundary when applicable", () => {
      // Jan 27, 2025 is a Monday (weekStartsOn=1); week ends Feb 2
      const monday = new Date(2025, 0, 27);
      const result = formatWeekRange(monday, 1);
      expect(result).toMatch(/Jan 27 2025/);
      expect(result).toMatch(/Feb 2 2025/);
    });

    it("should include the week number in the output", () => {
      const date = new Date(2025, 0, 6); // Jan 6, first Mon of Jan
      const result = formatWeekRange(date, 1);
      expect(result).toMatch(/^Week 1/);
    });
  });

  // ---------------------------------------------------------------------------
  // getFirstDayOfMonth
  // ---------------------------------------------------------------------------
  describe("getFirstDayOfMonth", () => {
    it("should return the correct day-of-week for Jan 2025 (Wednesday = 3)", () => {
      // Jan 1, 2025 is a Wednesday
      expect(getFirstDayOfMonth(2025, 0)).toBe(3);
    });

    it("should return the correct day-of-week for a known month", () => {
      // July 1, 2024 is a Monday (day = 1)
      expect(getFirstDayOfMonth(2024, 6)).toBe(1);
    });

    it("should return 0 (Sunday) when the month starts on Sunday", () => {
      // Dec 1, 2024 is a Sunday
      expect(getFirstDayOfMonth(2024, 11)).toBe(0);
    });

    it("should return a value between 0 and 6", () => {
      for (let m = 0; m < 12; m++) {
        const day = getFirstDayOfMonth(2024, m);
        expect(day).toBeGreaterThanOrEqual(0);
        expect(day).toBeLessThanOrEqual(6);
      }
    });
  });
});
