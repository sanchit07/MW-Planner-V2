import { describe, it, expect, vi } from "vitest";

import type { PresentationTheme } from "../types";
import {
  getThemePrimaryColorStyle,
  getThemePrimaryBackgroundStyle,
  getTrafficLevelFromPercentage,
  getCssVariableValue,
  computeAudienceActivityByDay,
  computeConcentrationIndex,
  CONCENTRATION_INDEX_MAX,
  computeExpectedDelivery,
  computeGoalRoadmap,
  resolveGoalForecast,
  buildPlanReasons,
  humaniseSegment,
} from "../utils";

describe("media-plan utils", () => {
  describe("getThemePrimaryColorStyle", () => {
    it("returns default primary color when theme is undefined", () => {
      const result = getThemePrimaryColorStyle(undefined);
      expect(result).toEqual({ color: "var(--color-mw-primary-500)" });
    });

    it("returns default primary color when theme is null", () => {
      const result = getThemePrimaryColorStyle(null);
      expect(result).toEqual({ color: "var(--color-mw-primary-500)" });
    });

    it("returns theme primary var when theme is provided", () => {
      const theme: PresentationTheme = {
        id: "primary",
        name: "Test",
        colors: {
          primary: "--color-mw-primary-500",
          secondary: "--color-mw-primary-400",
          accent: "--color-mw-primary-300",
        },
      };
      const result = getThemePrimaryColorStyle(theme);
      expect(result).toEqual({
        color: "var(--color-mw-primary-500)",
      });
    });
  });

  describe("getTrafficLevelFromPercentage", () => {
    it("returns high when value >= 50", () => {
      expect(getTrafficLevelFromPercentage(50)).toBe("high");
      expect(getTrafficLevelFromPercentage(100)).toBe("high");
      expect(getTrafficLevelFromPercentage(75)).toBe("high");
    });

    it("returns medium when value >= 25 and < 50", () => {
      expect(getTrafficLevelFromPercentage(25)).toBe("medium");
      expect(getTrafficLevelFromPercentage(49)).toBe("medium");
      expect(getTrafficLevelFromPercentage(30)).toBe("medium");
    });

    it("returns low when value < 25", () => {
      expect(getTrafficLevelFromPercentage(0)).toBe("low");
      expect(getTrafficLevelFromPercentage(24)).toBe("low");
      expect(getTrafficLevelFromPercentage(10)).toBe("low");
    });
  });

  describe("getCssVariableValue", () => {
    it("returns known fallback for success-500 when DOM returns empty", () => {
      const getPropertyValue = vi.fn().mockReturnValue("");
      vi.stubGlobal(
        "getComputedStyle",
        vi.fn().mockReturnValue({ getPropertyValue }),
      );
      expect(getCssVariableValue("--color-mw-success-500")).toBe("#2d7d32");
    });

    it("returns default fallback for unknown var when DOM returns empty", () => {
      const getPropertyValue = vi.fn().mockReturnValue("");
      vi.stubGlobal(
        "getComputedStyle",
        vi.fn().mockReturnValue({ getPropertyValue }),
      );
      expect(getCssVariableValue("--unknown-var")).toBe("#2176cc");
    });

    it("returns trimmed value from getComputedStyle when available", () => {
      const getPropertyValue = vi
        .fn()
        .mockImplementation((name: string) =>
          name === "--color-mw-primary-500" ? " #abc123 " : "",
        );
      vi.stubGlobal(
        "getComputedStyle",
        vi.fn().mockReturnValue({ getPropertyValue }),
      );
      expect(getCssVariableValue("--color-mw-primary-500")).toBe("#abc123");
    });
  });

  describe("getThemePrimaryBackgroundStyle", () => {
    it("returns default primary bg when theme is undefined", () => {
      expect(getThemePrimaryBackgroundStyle(undefined)).toEqual({
        backgroundColor: "var(--color-mw-primary-500)",
      });
    });

    it("uses the theme primary variable when a theme is given", () => {
      const theme = {
        colors: { primary: "--color-x" },
      } as unknown as PresentationTheme;
      expect(getThemePrimaryBackgroundStyle(theme)).toEqual({
        backgroundColor: "var(--color-x)",
      });
    });
  });

  describe("computeAudienceActivityByDay", () => {
    // 2026-07-27 = Monday. bookingMatrix key → weekday.
    const sched = (matrix: Record<string, number[]>) => ({
      schedules: [{ bookingMatrix: matrix }],
    });

    it("returns 7 days Mon–Sun; flat 14.3% with no schedule data", () => {
      const r = computeAudienceActivityByDay([]);
      expect(r.bars.map((d) => d.day)).toEqual([
        "Mon",
        "Tue",
        "Wed",
        "Thu",
        "Fri",
        "Sat",
        "Sun",
      ]);
      expect(r.bars.every((d) => Math.abs(d.sharePct - 100 / 7) < 0.001)).toBe(
        true,
      );
      expect(r.peakDay).toBeNull();
      expect(r.hasCustomSchedule).toBe(false);
      expect(r.hasScheduleData).toBe(false);
    });

    it("unions distinct hours across inventories (overlap counts once)", () => {
      // Both inventories on Monday: A={9,10,11}, B={10,11,12} → union 4 hours.
      const r = computeAudienceActivityByDay([
        sched({ "2026-07-27": [9, 10, 11] }),
        sched({ "2026-07-27": [10, 11, 12] }),
      ]);
      expect(r.bars[0].hours).toBe(4); // Monday
      expect(r.hasScheduleData).toBe(true);
      expect(r.hasCustomSchedule).toBe(true); // <24h → custom
    });

    it("unions the same weekday across multiple dates", () => {
      // Two Mondays (27 Jul, 3 Aug) → union {8,9} ∪ {9,10} = {8,9,10} = 3.
      const r = computeAudienceActivityByDay([
        sched({ "2026-07-27": [8, 9], "2026-08-03": [9, 10] }),
      ]);
      expect(r.bars[0].hours).toBe(3);
    });

    it("marks the single busiest weekday as peak", () => {
      const r = computeAudienceActivityByDay([
        sched({ "2026-07-27": [0, 1, 2, 3], "2026-07-28": [0, 1] }), // Mon 4h, Tue 2h
      ]);
      expect(r.peakDay).toBe("Mon");
      expect(r.bars[0].isPeak).toBe(true);
      expect(r.bars[1].isPeak).toBe(false);
    });

    it("returns peakDay null on a tie", () => {
      const r = computeAudienceActivityByDay([
        sched({ "2026-07-27": [0, 1], "2026-07-28": [0, 1] }), // Mon 2h = Tue 2h
      ]);
      expect(r.peakDay).toBeNull();
      expect(r.bars.every((d) => !d.isPeak)).toBe(true);
    });
  });

  describe("computeExpectedDelivery", () => {
    it("bins monthly for flights longer than 8 weeks", () => {
      const r = computeExpectedDelivery("2026-04-01", "2026-06-30", 910000);
      expect(r.granularity).toBe("monthly");
      expect(r.bins.map((b) => b.label)).toEqual([
        "Apr 2026",
        "May 2026",
        "Jun 2026",
      ]);
      // Sine-wave-modulated split rises with bin index over this range →
      // the last bin is the peak, independent of each month's day count.
      expect(r.peakLabel).toBe("Jun 2026");
    });

    it("distributes impressions via sine-wave-modulated equal split", () => {
      const r = computeExpectedDelivery("2026-04-01", "2026-06-30", 910000);
      expect(r.bins.map((b) => b.value)).toEqual([282214, 306117, 321669]);
      // bins sum ≈ total (rounding aside)
      const sum = r.bins.reduce((s, b) => s + b.value, 0);
      expect(Math.abs(sum - 910000)).toBeLessThanOrEqual(2);
    });

    it("distributes reach via a half-amplitude sine-wave-modulated split", () => {
      const r = computeExpectedDelivery(
        "2026-04-01",
        "2026-06-30",
        910000,
        910000,
      );
      expect(r.bins.map((b) => b.reach)).toEqual([292393, 304775, 312832]);
      const sum = r.bins.reduce((s, b) => s + b.reach, 0);
      expect(Math.abs(sum - 910000)).toBeLessThanOrEqual(2);
    });

    it("bins weekly for flights up to 8 weeks", () => {
      const r = computeExpectedDelivery("2026-05-01", "2026-05-31", 100000);
      expect(r.granularity).toBe("weekly");
      expect(r.bins.length).toBeGreaterThanOrEqual(4);
    });

    it("returns empty on missing/invalid dates", () => {
      expect(computeExpectedDelivery(undefined, undefined, 100).bins).toEqual(
        [],
      );
      expect(
        computeExpectedDelivery("2026-06-30", "2026-04-01", 100).bins,
      ).toEqual([]);
    });
  });

  // ─── PRD §10.5.7 goal roadmap ──────────────────────────────────────────────
  describe("computeGoalRoadmap", () => {
    // Inclusive end date `days` after start (day 1 == start).
    const endAfter = (start: string, days: number) => {
      const d = new Date(start);
      d.setDate(d.getDate() + days - 1);
      return d.toISOString().slice(0, 10);
    };

    it("always returns exactly three phases, ordinals 1/2/3", () => {
      const r = computeGoalRoadmap("2026-01-01", endAfter("2026-01-01", 45));
      expect(r.map((m) => m.phase)).toEqual(["ramp", "mid_flight", "closeout"]);
      expect(r.map((m) => m.ordinal)).toEqual([1, 2, 3]);
    });

    it("splits into three equal thirds by calendar days (date ranges)", () => {
      // 45 days from Jan 1 → thirds of 15 days: 1–15, 16–30, 31–45.
      const r = computeGoalRoadmap("2026-01-01", endAfter("2026-01-01", 45));
      expect(r[0].dateRange).toBe("Jan 1 – Jan 15");
      expect(r[1].dateRange).toBe("Jan 16 – Jan 30");
      expect(r[2].dateRange).toBe("Jan 31 – Feb 14");
    });

    it("picks the label unit once from total duration", () => {
      expect(
        computeGoalRoadmap("2026-01-01", endAfter("2026-01-01", 45))[0].unit,
      ).toBe("week");
      expect(
        computeGoalRoadmap("2026-01-01", endAfter("2026-01-01", 90))[0].unit,
      ).toBe("week");
      expect(
        computeGoalRoadmap("2026-01-01", endAfter("2026-01-01", 287))[0].unit,
      ).toBe("month");
      expect(
        computeGoalRoadmap("2026-01-01", endAfter("2026-01-01", 14))[0].unit,
      ).toBe("day");
      expect(
        computeGoalRoadmap("2026-01-01", endAfter("2026-01-01", 700))[0].unit,
      ).toBe("quarter");
    });

    it("achievement %: Forecast 11M vs Target 10M → 29 / 81 / 110", () => {
      const r = computeGoalRoadmap(
        "2026-01-01",
        endAfter("2026-01-01", 45),
        11_000_000,
        10_000_000,
      );
      expect(r.map((m) => m.pct)).toEqual([29, 81, 110]);
    });

    it("no target → target defaults to forecast → closeout 100%", () => {
      const r = computeGoalRoadmap(
        "2026-01-01",
        endAfter("2026-01-01", 45),
        500_000,
        0,
      );
      expect(r.map((m) => m.pct)).toEqual([26, 74, 100]);
    });

    it("returns empty on invalid dates", () => {
      expect(computeGoalRoadmap(undefined, undefined)).toEqual([]);
    });
  });

  describe("resolveGoalForecast", () => {
    const perf = {
      estimatedImpression: 100,
      estimatedAdPlays: 200,
      sov: 30,
      estimatedReach: 400,
    };
    it("selects the metric matching the goal type", () => {
      expect(resolveGoalForecast("IMPRESSIONS", perf)).toBe(100);
      expect(resolveGoalForecast("ADPLAYS", perf)).toBe(200);
      expect(resolveGoalForecast("SOV", perf)).toBe(30);
      expect(resolveGoalForecast("REACH", perf)).toBe(400);
      expect(resolveGoalForecast(undefined, perf)).toBe(400);
    });
  });

  describe("buildPlanReasons", () => {
    const base = {
      topCityName: "Orchard",
      inventories: 6,
      impressions: 11_000_000,
      cpm: 33,
      compact: (n: number) => `${n}`,
      currency: (n: number) => `USD ${n}`,
    };

    it("reason 3 uses demographics when present", () => {
      const r = buildPlanReasons({
        ...base,
        demographicSegments: ["18–24", "25–34"],
        venueTypes: ["Mall"],
      });
      expect(r).toHaveLength(3);
      expect(r[2].key).toBe("reason_audience");
      expect(r[2].params.segments).toBe("18–24 + 25–34");
    });

    it("reason 3 falls back to venue types, then generic", () => {
      const venue = buildPlanReasons({
        ...base,
        demographicSegments: [],
        venueTypes: ["Mall", "Transit"],
      });
      expect(venue[2].key).toBe("reason_audience_venue");
      expect(venue[2].params.venues).toBe("Mall + Transit");

      const generic = buildPlanReasons({
        ...base,
        demographicSegments: [],
        venueTypes: [],
      });
      expect(generic[2].key).toBe("reason_audience_generic");
    });
  });

  describe("computeConcentrationIndex", () => {
    it("always returns the four fixed rows in order", () => {
      const rows = computeConcentrationIndex({});
      expect(rows.map((r) => r.key)).toEqual([
        "age_gender",
        "income_group",
        "behavior",
        "interest",
      ]);
    });

    it("every index sits within the 1.4×–3.6× band", () => {
      const rows = computeConcentrationIndex({
        ageGender: ["18_24", "male"],
        income: ["high"],
        behavior: ["Commuters"],
        interest: ["Travel"],
      });
      rows.forEach((r) => {
        expect(r.index).toBeGreaterThanOrEqual(1.4);
        expect(r.index).toBeLessThanOrEqual(CONCENTRATION_INDEX_MAX);
      });
    });

    it("is deterministic for identical selections", () => {
      const input = { ageGender: ["18_24"], behavior: ["Commuters"] };
      expect(computeConcentrationIndex(input)).toEqual(
        computeConcentrationIndex(input),
      );
    });

    it("reacts to the selected values (not constant)", () => {
      const a = computeConcentrationIndex({ behavior: ["Commuters"] });
      const b = computeConcentrationIndex({ behavior: ["Shoppers"] });
      expect(a[2].index).not.toBe(b[2].index); // Behavior row differs
    });
  });

  describe("humaniseSegment", () => {
    it("formats age ranges with an en-dash", () => {
      expect(humaniseSegment("18_24")).toBe("18–24");
    });
    it("title-cases word codes", () => {
      expect(humaniseSegment("young_adult")).toBe("Young Adult");
    });
  });
});
