import { z } from "zod";

// Agency form schema factory - accepts translation function for localized error messages
export const createAgencySchema = (t: (key: string) => string) =>
  z.object({
    name: z.string().min(1, t("agency_form.name_required")),
    companyEmail: z.string().email(t("agency_form.email_invalid")),
    domain: z.string().optional(),
  });

// Type inference for form data
export type AgencyFormData = z.infer<ReturnType<typeof createAgencySchema>>;
