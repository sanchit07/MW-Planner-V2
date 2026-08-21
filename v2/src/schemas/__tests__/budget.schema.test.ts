import { describe, it, expect, vi } from "vitest";

import budgetSchema from "../campaigns/budget.schema";

vi.mock("@tolgee/react", () => ({
  useTranslate: () => ({ t: (key: string) => key }),
  useTolgee: () => ({ getLanguage: () => "en" }),
  TolgeeProvider: ({ children }: { children: React.ReactNode }) => children,
}));

function createBudgetData(
  overrides: Partial<{
    country: string;
    currency: string;
    budget: number | undefined;
    goalType: string;
    targetName: string;
    targetValue: number;
  }> = {},
) {
  return {
    country: "Singapore",
    currency: "SGD",
    budget: undefined,
    goalType: "",
    targetName: undefined,
    targetValue: undefined,
    ...overrides,
  };
}

function parseBudget(
  data: ReturnType<typeof createBudgetData>,
): ReturnType<typeof budgetSchema.safeParse> {
  return budgetSchema.safeParse(data);
}

describe("budget.schema - goalType validation", () => {
  describe("goal type and target value validation", () => {
    it("should accept budget without goal type", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "", targetValue: undefined }),
      );
      expect(result.success).toBe(true);
    });

    it("should require target value when goal type is selected", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "IMPRESSIONS", targetValue: undefined }),
      );
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "budget_goal.budget_goal_setup.target_required",
        );
      }
    });

    it("should accept goal type with target value", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "IMPRESSIONS", targetValue: 100000 }),
      );
      expect(result.success).toBe(true);
    });
  });

  describe("REACH goal type validation", () => {
    it("should accept valid REACH target value", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "REACH", targetValue: 50000 }),
      );
      expect(result.success).toBe(true);
    });

    it("should reject REACH with decimal value", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "REACH", targetValue: 50000.5 }),
      );
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "budget_goal.budget_goal_setup.whole_number_required",
        );
      }
    });

    it("should reject REACH below minimum (1000)", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "REACH", targetValue: 999 }),
      );
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toContain("target_too_small");
      }
    });

    it("should accept REACH at minimum (1000)", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "REACH", targetValue: 1000 }),
      );
      expect(result.success).toBe(true);
    });

    it("should accept REACH with case-insensitive goal type", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "reach", targetValue: 50000 }),
      );
      expect(result.success).toBe(true);
    });
  });

  describe("IMPRESSIONS goal type validation", () => {
    it("should accept valid IMPRESSIONS target value", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "IMPRESSIONS", targetValue: 100000 }),
      );
      expect(result.success).toBe(true);
    });

    it("should reject IMPRESSIONS with decimal value", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "IMPRESSIONS", targetValue: 100000.25 }),
      );
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "budget_goal.budget_goal_setup.whole_number_required",
        );
      }
    });

    it("should reject IMPRESSIONS below minimum (1000)", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "IMPRESSIONS", targetValue: 500 }),
      );
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toContain("target_too_small");
      }
    });

    it("should accept IMPRESSIONS at minimum (1000)", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "IMPRESSIONS", targetValue: 1000 }),
      );
      expect(result.success).toBe(true);
    });
  });

  describe("ADPLAYS goal type validation", () => {
    it("should accept valid ADPLAYS target value", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "ADPLAYS", targetValue: 50000 }),
      );
      expect(result.success).toBe(true);
    });

    it("should reject ADPLAYS with decimal value", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "ADPLAYS", targetValue: 50000.75 }),
      );
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "budget_goal.budget_goal_setup.whole_number_required",
        );
      }
    });

    it("should reject ADPLAYS below minimum (100)", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "ADPLAYS", targetValue: 99 }),
      );
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toContain("target_out_of_range");
      }
    });

    it("should accept ADPLAYS at minimum (100)", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "ADPLAYS", targetValue: 100 }),
      );
      expect(result.success).toBe(true);
    });

    it("should reject ADPLAYS above maximum (1,000,000)", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "ADPLAYS", targetValue: 1000001 }),
      );
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toContain("target_out_of_range");
      }
    });

    it("should accept ADPLAYS at maximum (1,000,000)", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "ADPLAYS", targetValue: 1000000 }),
      );
      expect(result.success).toBe(true);
    });

    it("should accept ADPLAYS with case-insensitive goal type", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "adplays", targetValue: 50000 }),
      );
      expect(result.success).toBe(true);
    });
  });

  describe("SOV goal type validation", () => {
    it("should accept valid SOV percentage", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "SOV", targetValue: 45 }),
      );
      expect(result.success).toBe(true);
    });

    it("should accept SOV with decimal value", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "SOV", targetValue: 45.5 }),
      );
      expect(result.success).toBe(true);
    });

    it("should accept SOV with 2 decimal places", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "SOV", targetValue: 45.25 }),
      );
      expect(result.success).toBe(true);
    });

    it("should reject SOV with more than 2 decimal places", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "SOV", targetValue: 45.255 }),
      );
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "budget_goal.budget_goal_setup.target_decimal_places",
        );
      }
    });

    it("should reject SOV below minimum (0)", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "SOV", targetValue: -1 }),
      );
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toContain("target_out_of_range");
      }
    });

    it("should accept SOV at minimum (0)", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "SOV", targetValue: 0 }),
      );
      expect(result.success).toBe(true);
    });

    it("should reject SOV above maximum (100)", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "SOV", targetValue: 100.01 }),
      );
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toContain("target_out_of_range");
      }
    });

    it("should accept SOV at maximum (100)", () => {
      const result = parseBudget(
        createBudgetData({ goalType: "SOV", targetValue: 100 }),
      );
      expect(result.success).toBe(true);
    });
  });

  describe("target value edge cases", () => {
    it("should reject target value exceeding global maximum", () => {
      const result = parseBudget(
        createBudgetData({
          goalType: "IMPRESSIONS",
          targetValue: 1000000000,
        }),
      );
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "Target value must be at most 999999999",
        );
      }
    });

    it("should accept target value at global maximum", () => {
      const result = parseBudget(
        createBudgetData({
          goalType: "IMPRESSIONS",
          targetValue: 999999999,
        }),
      );
      expect(result.success).toBe(true);
    });
  });

  describe("targetName validation", () => {
    it("should accept targetName within limit", () => {
      const result = parseBudget(
        createBudgetData({
          goalType: "OTHER",
          targetValue: 1000,
          targetName: "Custom Target",
        }),
      );
      expect(result.success).toBe(true);
    });

    it("should reject targetName exceeding 100 characters", () => {
      const result = parseBudget(
        createBudgetData({
          goalType: "OTHER",
          targetValue: 1000,
          targetName: "A".repeat(101),
        }),
      );
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "Target name must be at most 100 characters",
        );
      }
    });

    it("should accept targetName at 100 characters", () => {
      const result = parseBudget(
        createBudgetData({
          goalType: "OTHER",
          targetValue: 1000,
          targetName: "A".repeat(100),
        }),
      );
      expect(result.success).toBe(true);
    });
  });

  describe("budget field validation", () => {
    it("should accept undefined budget (optional field)", () => {
      const result = parseBudget(createBudgetData({ budget: undefined }));
      expect(result.success).toBe(true);
    });

    it("should accept valid budget amount", () => {
      const result = parseBudget(createBudgetData({ budget: 10000 }));
      expect(result.success).toBe(true);
    });

    it("should accept budget with 2 decimal places", () => {
      const result = parseBudget(createBudgetData({ budget: 10000.99 }));
      expect(result.success).toBe(true);
    });

    it("should accept budget with 1 decimal place", () => {
      const result = parseBudget(createBudgetData({ budget: 10000.5 }));
      expect(result.success).toBe(true);
    });

    it("should accept budget at minimum (500)", () => {
      const result = parseBudget(createBudgetData({ budget: 500 }));
      expect(result.success).toBe(true);
    });

    it("should reject budget below minimum (500)", () => {
      const result = parseBudget(createBudgetData({ budget: 499 }));
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "Budget must be at least 500",
        );
      }
    });

    it("should reject zero budget", () => {
      const result = parseBudget(createBudgetData({ budget: 0 }));
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "Budget must be at least 500",
        );
      }
    });

    it("should reject negative budget", () => {
      const result = parseBudget(createBudgetData({ budget: -100 }));
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "Budget must be at least 500",
        );
      }
    });

    it("should reject budget with more than 2 decimal places", () => {
      const result = parseBudget(createBudgetData({ budget: 10000.999 }));
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "Must have up to 2 decimal places",
        );
      }
    });

    it("should accept budget at maximum (999999999)", () => {
      const result = parseBudget(createBudgetData({ budget: 999999999 }));
      expect(result.success).toBe(true);
    });

    it("should reject budget above maximum", () => {
      const result = parseBudget(createBudgetData({ budget: 1000000000 }));
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "Budget must be at most 999999999",
        );
      }
    });
  });
});
