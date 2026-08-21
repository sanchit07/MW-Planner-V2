import { z } from "zod";

const brandSchema = z.object({
  name: z
    .string()
    .min(1, "Brand name is required")
    .min(2, "Brand name must be at least 2 characters")
    .max(100, "Brand name must be less than 100 characters"),

  iabCategoryId: z.string().min(1, "Category is required"),

  iabSubcategoryId: z.string().optional().or(z.literal("")),

  description: z
    .string()
    .max(500, "Description must be less than 500 characters")
    .optional()
    .or(z.literal("")),

  website: z
    .string()
    .max(500, "Website URL must be less than 500 characters")
    .optional()
    .or(z.literal("")),
});

export type BrandFormData = z.infer<typeof brandSchema>;

export default brandSchema;
