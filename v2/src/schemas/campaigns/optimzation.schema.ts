import { z } from "zod";

// Budget allocation schema
const budgetAllocationSchema = z
  .object({
    digital: z.number().min(0),
    transit: z.number().min(0),
    classic: z.number().min(0),
    retail: z.number().min(0),
  })
  .strict()
  .refine(
    (data) => {
      const total =
        (data.digital || 0) +
        (data.transit || 0) +
        (data.classic || 0) +
        (data.retail || 0);
      return total >= 1 && total <= 100.01;
    },
    {
      message: "optimization.budgetAllocation.totalValidationMessage",
    },
  );

// Week distribution schema
const weekdayDistributionSchema = z
  .object({
    MONDAY: z.number().min(0),
    TUESDAY: z.number().min(0),
    WEDNESDAY: z.number().min(0),
    THURSDAY: z.number().min(0),
    FRIDAY: z.number().min(0),
    SATURDAY: z.number().min(0),
    SUNDAY: z.number().min(0),
  })
  .strict()
  .refine(
    (data) => {
      const total =
        (data.MONDAY || 0) +
        (data.TUESDAY || 0) +
        (data.WEDNESDAY || 0) +
        (data.THURSDAY || 0) +
        (data.FRIDAY || 0) +
        (data.SATURDAY || 0) +
        (data.SUNDAY || 0);
      return total >= 1 && total <= 100.01;
    },
    {
      message:
        "optimization.schedulingTargeting.weekdayDistribution.totalValidationMessage",
    },
  );

// Daypart distribution schema
const daypartDistributionSchema = z
  .object({
    "06-10": z.number().min(0),
    "10-14": z.number().min(0),
    "14-18": z.number().min(0),
    "18-22": z.number().min(0),
    "22-06": z.number().min(0),
  })
  .strict()
  .refine(
    (data) => {
      const total =
        (data["06-10"] || 0) +
        (data["10-14"] || 0) +
        (data["14-18"] || 0) +
        (data["18-22"] || 0) +
        (data["22-06"] || 0);
      return total >= 1 && total <= 100.01;
    },
    {
      message:
        "optimization.schedulingTargeting.daypartDistribution.totalValidationMessage",
    },
  );

// Schedule allocation schema
const scheduleAllocationSchema = z
  .object({
    weekdayDistribution: weekdayDistributionSchema,
    daypartDistribution: daypartDistributionSchema,
  })
  .strict();

// Main targeting schema
const optimizationSchema = z
  .object({
    budgetAllocation: budgetAllocationSchema,
    scheduleTargeting: scheduleAllocationSchema,
  })
  .strict();

export default optimizationSchema;
export type OptimizationFormData = z.infer<typeof optimizationSchema>;
