import { z } from "zod";

// Date range schema
const dateRangeSchema = z
  .object({
    from: z.date().nullable(),
    to: z.date().nullable(),
  })
  .refine(
    (data) => {
      if (!data.to) {
        return false;
      }

      return true;
    },
    {
      message: "End date is required",
    },
  )
  .refine(
    (data) => {
      if (!data.from || !data.to) {
        return false;
      }

      return data.to >= data.from;
    },
    {
      message: "End date must be after start date",
    },
  );

// Main campaign schema
const campaignSchema = z
  .object({
    campaignName: z
      .string()
      .min(1, "Campaign name is required")
      .min(3, "Campaign name must be at least 3 characters")
      .max(100, "Campaign name must be less than 100 characters"),

    campaignDates: dateRangeSchema.refine(
      (data) => {
        return data.from !== null && data.to !== null;
      },
      {
        message: "Both start and end dates are required",
      },
    ),

    brand: z.string().optional(), // Optional field

    clientType: z.string().min(1, "Client type is required"),

    agency: z.string().optional(), // Optional, only required when clientType is "Agency"

    mediaChannels: z
      .array(z.string())
      .min(1, "At least one media channel is required")
      .default(["DIGITAL_OOH"]),

    dsp: z.string().optional(),
    seatId: z.string().optional(),

    // Additional fields for campaign creation
    description: z.string().optional(),
    status: z
      .enum(["DRAFT", "PLANNED", "REJECTED"])
      .default("DRAFT")
      .optional(),
    budget: z.number().optional(),
    currency: z.string().optional(),
  })
  .refine(
    (data) => {
      // If clientType is "Agency", then agency is required
      if (
        data.clientType === "Agency" &&
        (!data.agency || data.agency.trim() === "")
      ) {
        return false;
      }
      return true;
    },
    {
      message: "Agency is required when client type is Agency",
      path: ["agency"], // Point the error to the agency field
    },
  );

export default campaignSchema;
