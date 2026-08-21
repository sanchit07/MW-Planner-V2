import { describe, it, expect } from "vitest";

import optimizationSchema from "../campaigns/optimzation.schema";

const validBudgetAllocation = {
  digital: 40,
  transit: 20,
  classic: 30,
  retail: 10,
};
const validWeekday = {
  MONDAY: 15,
  TUESDAY: 15,
  WEDNESDAY: 15,
  THURSDAY: 15,
  FRIDAY: 15,
  SATURDAY: 12,
  SUNDAY: 13,
};
const validDaypart = {
  "06-10": 20,
  "10-14": 20,
  "14-18": 20,
  "18-22": 20,
  "22-06": 20,
};

const validData = {
  budgetAllocation: validBudgetAllocation,
  scheduleTargeting: {
    weekdayDistribution: validWeekday,
    daypartDistribution: validDaypart,
  },
};

describe("optimizationSchema", () => {
  describe("budgetAllocation refine", () => {
    it("accepts valid budget allocation totalling 100", () => {
      const result = optimizationSchema.safeParse(validData);
      expect(result.success).toBe(true);
    });

    it("rejects budget allocation where total < 1", () => {
      const data = {
        ...validData,
        budgetAllocation: { digital: 0, transit: 0, classic: 0, retail: 0 },
      };
      const result = optimizationSchema.safeParse(data);
      expect(result.success).toBe(false);
    });

    it("rejects budget allocation where total > 100.01", () => {
      const data = {
        ...validData,
        budgetAllocation: { digital: 40, transit: 30, classic: 30, retail: 5 },
      };
      const result = optimizationSchema.safeParse(data);
      expect(result.success).toBe(false);
    });
  });

  describe("weekdayDistribution refine", () => {
    it("accepts valid weekday distribution totalling 100", () => {
      const result = optimizationSchema.safeParse(validData);
      expect(result.success).toBe(true);
    });

    it("rejects weekday distribution where total is 0", () => {
      const data = {
        ...validData,
        scheduleTargeting: {
          ...validData.scheduleTargeting,
          weekdayDistribution: {
            MONDAY: 0,
            TUESDAY: 0,
            WEDNESDAY: 0,
            THURSDAY: 0,
            FRIDAY: 0,
            SATURDAY: 0,
            SUNDAY: 0,
          },
        },
      };
      const result = optimizationSchema.safeParse(data);
      expect(result.success).toBe(false);
    });
  });

  describe("daypartDistribution refine", () => {
    it("accepts valid daypart distribution totalling 100", () => {
      const result = optimizationSchema.safeParse(validData);
      expect(result.success).toBe(true);
    });

    it("rejects daypart distribution where total is 0", () => {
      const data = {
        ...validData,
        scheduleTargeting: {
          ...validData.scheduleTargeting,
          daypartDistribution: {
            "06-10": 0,
            "10-14": 0,
            "14-18": 0,
            "18-22": 0,
            "22-06": 0,
          },
        },
      };
      const result = optimizationSchema.safeParse(data);
      expect(result.success).toBe(false);
    });
  });
});
