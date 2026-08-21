import { InventorySchedule } from "src/types/inventory.types";
import { beforeEach, describe, it, expect } from "vitest";

import { applySchedulePattern, convertDayNameBack } from "../schedule.utils";
import {
  captureDefaultSchedule,
  clearDefaultSchedule,
  getDefaultSchedule,
  mapScheduleToFormState,
  resolveSchedulePattern,
  schedulesMatch,
} from "../scheduleDefaults";

const ALL_DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

const CAMPAIGN_ID = "camp-1";
const INVENTORY_ID = "inv-1";

const buildSchedule = (
  overrides: Partial<InventorySchedule> = {},
): InventorySchedule =>
  ({
    startDate: "2024-01-01",
    endDate: "2024-01-07",
    scheduleDays: ["MONDAY", "TUESDAY"],
    bookingMatrix: { "2024-01-01": [9, 10] },
    duration: 15,
    spotsPerLoop: 2,
    spotsPerHour: 30,
    order: 1,
    pricing: 0,
    ...overrides,
  }) as InventorySchedule;

describe("scheduleDefaults", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  describe("getDefaultSchedule", () => {
    it("returns null when nothing is stored", () => {
      expect(getDefaultSchedule(CAMPAIGN_ID, INVENTORY_ID)).toBeNull();
    });

    it("returns null when ids are missing", () => {
      expect(getDefaultSchedule(undefined, INVENTORY_ID)).toBeNull();
      expect(getDefaultSchedule(CAMPAIGN_ID, undefined)).toBeNull();
    });
  });

  describe("captureDefaultSchedule", () => {
    it("stores the schedule and reads it back", () => {
      const schedule = buildSchedule();
      captureDefaultSchedule(CAMPAIGN_ID, INVENTORY_ID, schedule);
      expect(getDefaultSchedule(CAMPAIGN_ID, INVENTORY_ID)).toEqual(schedule);
    });

    it("captures only once - later calls do not overwrite the original", () => {
      const original = buildSchedule({ duration: 15 });
      const edited = buildSchedule({ duration: 30 });
      captureDefaultSchedule(CAMPAIGN_ID, INVENTORY_ID, original);
      captureDefaultSchedule(CAMPAIGN_ID, INVENTORY_ID, edited);
      expect(getDefaultSchedule(CAMPAIGN_ID, INVENTORY_ID)?.duration).toBe(15);
    });

    it("does nothing when ids are missing", () => {
      captureDefaultSchedule(undefined, INVENTORY_ID, buildSchedule());
      expect(getDefaultSchedule(CAMPAIGN_ID, INVENTORY_ID)).toBeNull();
    });

    it("scopes snapshots per inventory", () => {
      captureDefaultSchedule(CAMPAIGN_ID, "inv-a", buildSchedule({ order: 1 }));
      captureDefaultSchedule(CAMPAIGN_ID, "inv-b", buildSchedule({ order: 2 }));
      expect(getDefaultSchedule(CAMPAIGN_ID, "inv-a")?.order).toBe(1);
      expect(getDefaultSchedule(CAMPAIGN_ID, "inv-b")?.order).toBe(2);
    });
  });

  describe("clearDefaultSchedule", () => {
    it("removes the stored snapshot", () => {
      captureDefaultSchedule(CAMPAIGN_ID, INVENTORY_ID, buildSchedule());
      clearDefaultSchedule(CAMPAIGN_ID, INVENTORY_ID);
      expect(getDefaultSchedule(CAMPAIGN_ID, INVENTORY_ID)).toBeNull();
    });
  });

  describe("schedulesMatch", () => {
    it("returns true for schedules with identical defining fields", () => {
      expect(schedulesMatch(buildSchedule(), buildSchedule())).toBe(true);
    });

    it("ignores ids, name, order, pricing and metrics", () => {
      const a = buildSchedule({ id: "a", name: "X", order: 1, pricing: 0 });
      const b = buildSchedule({ id: "b", name: "Y", order: 5, pricing: 99 });
      expect(schedulesMatch(a, b)).toBe(true);
    });

    it("treats day order as irrelevant", () => {
      const a = buildSchedule({ scheduleDays: ["MONDAY", "TUESDAY"] });
      const b = buildSchedule({ scheduleDays: ["TUESDAY", "MONDAY"] });
      expect(schedulesMatch(a, b)).toBe(true);
    });

    it("treats booking-matrix hour order as irrelevant", () => {
      const a = buildSchedule({ bookingMatrix: { "2024-01-01": [9, 10] } });
      const b = buildSchedule({ bookingMatrix: { "2024-01-01": [10, 9] } });
      expect(schedulesMatch(a, b)).toBe(true);
    });

    it("returns false when hours differ", () => {
      const a = buildSchedule({ bookingMatrix: { "2024-01-01": [9, 10] } });
      const b = buildSchedule({ bookingMatrix: { "2024-01-01": [9, 11] } });
      expect(schedulesMatch(a, b)).toBe(false);
    });

    it("returns false when dates or duration differ", () => {
      expect(
        schedulesMatch(
          buildSchedule(),
          buildSchedule({ endDate: "2024-02-01" }),
        ),
      ).toBe(false);
      expect(
        schedulesMatch(buildSchedule(), buildSchedule({ duration: 30 })),
      ).toBe(false);
    });

    it("returns false when either side is null", () => {
      expect(schedulesMatch(buildSchedule(), null)).toBe(false);
      expect(schedulesMatch(null, buildSchedule())).toBe(false);
    });
  });

  describe("resolveSchedulePattern", () => {
    const matrixFromHours = (hours: Set<string>): Record<string, number[]> => {
      const matrix: Record<string, number[]> = {};
      hours.forEach((key) => {
        const i = key.lastIndexOf("-");
        const date = key.slice(0, i);
        (matrix[date] ??= []).push(Number(key.slice(i + 1)));
      });
      return matrix;
    };

    const businessSchedule = (): InventorySchedule => {
      const { days, hours } = applySchedulePattern(
        "business",
        { from: new Date("2024-01-01"), to: new Date("2024-01-07") },
        ALL_DAYS,
      );
      return buildSchedule({
        scheduleDays: days.map(
          convertDayNameBack,
        ) as InventorySchedule["scheduleDays"],
        bookingMatrix: matrixFromHours(hours),
      });
    };

    it("returns 'default' when the schedule matches the snapshot", () => {
      const schedule = businessSchedule();
      expect(resolveSchedulePattern(schedule, schedule, ALL_DAYS, 60)).toBe(
        "default",
      );
    });

    it("returns the matching generated pattern when not the default", () => {
      expect(
        resolveSchedulePattern(businessSchedule(), null, ALL_DAYS, 60),
      ).toBe("business");
    });

    it("returns 'custom' when it is neither the default nor a known pattern", () => {
      expect(resolveSchedulePattern(buildSchedule(), null, ALL_DAYS, 60)).toBe(
        "custom",
      );
    });
  });

  describe("mapScheduleToFormState", () => {
    it("maps schedule fields into form state", () => {
      const form = mapScheduleToFormState(buildSchedule(), 60);
      expect(form.scheduleDate.from).toEqual(new Date("2024-01-01"));
      expect(form.scheduleDate.to).toEqual(new Date("2024-01-07"));
      expect(form.selectedDays).toEqual(["Mon", "Tue"]);
      expect(form.duration).toBe("15 Sec");
      expect(form.spots).toEqual({ perLoop: 2, perHour: 30 });
    });

    it("expands bookingMatrix into the selectedHours set", () => {
      const form = mapScheduleToFormState(buildSchedule(), 60);
      expect(form.selectedHours.has("2024-01-01-9")).toBe(true);
      expect(form.selectedHours.has("2024-01-01-10")).toBe(true);
      expect(form.selectedHours.size).toBe(2);
    });

    it("applies fallbacks when fields are absent", () => {
      const form = mapScheduleToFormState(
        buildSchedule({
          scheduleDays: undefined,
          duration: undefined,
          spotsPerLoop: undefined,
          spotsPerHour: undefined,
          bookingMatrix: undefined,
        }),
        72,
      );
      expect(form.selectedDays).toEqual([]);
      expect(form.duration).toBe("15 Sec");
      expect(form.spots).toEqual({ perLoop: 1, perHour: 72 });
      expect(form.selectedHours.size).toBe(0);
    });
  });
});
