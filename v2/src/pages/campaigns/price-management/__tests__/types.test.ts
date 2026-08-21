import { describe, it, expect } from "vitest";

import {
  distributeProRata,
  getPendingPriceDelta,
  getPendingScheduleDelta,
  PendingPriceEdits,
} from "../types";

const inventoryEdit = (
  inventoryId: string,
  originalPrice: number,
  newPrice: number,
) => ({
  newPrice,
  originalPrice,
  campaignInventoryScheduleId: `cis-${inventoryId}`,
  isInventoryRow: true,
  inventoryId,
  label: inventoryId,
});

const scheduleEdit = (
  inventoryId: string,
  scheduleId: string,
  originalPrice: number,
  newPrice: number,
) => ({
  newPrice,
  originalPrice,
  campaignInventoryScheduleId: `cis-${inventoryId}`,
  scheduleId,
  isInventoryRow: false,
  inventoryId,
  label: scheduleId,
});

describe("getPendingPriceDelta", () => {
  it("is zero when nothing is staged", () => {
    expect(getPendingPriceDelta({})).toBe(0);
  });

  it("returns a negative delta when a price is lowered", () => {
    const edits: PendingPriceEdits = {
      "inv-1": inventoryEdit("inv-1", 10300, 10200),
    };

    expect(getPendingPriceDelta(edits)).toBe(-100);
  });

  it("returns a positive delta when a price is raised", () => {
    const edits: PendingPriceEdits = {
      "inv-1": inventoryEdit("inv-1", 2200, 2500),
    };

    expect(getPendingPriceDelta(edits)).toBe(300);
  });

  it("sums deltas across different inventories", () => {
    const edits: PendingPriceEdits = {
      "inv-1": inventoryEdit("inv-1", 1000, 900),
      "inv-2": inventoryEdit("inv-2", 500, 800),
    };

    expect(getPendingPriceDelta(edits)).toBe(200);
  });

  it("counts schedule edits when their inventory is not edited", () => {
    const edits: PendingPriceEdits = {
      "inv-1:sch-1": scheduleEdit("inv-1", "sch-1", 900, 800),
      "inv-1:sch-2": scheduleEdit("inv-1", "sch-2", 500, 450),
    };

    expect(getPendingPriceDelta(edits)).toBe(-150);
  });

  it("ignores schedule edits when their own inventory is also edited", () => {
    // An inventory's proposed price already aggregates its schedules, so
    // counting both would apply the change twice.
    const edits: PendingPriceEdits = {
      "inv-1": inventoryEdit("inv-1", 2200, 2000),
      "inv-1:sch-1": scheduleEdit("inv-1", "sch-1", 900, 700),
    };

    expect(getPendingPriceDelta(edits)).toBe(-200);
  });

  it("only suppresses schedules of the edited inventory, not others", () => {
    const edits: PendingPriceEdits = {
      "inv-1": inventoryEdit("inv-1", 2200, 2000),
      "inv-1:sch-1": scheduleEdit("inv-1", "sch-1", 900, 700),
      "inv-2:sch-9": scheduleEdit("inv-2", "sch-9", 300, 250),
    };

    // -200 from inv-1, -50 from inv-2's schedule, inv-1's schedule ignored
    expect(getPendingPriceDelta(edits)).toBe(-250);
  });
});

describe("getPendingScheduleDelta", () => {
  it("is zero when the inventory has no staged schedule edits", () => {
    expect(getPendingScheduleDelta({}, "inv-1")).toBe(0);
  });

  it("sums the staged schedule edits of that inventory", () => {
    const edits: PendingPriceEdits = {
      "inv-1:sch-1": scheduleEdit("inv-1", "sch-1", 900, 800),
      "inv-1:sch-2": scheduleEdit("inv-1", "sch-2", 500, 550),
    };

    expect(getPendingScheduleDelta(edits, "inv-1")).toBe(-50);
  });

  it("ignores schedule edits belonging to other inventories", () => {
    const edits: PendingPriceEdits = {
      "inv-1:sch-1": scheduleEdit("inv-1", "sch-1", 900, 800),
      "inv-2:sch-9": scheduleEdit("inv-2", "sch-9", 300, 100),
    };

    expect(getPendingScheduleDelta(edits, "inv-1")).toBe(-100);
  });

  it("ignores inventory-level edits", () => {
    const edits: PendingPriceEdits = {
      "inv-1": inventoryEdit("inv-1", 2200, 2000),
    };

    expect(getPendingScheduleDelta(edits, "inv-1")).toBe(0);
  });
});

describe("distributeProRata", () => {
  const schedules = [
    { id: "s1", currentPrice: 1400 },
    { id: "s2", currentPrice: 300 },
    { id: "s3", currentPrice: 100 },
  ];

  const sum = (result: Array<{ newPrice: number }>) =>
    Math.round(result.reduce((total, r) => total + r.newPrice, 0) * 100) / 100;

  it("returns nothing when there are no schedules", () => {
    expect(distributeProRata(1000, [])).toEqual([]);
  });

  it("splits by share of the total, never equally", () => {
    const result = distributeProRata(1000, schedules);

    // 1400/1800, 300/1800, 100/1800 of 1000
    expect(result).toEqual([
      { id: "s1", newPrice: 777.77 },
      { id: "s2", newPrice: 166.67 },
      { id: "s3", newPrice: 55.56 },
    ]);
  });

  it("always adds back up to the new total", () => {
    expect(sum(distributeProRata(1000, schedules))).toBe(1000);
    expect(sum(distributeProRata(1234.56, schedules))).toBe(1234.56);
    expect(sum(distributeProRata(7, schedules))).toBe(7);
  });

  it("never drives a schedule negative, even on a steep cut", () => {
    // The case an equal split breaks on: 100 cannot absorb 800/3
    const result = distributeProRata(10, schedules);

    result.forEach((schedule) => {
      expect(schedule.newPrice).toBeGreaterThanOrEqual(0);
    });
    expect(sum(result)).toBe(10);
  });

  it("zeroes every schedule when the inventory goes to zero", () => {
    const result = distributeProRata(0, schedules);

    expect(result).toEqual([
      { id: "s1", newPrice: 0 },
      { id: "s2", newPrice: 0 },
      { id: "s3", newPrice: 0 },
    ]);
  });

  it("treats a negative total as zero", () => {
    expect(sum(distributeProRata(-500, schedules))).toBe(0);
  });

  it("handles an increase the same way", () => {
    const result = distributeProRata(3600, schedules);

    expect(result).toEqual([
      { id: "s1", newPrice: 2800 },
      { id: "s2", newPrice: 600 },
      { id: "s3", newPrice: 200 },
    ]);
  });

  it("falls back to an equal split when every schedule is at zero", () => {
    const result = distributeProRata(90, [
      { id: "s1", currentPrice: 0 },
      { id: "s2", currentPrice: 0 },
      { id: "s3", currentPrice: 0 },
    ]);

    expect(result).toEqual([
      { id: "s1", newPrice: 30 },
      { id: "s2", newPrice: 30 },
      { id: "s3", newPrice: 30 },
    ]);
  });

  it("gives a single schedule the whole value", () => {
    expect(distributeProRata(1000, [{ id: "s1", currentPrice: 250 }])).toEqual([
      { id: "s1", newPrice: 1000 },
    ]);
  });
});
