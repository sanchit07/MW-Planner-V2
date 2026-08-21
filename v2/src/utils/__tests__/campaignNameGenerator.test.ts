import { describe, it, expect } from "vitest";

import { generateCampaignPrefix } from "../campaignNameGenerator";

describe("campaignNameGenerator", () => {
  describe("generateCampaignPrefix", () => {
    it("should generate prefix with name and date", () => {
      const result = generateCampaignPrefix("TestCampaign");
      expect(result).toContain("TestCampaign");
      expect(result).toMatch(/TestCampaign_[A-Z][a-z]{2}_\d{2}_\d{2}_/);
    });

    it("should include month abbreviation", () => {
      const result = generateCampaignPrefix("Test");
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
      const hasMonth = months.some((month) => result.includes(month));
      expect(hasMonth).toBe(true);
    });

    it("should pad day with zero", () => {
      const result = generateCampaignPrefix("Test");
      const dayMatch = result.match(/_(\d{2})_/);
      expect(dayMatch).toBeTruthy();
      if (dayMatch) {
        expect(dayMatch[1].length).toBe(2);
      }
    });

    it("should use last 2 digits of year", () => {
      const result = generateCampaignPrefix("Test");
      const yearMatch = result.match(/_(\d{2})_$/);
      expect(yearMatch).toBeTruthy();
      if (yearMatch) {
        const year = parseInt(yearMatch[1]);
        expect(year).toBeGreaterThanOrEqual(0);
        expect(year).toBeLessThan(100);
      }
    });

    it("should handle empty name", () => {
      const result = generateCampaignPrefix("");
      expect(result).toMatch(/^_[A-Z][a-z]{2}_\d{2}_\d{2}_$/);
    });
  });
});
