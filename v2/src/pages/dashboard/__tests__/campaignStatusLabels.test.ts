import { describe, it, expect } from "vitest";

import dashboardEn from "../../../assets/i18n/dashboard/en.json";

// Feedback S.No 10 (1 July 2026): homepage status labels must read
// "Planning" (not "Planned") and "Approved" (not "Approve").
describe("dashboard campaignStatus labels", () => {
  it("uses 'Planning' for the planned status", () => {
    expect(dashboardEn.campaignStatus.planned).toBe("Planning");
  });

  it("uses 'Approved' for the approve status", () => {
    expect(dashboardEn.campaignStatus.approve).toBe("Approved");
  });
});
