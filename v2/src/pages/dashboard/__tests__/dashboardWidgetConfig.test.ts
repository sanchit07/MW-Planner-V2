import { describe, it, expect } from "vitest";

import {
  WIDGET_CATEGORIES,
  STANDALONE_WIDGET_KEYS,
  getRevenueChildKeys,
  getBudgetChildKeys,
  type WidgetCategory,
} from "../dashboardWidgetConfig";

describe("dashboardWidgetConfig", () => {
  describe("WIDGET_CATEGORIES", () => {
    it("exports an array of widget categories", () => {
      expect(Array.isArray(WIDGET_CATEGORIES)).toBe(true);
      expect(WIDGET_CATEGORIES.length).toBeGreaterThan(0);
    });

    it("each category has key, labelKey, and children array", () => {
      WIDGET_CATEGORIES.forEach((cat: WidgetCategory) => {
        expect(cat).toHaveProperty("key", expect.any(String));
        expect(cat).toHaveProperty("labelKey", expect.any(String));
        expect(Array.isArray(cat.children)).toBe(true);
      });
    });

    it("includes revenue-performance and budget-tracker categories", () => {
      const keys = WIDGET_CATEGORIES.map((c) => c.key);
      expect(keys).toContain("revenue-performance");
      expect(keys).toContain("budget-tracker");
    });

    it("hides the inventory-utilization category (ghost toggles)", () => {
      const keys = WIDGET_CATEGORIES.map((c) => c.key);
      expect(keys).not.toContain("inventory-utilization");
    });
  });

  describe("STANDALONE_WIDGET_KEYS", () => {
    it("exports a non-empty readonly array", () => {
      expect(Array.isArray(STANDALONE_WIDGET_KEYS)).toBe(true);
      expect(STANDALONE_WIDGET_KEYS.length).toBeGreaterThan(0);
    });

    it("includes expected standalone widget keys", () => {
      expect(STANDALONE_WIDGET_KEYS).toContain("campaign-overview");
      expect(STANDALONE_WIDGET_KEYS).toContain("campaign-performance");
      expect(STANDALONE_WIDGET_KEYS).toContain("audience-reach-performance");
      // creative-status is hidden (ghost toggle, CreativeStatusTracker disabled)
      expect(STANDALONE_WIDGET_KEYS).not.toContain("creative-status");
    });
  });

  describe("getRevenueChildKeys", () => {
    it("returns an array of strings", () => {
      const keys = getRevenueChildKeys();
      expect(Array.isArray(keys)).toBe(true);
      keys.forEach((k) => expect(typeof k).toBe("string"));
    });

    it("returns revenue category children when category exists", () => {
      const keys = getRevenueChildKeys();
      expect(keys).toContain("sales-overview");
      expect(keys).toContain("sales-performance-summary");
      expect(keys).toContain("sales-pipeline-funnel");
      // revenue-distribution is hidden (ghost toggle, no widget wired)
      expect(keys).not.toContain("revenue-distribution");
    });

    it("returns a new array each time (no mutation)", () => {
      const a = getRevenueChildKeys();
      const b = getRevenueChildKeys();
      expect(a).toEqual(b);
      expect(a).not.toBe(b);
    });
  });

  describe("getBudgetChildKeys", () => {
    it("returns an array of strings", () => {
      const keys = getBudgetChildKeys();
      expect(Array.isArray(keys)).toBe(true);
      keys.forEach((k) => expect(typeof k).toBe("string"));
    });

    it("returns budget category children when category exists", () => {
      const keys = getBudgetChildKeys();
      expect(keys).toContain("budget-overview");
      expect(keys).toContain("budget-performance-summary");
    });

    it("returns a new array each time (no mutation)", () => {
      const a = getBudgetChildKeys();
      const b = getBudgetChildKeys();
      expect(a).toEqual(b);
      expect(a).not.toBe(b);
    });
  });
});
