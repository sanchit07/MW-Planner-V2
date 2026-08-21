import { describe, it, expect } from "vitest";

import { formatCurrencyWithLocale } from "../currency";

describe("currency", () => {
  describe("formatCurrencyWithLocale", () => {
    it("should format number with locale formatting and default 2 decimals", () => {
      const result = formatCurrencyWithLocale(1000.5, "USD");
      expect(result).toContain("USD");
      expect(result).toContain("1,000");
      expect(result).toContain("50");
    });

    it("should format integer with default 2 decimals", () => {
      const result = formatCurrencyWithLocale(1000, "USD");
      expect(result).toContain("USD");
      expect(result).toContain("1,000");
      expect(result).toContain("00");
    });

    it("should use custom decimal places when provided", () => {
      const result = formatCurrencyWithLocale(1000.123, "USD", 3);
      expect(result).toContain("USD");
      expect(result).toContain("1,000");
      expect(result).toContain("123");
    });

    it("should return '-' for undefined value", () => {
      expect(formatCurrencyWithLocale(undefined, "USD")).toBe("-");
    });

    it("should return '-' for null value", () => {
      expect(formatCurrencyWithLocale(null, "USD")).toBe("-");
    });

    it("should handle zero", () => {
      const result = formatCurrencyWithLocale(0, "USD");
      expect(result).toContain("USD");
      expect(result).toContain("0");
    });

    it("should handle negative numbers", () => {
      const result = formatCurrencyWithLocale(-1000, "USD");
      expect(result).toContain("USD");
      expect(result).toContain("1,000");
      expect(result).toContain("-");
    });

    it("should handle empty currency string with default", () => {
      const result = formatCurrencyWithLocale(1000, "");
      expect(result).toContain("1,000");
      expect(result).toContain("00");
    });

    it("should format with different currency codes", () => {
      const result = formatCurrencyWithLocale(1234567, "EUR");
      expect(result).toContain("EUR");
      const digitsOnly = result.replace(/\D/g, "");
      expect(digitsOnly).toContain("1234567");
    });
  });
});
