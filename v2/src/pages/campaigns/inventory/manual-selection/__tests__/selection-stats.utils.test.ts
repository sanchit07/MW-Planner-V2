import { MediaChannel } from "@constants/inventory.constants";
import type { InventoryItem } from "src/types/inventory.types";
import { describe, expect, it } from "vitest";

import {
  channelOfType,
  computeChannelRows,
  computeFooterTotals,
  statFromItem,
} from "../selection-stats.utils";

const stat = (id: string, cost: number, imp: number, type: string) => ({
  id,
  estimatedCost: cost,
  estimatedImpressions: imp,
  inventoryType: type,
});

describe("channelOfType", () => {
  it("maps digital-prefixed types to digital", () => {
    expect(channelOfType("Digital")).toBe("digital");
    expect(channelOfType("Digital Network")).toBe("digital");
    expect(channelOfType("DIGITAL")).toBe("digital");
  });
  it("maps everything else to classic", () => {
    expect(channelOfType("Transit")).toBe("classic");
    expect(channelOfType("OOH")).toBe("classic");
    expect(channelOfType("")).toBe("classic");
  });
});

describe("statFromItem", () => {
  it("reads id/cost/impression/type off an InventoryItem", () => {
    const item = {
      detail: { id: "a", inventoryType: "Digital" },
      performance: { estimatedCost: 100, estimatedImpression: 2000 },
    } as unknown as InventoryItem;
    expect(statFromItem(item)).toEqual(stat("a", 100, 2000, "Digital"));
  });
  it("falls back to plural estimatedImpressions and 0s", () => {
    const item = {
      detail: { id: "b", inventoryType: "OOH" },
      performance: { estimatedImpressions: 50 },
    } as unknown as InventoryItem;
    expect(statFromItem(item)).toEqual(stat("b", 0, 50, "OOH"));
  });
});

describe("computeFooterTotals", () => {
  const map = new Map([
    ["a", stat("a", 100, 2000, "Digital")],
    ["b", stat("b", 50, 500, "OOH")],
  ]);
  it("sums count/impressions/cost", () => {
    const t = computeFooterTotals(map, 1000);
    expect(t.count).toBe(2);
    expect(t.impressions).toBe(2500);
    expect(t.cost).toBe(150);
    expect(t.overBudget).toBe(false);
    expect(t.overBy).toBe(0);
  });
  it("flags over-budget and reports overBy", () => {
    const t = computeFooterTotals(map, 100);
    expect(t.overBudget).toBe(true);
    expect(t.overBy).toBe(50);
  });
});

describe("computeChannelRows", () => {
  const map = new Map([
    ["a", stat("a", 300, 0, "Digital")],
    ["b", stat("b", 200, 0, "Transit")],
  ]);
  it("builds one row per selected media channel with planned/selected/difference", () => {
    const rows = computeChannelRows(
      map,
      [MediaChannel.DIGITAL_OOH, MediaChannel.CLASSIC_OOH],
      { digital: 40, classic: 60, transit: 0, retail: 0 },
      1000,
    );
    const digital = rows.find((r) => r.key === "digital")!;
    const classic = rows.find((r) => r.key === "classic")!;
    expect(digital.planned).toBe(400);
    expect(digital.selected).toBe(300);
    expect(digital.difference).toBe(-100);
    expect(digital.inventories).toBe(1);
    expect(classic.planned).toBe(600);
    expect(classic.selected).toBe(200); // Transit counts as classic
    expect(classic.difference).toBe(-400);
    expect(classic.inventories).toBe(1);
  });
  it("returns rows only for the campaign's media channels", () => {
    const rows = computeChannelRows(
      map,
      [MediaChannel.DIGITAL_OOH],
      undefined,
      0,
    );
    expect(rows).toHaveLength(1);
    expect(rows[0].key).toBe("digital");
    expect(rows[0].planned).toBe(0);
  });
});
