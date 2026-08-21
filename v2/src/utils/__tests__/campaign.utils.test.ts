import { describe, it, expect } from "vitest";

import { CampaignListItem } from "../../types/campaign.types";
import {
  formatCurrency,
  normalizeCampaignId,
  normalizeStatus,
  transformCampaignData,
} from "../campaign.utils";

describe("campaign.utils", () => {
  describe("formatCurrency", () => {
    it("should format currency with amount", () => {
      expect(formatCurrency(1000, "USD")).toContain("USD");
      expect(formatCurrency(1000, "USD")).toContain("1,000");
      // Note: Locale formatting may vary (e.g., Indian: 12,34,567 vs US: 1,234,567)
      // So we check for currency and that it contains the digits
      const result = formatCurrency(1234567, "EUR");
      expect(result).toContain("EUR");
      // Remove all non-digit characters and check for the number
      const digitsOnly = result.replace(/\D/g, "");
      expect(digitsOnly).toContain("1234567");
    });

    it("should use default currency when not provided", () => {
      const result = formatCurrency(1000);
      expect(result).toContain("1,000");
    });

    it("should return fallback for null", () => {
      expect(formatCurrency(null)).toBe("N/A");
    });

    it("should return fallback for undefined", () => {
      expect(formatCurrency(undefined)).toBe("N/A");
    });
  });

  describe("normalizeCampaignId", () => {
    it("returns string as-is", () => {
      expect(normalizeCampaignId("camp-123")).toBe("camp-123");
    });

    it("converts number to string", () => {
      expect(normalizeCampaignId(42)).toBe("42");
    });
  });

  describe("normalizeStatus", () => {
    it("should normalize 'approved' to 'active'", () => {
      expect(normalizeStatus("approved")).toBe("active");
      expect(normalizeStatus("APPROVED")).toBe("active");
    });

    it("should normalize 'planned' to 'paused'", () => {
      expect(normalizeStatus("planned")).toBe("paused");
      expect(normalizeStatus("PLANNED")).toBe("paused");
    });

    it("should return 'draft' for other statuses", () => {
      expect(normalizeStatus("draft")).toBe("draft");
      expect(normalizeStatus("unknown")).toBe("draft");
    });
  });

  describe("transformCampaignData", () => {
    it("should transform campaign data correctly", () => {
      const apiCampaign: CampaignListItem = {
        id: "123",
        name: "Test Campaign",
        userName: "Test User",
        brand: { id: "brand-1", name: "Test Brand" },
        categoryName: "Test Category",
        goals: {
          goalType: "Awareness",
          targetName: "Target",
        },
        budget: {
          totalBudget: 10000,
          currency: "USD",
        },
        startDate: "2024-01-01",
        endDate: "2024-01-31",
        status: "approved",
      } as unknown as CampaignListItem;

      const result = transformCampaignData(apiCampaign);

      expect(result.id).toBe("123");
      expect(result.campaignName).toBe("Test Campaign");
      expect(result.userName).toBe("Test User");
      expect(result.brand).toBe("Test Brand");
    });

    it("should use fallback values for missing data", () => {
      const apiCampaign: CampaignListItem = {
        id: "123",
        name: "Test Campaign",
        status: "DRAFT", // Add status to avoid normalizeStatus error
      } as unknown as CampaignListItem;

      const result = transformCampaignData(apiCampaign);

      expect(result.userName).toBe("N/A");
      expect(result.brand).toBe("N/A");
    });

    it("maps rawBudget from apiCampaign.budget", () => {
      const apiCampaign = {
        id: "123",
        name: "Test",
        status: "DRAFT",
        budget: 1000,
      } as unknown as CampaignListItem;

      const result = transformCampaignData(apiCampaign);

      expect(result.rawBudget).toBe(1000);
    });

    it("maps rawTotalCost from apiCampaign.totalCost", () => {
      const apiCampaign = {
        id: "123",
        name: "Test",
        status: "DRAFT",
        totalCost: 2500,
      } as unknown as CampaignListItem;

      const result = transformCampaignData(apiCampaign);

      expect(result.rawTotalCost).toBe(2500);
    });

    it("defaults rawBudget to 0 when budget is undefined", () => {
      const apiCampaign = {
        id: "123",
        name: "Test",
        status: "DRAFT",
      } as unknown as CampaignListItem;

      const result = transformCampaignData(apiCampaign);

      expect(result.rawBudget).toBe(0);
    });

    it("defaults rawTotalCost to 0 when totalCost is undefined", () => {
      const apiCampaign = {
        id: "123",
        name: "Test",
        status: "DRAFT",
      } as unknown as CampaignListItem;

      const result = transformCampaignData(apiCampaign);

      expect(result.rawTotalCost).toBe(0);
    });
  });
});
