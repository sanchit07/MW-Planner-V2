import { describe, expect, it } from "vitest";

import type { InventoryItem } from "../../../../types/inventory.types";
import { excludeByReferenceId, markAllSelected } from "../twoPhaseList";

const item = (id: string, ref: string, isSelected = false): InventoryItem =>
  ({
    detail: { id, referenceId: ref, isSelected },
  }) as unknown as InventoryItem;

describe("markAllSelected", () => {
  it("sets isSelected true on every item", () => {
    const out = markAllSelected([item("1", "R1"), item("2", "R2")]);
    expect(out.every((i) => i.detail.isSelected)).toBe(true);
  });

  it("does not mutate the input items", () => {
    const input = [item("1", "R1", false)];
    markAllSelected(input);
    expect(input[0].detail.isSelected).toBe(false);
  });
});

describe("excludeByReferenceId", () => {
  it("removes items whose referenceId is in the set", () => {
    const out = excludeByReferenceId(
      [item("1", "R1"), item("2", "R2"), item("3", "R3")],
      new Set(["R2"]),
    );
    expect(out.map((i) => i.detail.referenceId)).toEqual(["R1", "R3"]);
  });

  it("returns the same array reference when the set is empty", () => {
    const input = [item("1", "R1")];
    expect(excludeByReferenceId(input, new Set())).toBe(input);
  });
});
