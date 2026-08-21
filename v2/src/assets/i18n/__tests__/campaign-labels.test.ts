import { describe, expect, it } from "vitest";

import en from "../campaigns/en.json";

describe("campaigns en.json labels", () => {
  it("uses the singular 'Inventory Information' heading (SI 79)", () => {
    expect(en.inventoryDetails.inventoryInformations).toBe(
      "Inventory Information",
    );
  });

  it("uses grammatical no-schedule empty text (SI 80)", () => {
    expect(en.optimization.schedulingTargeting.noScheduleText).toBe(
      "Select an inventory to view the schedule details.",
    );
  });
});
