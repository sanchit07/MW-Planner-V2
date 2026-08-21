import { z } from "zod";

import { GOAL_TYPE_CONFIG } from "../../constants/budget.constants";

// Goal types that require integer values (no decimals)
const INTEGER_ONLY_GOAL_TYPES = ["IMPRESSIONS", "REACH", "ADPLAYS"];

type TFunc = (key: string, params?: Record<string, string | number>) => string;

export const createBudgetSchema = (t: TFunc) =>
  z
    .object({
      country: z.string().min(1, "Country is required"),
      currency: z.string().min(1, "Currency is required"),
      budget: z
        .union([
          z
            .number()
            .min(500, { message: "Budget must be at least 500" })
            .max(999999999, "Budget must be at most 999999999")
            .refine(
              (val) => {
                const decimalPlaces = val.toString().split(".")[1]?.length || 0;
                return decimalPlaces <= 2;
              },
              { message: "Must have up to 2 decimal places" },
            ),
          z.undefined(),
        ])
        .optional(),
      goalType: z.string().optional(),
      targetName: z
        .string()
        .max(100, "Target name must be at most 100 characters")
        .optional(),
      targetValue: z
        .number()
        .max(999999999, "Target value must be at most 999999999")
        .optional(),
    })
    .refine(
      (data) => {
        if (data.goalType && data.goalType.trim().length > 0) {
          return data.targetValue !== undefined && data.targetValue !== null;
        }
        return true;
      },
      {
        message: t("budget_goal.budget_goal_setup.target_required"),
        path: ["targetValue"],
      },
    )
    .superRefine((data, ctx) => {
      if (
        !data.goalType ||
        data.targetValue === undefined ||
        data.targetValue === null
      ) {
        return;
      }

      const config = GOAL_TYPE_CONFIG[data.goalType.toUpperCase()];
      if (!config) return;

      const { min, max } = config;
      const goalTypeUpper = data.goalType.toUpperCase();

      if (
        INTEGER_ONLY_GOAL_TYPES.includes(goalTypeUpper) &&
        !Number.isInteger(data.targetValue)
      ) {
        ctx.addIssue({
          code: "custom",
          message: t("budget_goal.budget_goal_setup.whole_number_required"),
          path: ["targetValue"],
        });
        return;
      }

      const isMinInvalid = min !== undefined && data.targetValue < min;
      const isMaxInvalid = max !== undefined && data.targetValue > max;

      if (isMinInvalid || isMaxInvalid) {
        let message: string;
        if (min !== undefined && max !== undefined) {
          message = t("budget_goal.budget_goal_setup.target_out_of_range", {
            min: min.toLocaleString(),
            max: max.toLocaleString(),
          });
        } else if (min !== undefined) {
          message = t("budget_goal.budget_goal_setup.target_too_small", {
            min: min.toLocaleString(),
          });
        } else {
          message = t("budget_goal.budget_goal_setup.target_too_large", {
            max: max!.toLocaleString(),
          });
        }
        ctx.addIssue({ code: "custom", message, path: ["targetValue"] });
      }
    })
    .refine(
      (data) => {
        if (
          data.targetValue !== undefined &&
          data.targetValue !== null &&
          data.goalType
        ) {
          const goalTypeUpper = data.goalType.toUpperCase();
          if (INTEGER_ONLY_GOAL_TYPES.includes(goalTypeUpper)) return true;
          const decimalPlaces = (
            data.targetValue.toString().split(".")[1] || ""
          ).length;
          return decimalPlaces <= 2;
        }
        return true;
      },
      {
        message: t("budget_goal.budget_goal_setup.target_decimal_places"),
        path: ["targetValue"],
      },
    );

// Static instance used solely for type inference — structure is identical regardless of `t`
const _schemaForType = createBudgetSchema((key) => key);
export type BudgetFormData = z.infer<typeof _schemaForType>;

export default _schemaForType;
