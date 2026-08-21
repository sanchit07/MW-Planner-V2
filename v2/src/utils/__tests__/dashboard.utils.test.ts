import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

import {
  formatSummaryCurrency,
  formatCompactNumber,
  formatChartTooltipValue,
  formatChartTooltipDate,
  formatChartYAxisValue,
  getChartCurrentDateIndex,
  getPeriodLabel,
  getPercentageColorClass,
  calculateDateRangeForPeriod,
  bucketDayWiseChartData,
  type PeriodOption,
  type DateRange,
} from "../dashboard.utils";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

describe("dashboard.utils", () => {
  describe("formatSummaryCurrency", () => {
    it("formats number with default currency MYR", () => {
      expect(formatSummaryCurrency(1000)).toMatch(/MYR\s+1/);
    });

    it("formats number with custom currency", () => {
      expect(formatSummaryCurrency(500, "USD")).toMatch(/USD\s+500/);
    });

    it("returns currency 0 for NaN", () => {
      expect(formatSummaryCurrency(Number.NaN)).toBe("MYR 0");
    });

    it("returns currency 0 for non-number type", () => {
      expect(formatSummaryCurrency("x" as unknown as number)).toBe("MYR 0");
    });
  });

  describe("formatCompactNumber", () => {
    it("formats millions with M suffix", () => {
      expect(formatCompactNumber(1_500_000)).toBe("1.5M");
    });

    it("formats thousands with K suffix", () => {
      expect(formatCompactNumber(5_000)).toBe("5K");
    });

    it("uses decimals for K when specified", () => {
      expect(formatCompactNumber(5_500, 1)).toBe("5.5K");
    });

    it("returns string for value less than 1000", () => {
      expect(formatCompactNumber(500)).toBe("500");
    });
  });

  describe("formatChartTooltipValue", () => {
    it("returns label and compact value with default decimals", () => {
      expect(formatChartTooltipValue(1200, "Impressions")).toBe(
        "Impressions: 1K",
      );
    });

    it("uses decimals when provided", () => {
      expect(formatChartTooltipValue(1500, "Reach", 1)).toBe("Reach: 1.5K");
    });
  });

  describe("formatChartTooltipDate", () => {
    it("appends current year to date label", () => {
      const result = formatChartTooltipDate("10 Feb");
      expect(result).toContain("10 Feb");
      expect(result).toContain(String(new Date().getFullYear()));
    });
  });

  describe("formatChartYAxisValue", () => {
    it("returns compact number for axis", () => {
      expect(formatChartYAxisValue(2000)).toBe("2K");
    });
  });

  describe("getChartCurrentDateIndex", () => {
    it("returns 0 when labelsLength is 0", () => {
      expect(getChartCurrentDateIndex("last-7-days", 0)).toBe(0);
    });

    it("returns last index for non-yearly period", () => {
      expect(getChartCurrentDateIndex("last-7-days", 7)).toBe(6);
    });

    it("returns min(11, labelsLength-1) for yearly period", () => {
      expect(getChartCurrentDateIndex("yearly", 12)).toBe(11);
    });

    it("returns labelsLength-1 for yearly when labels less than 12", () => {
      expect(getChartCurrentDateIndex("yearly", 5)).toBe(4);
    });
  });

  describe("getPercentageColorClass", () => {
    it("returns text-mw-success-500 when value is less than 60", () => {
      expect(getPercentageColorClass(0)).toBe("text-mw-success-500");
      expect(getPercentageColorClass(59)).toBe("text-mw-success-500");
      expect(getPercentageColorClass(59.99)).toBe("text-mw-success-500");
    });

    it("returns text-mw-warning-500 when value is between 60 and 80 inclusive", () => {
      expect(getPercentageColorClass(60)).toBe("text-mw-warning-500");
      expect(getPercentageColorClass(70)).toBe("text-mw-warning-500");
      expect(getPercentageColorClass(80)).toBe("text-mw-warning-500");
    });

    it("returns text-mw-error-500 when value is greater than 80", () => {
      expect(getPercentageColorClass(80.01)).toBe("text-mw-error-500");
      expect(getPercentageColorClass(100)).toBe("text-mw-error-500");
    });
  });

  describe("getPeriodLabel", () => {
    it("returns correct label for each period", () => {
      expect(getPeriodLabel("last-7-days")).toBe("filters.last7Days");
      expect(getPeriodLabel("last-30-days")).toBe("filters.last30Days");
      expect(getPeriodLabel("last-month")).toBe("filters.lastMonth");
      expect(getPeriodLabel("quarterly")).toBe("filters.quarterly");
      expect(getPeriodLabel("yearly")).toBe("filters.yearly");
      expect(getPeriodLabel("date-range")).toBe("filters.dateRange");
    });

    it("returns Select period for unknown period", () => {
      expect(getPeriodLabel("unknown" as PeriodOption)).toBe("Select period");
    });
  });

  describe("calculateDateRangeForPeriod", () => {
    const fixedDate = new Date("2026-02-15T12:00:00.000Z");

    beforeEach(() => {
      vi.useFakeTimers();
      vi.setSystemTime(fixedDate);
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it("returns last 7 days range", () => {
      const result = calculateDateRangeForPeriod("last-7-days");
      expect(result.startDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(result.endDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(new Date(result.startDate).getTime()).toBeLessThanOrEqual(
        new Date(result.endDate).getTime(),
      );
    });

    it("returns last 30 days range", () => {
      const result = calculateDateRangeForPeriod("last-30-days");
      expect(result.startDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(result.endDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    });

    it("returns previous month for last-month", () => {
      const result = calculateDateRangeForPeriod("last-month");
      expect(result.startDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(result.endDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    });

    it("returns current quarter for quarterly", () => {
      const result = calculateDateRangeForPeriod("quarterly");
      expect(result.startDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(result.endDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    });

    it("returns current year for yearly", () => {
      const result = calculateDateRangeForPeriod("yearly");
      expect(result.startDate).toMatch(/^\d{4}-01-01$/);
      expect(result.endDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(new Date(result.startDate).getTime()).toBeLessThanOrEqual(
        new Date(result.endDate).getTime(),
      );
    });

    it("uses dateRange when period is date-range and from/to provided", () => {
      const dateRange: DateRange = {
        from: new Date("2026-01-01"),
        to: new Date("2026-01-31"),
      };
      const result = calculateDateRangeForPeriod("date-range", dateRange);
      expect(result.startDate).toBe("2026-01-01");
      expect(result.endDate).toBe("2026-01-31");
    });

    it("falls back to last 7 days when date-range has no from/to", () => {
      const result = calculateDateRangeForPeriod("date-range");
      expect(result.startDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(result.endDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    });

    it("treats a single clicked date (from only, no to) as a single-day range instead of falling back to last 7 days", () => {
      const dateRange: DateRange = {
        from: new Date("2026-01-15"),
        to: null,
      };
      const result = calculateDateRangeForPeriod("date-range", dateRange);
      expect(result.startDate).toBe("2026-01-15");
      expect(result.endDate).toBe("2026-01-15");
    });
  });

  describe("bucketDayWiseChartData", () => {
    it("returns empty labels and metric arrays when dayWiseData has no numeric metrics", () => {
      const result = bucketDayWiseChartData({
        dayWiseData: {},
        period: "last-7-days",
        startDate: "2026-01-01",
        endDate: "2026-01-07",
      });
      expect(result.labels).toEqual([]);
    });

    it("returns empty result when date range has no days", () => {
      const result = bucketDayWiseChartData({
        dayWiseData: { "2026-01-01": { impressions: 100 } },
        period: "last-7-days",
        startDate: "2026-01-02",
        endDate: "2026-01-01",
      });
      expect(result.labels).toEqual([]);
    });

    it("buckets day-wise data for last-7-days period", () => {
      const dayWiseData = {
        "2026-01-01": { impressions: 100, reach: 50 },
        "2026-01-02": { impressions: 200, reach: 80 },
      };
      const result = bucketDayWiseChartData({
        dayWiseData,
        period: "last-7-days",
        startDate: "2026-01-01",
        endDate: "2026-01-02",
      });
      expect(result.labels.length).toBeGreaterThan(0);
      expect(result.impressions).toBeDefined();
      expect(result.reach).toBeDefined();
      expect(Array.isArray(result.impressions)).toBe(true);
      expect(Array.isArray(result.reach)).toBe(true);
    });

    it("buckets by year for yearly period", () => {
      const dayWiseData = {
        "2026-01-01": { cost: 100 },
        "2026-06-15": { cost: 200 },
      };
      const result = bucketDayWiseChartData({
        dayWiseData,
        period: "yearly",
        startDate: "2026-01-01",
        endDate: "2026-12-31",
      });
      expect(result.labels).toContain("2026");
      expect(result.cost).toBeDefined();
      expect(Array.isArray(result.cost)).toBe(true);
    });

    it("buckets by quarter for quarterly period", () => {
      const dayWiseData = {
        "2026-01-10": { revenue: 500 },
        "2026-02-10": { revenue: 600 },
      };
      const result = bucketDayWiseChartData({
        dayWiseData,
        period: "quarterly",
        startDate: "2026-01-01",
        endDate: "2026-03-31",
      });
      expect(result.labels.length).toBeGreaterThan(0);
      expect(result.revenue).toBeDefined();
    });
  });
});
