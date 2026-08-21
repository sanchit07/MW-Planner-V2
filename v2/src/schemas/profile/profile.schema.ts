import { z } from "zod";

export const changePasswordSchema = z
  .object({
    newPassword: z
      .string()
      .min(8, "Password must be at least 8 characters")
      .refine(
        (val) => /[A-Z]/.test(val),
        "Password must contain at least one uppercase letter",
      )
      .refine(
        (val) => /[a-z]/.test(val),
        "Password must contain at least one lowercase letter",
      )
      .refine(
        (val) => /[0-9]/.test(val),
        "Password must contain at least one number",
      ),
    confirmPassword: z.string().min(1, "Please confirm your password"),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

export type ChangePasswordValues = z.infer<typeof changePasswordSchema>;

export const profileFormSchema = z.object({
  fullName: z
    .string()
    .min(1, "Full name is required")
    .refine(
      (val) => val.trim().split(/\s+/).length <= 2,
      "Full name cannot have more than 2 words",
    ),
  email: z.string().email("Invalid email address"),
  phone: z.string().optional(),
});

export type ProfileFormValues = z.infer<typeof profileFormSchema>;
