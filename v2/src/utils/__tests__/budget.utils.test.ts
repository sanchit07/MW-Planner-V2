import { describe, it, expect } from "vitest";

import { CountryMarketDetails } from "../../types/campaign.types";
import {
  formatNumber,
  formatNumberInput,
  parseNumberInput,
  transformCountriesData,
  createGoalTypes,
} from "../budget.utils";

describe("budget.utils", () => {
  describe("formatNumberInput", () => {
    it("adds thousand separators to whole numbers", () => {
      expect(formatNumberInput(21633)).toBe("21,633");
      expect(formatNumberInput(1000000)).toBe("1,000,000");
      expect(formatNumberInput(500)).toBe("500");
    });

    it("groups the integer part but preserves decimals", () => {
      expect(formatNumberInput(21633.6)).toBe("21,633.6");
      expect(formatNumberInput(1234.56)).toBe("1,234.56");
    });

    it("formats a raw numeric string the user is typing", () => {
      expect(formatNumberInput("21633")).toBe("21,633");
      expect(formatNumberInput("1000000")).toBe("1,000,000");
    });

    it("keeps a trailing decimal point so the user can keep typing", () => {
      expect(formatNumberInput("1000.")).toBe("1,000.");
    });

    it("returns an empty string for empty / undefined input", () => {
      expect(formatNumberInput("")).toBe("");
      expect(formatNumberInput(undefined)).toBe("");
      expect(formatNumberInput(null)).toBe("");
    });
  });

  describe("parseNumberInput", () => {
    it("strips thousand separators and returns a number", () => {
      expect(parseNumberInput("21,633")).toBe(21633);
      expect(parseNumberInput("1,000,000")).toBe(1000000);
    });

    it("parses decimals", () => {
      expect(parseNumberInput("21,633.6")).toBe(21633.6);
      expect(parseNumberInput("1,234.56")).toBe(1234.56);
    });

    it("returns undefined for empty or non-numeric input", () => {
      expect(parseNumberInput("")).toBeUndefined();
      expect(parseNumberInput("abc")).toBeUndefined();
    });

    it("round-trips with formatNumberInput", () => {
      const parsed = parseNumberInput("1,250,000");
      expect(parsed).toBe(1250000);
      expect(formatNumberInput(parsed)).toBe("1,250,000");
    });
  });

  describe("formatNumber", () => {
    it("should format billions", () => {
      expect(formatNumber(1_500_000_000)).toBe("1.50B");
      expect(formatNumber(2_000_000_000)).toBe("2.00B");
    });

    it("should format millions", () => {
      expect(formatNumber(1_500_000)).toBe("1.50M");
      expect(formatNumber(2_000_000)).toBe("2.00M");
    });

    it("should format thousands", () => {
      expect(formatNumber(1_500)).toBe("1.50K");
      expect(formatNumber(2_000)).toBe("2.00K");
    });

    it("should return 0 for zero", () => {
      expect(formatNumber(0)).toBe("0");
    });

    it("should use locale string for numbers less than 1000", () => {
      const result = formatNumber(500);
      expect(result).toBeTruthy();
    });

    it("should round billions when rounding is true", () => {
      expect(formatNumber(1_500_000_000, true)).toBe("2B");
      expect(formatNumber(1_400_000_000, true)).toBe("1B");
    });

    it("should round millions when rounding is true", () => {
      expect(formatNumber(1_500_000, true)).toBe("2M");
      expect(formatNumber(1_400_000, true)).toBe("1M");
    });

    it("should round thousands when rounding is true", () => {
      expect(formatNumber(1_500, true)).toBe("2K");
      expect(formatNumber(1_400, true)).toBe("1K");
    });

    it("should round numbers less than 1000 when rounding is true", () => {
      expect(formatNumber(500, true)).toBe("500");
      expect(formatNumber(234, true)).toBe("234");
    });

    it("should use toFixed(2) format when rounding is false", () => {
      expect(formatNumber(1_234_567, false)).toBe("1.23M");
      expect(formatNumber(9_876, false)).toBe("9.88K");
    });
  });

  describe("transformCountriesData", () => {
    it("should transform countries data correctly", () => {
      const response = {
        data: [
          {
            id: "1",
            countryId: "US",
            countryName: "United States",
            population: 330000000,
            impressions: 1000000,
            inventoryCount: 5000,
          } as CountryMarketDetails,
        ],
      };

      const result = transformCountriesData(response);

      expect(result).toHaveLength(1);
      expect(result[0].id).toBe("1");
      expect(result[0].countryId).toBe("US");
      expect(result[0].countryName).toBe("United States");
      expect(result[0].population).toBe(330000000);
    });

    it("should return empty array when data is missing", () => {
      expect(transformCountriesData({})).toEqual([]);
      expect(transformCountriesData(null)).toEqual([]);
      expect(transformCountriesData(undefined)).toEqual([]);
    });

    it("should handle multiple countries and return them sorted A-Z by countryName", () => {
      const response = {
        data: [
          {
            id: "1",
            countryId: "US",
            countryName: "United States",
            population: 330000000,
            impressions: 1000000,
            inventoryCount: 5000,
          } as CountryMarketDetails,
          {
            id: "2",
            countryId: "UK",
            countryName: "United Kingdom",
            population: 67000000,
            impressions: 500000,
            inventoryCount: 2000,
          } as CountryMarketDetails,
        ],
      };

      const result = transformCountriesData(response);
      expect(result).toHaveLength(2);
      expect(result[0].countryName).toBe("United Kingdom");
      expect(result[1].countryName).toBe("United States");
    });
  });

  describe("createGoalTypes", () => {
    it("should create goal types with translation function", () => {
      const t = (key: string) => key;
      const result = createGoalTypes(t);

      expect(Array.isArray(result)).toBe(true);
      expect(result.length).toBeGreaterThan(0);
    });

    it("should include IMPRESSIONS goal type", () => {
      const t = (key: string) => key;
      const result = createGoalTypes(t);

      const impressions = result.find((g) => g.value === "IMPRESSIONS");
      expect(impressions).toBeDefined();
    });

    it("should include REACH goal type", () => {
      const t = (key: string) => key;
      const result = createGoalTypes(t);

      const reach = result.find((g) => g.value === "REACH");
      expect(reach).toBeDefined();
    });

    it("should include SOV goal type with max 100", () => {
      const t = (key: string) => key;
      const result = createGoalTypes(t);

      const sov = result.find((g) => g.value === "SOV");
      expect(sov).toBeDefined();
      expect(sov?.max).toBe(100);
    });
  });
});
