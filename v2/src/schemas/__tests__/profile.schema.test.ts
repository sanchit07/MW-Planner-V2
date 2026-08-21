import { describe, it, expect } from "vitest";

import {
  changePasswordSchema,
  profileFormSchema,
} from "../profile/profile.schema";

describe("changePasswordSchema", () => {
  const valid = { newPassword: "Secure1Pass", confirmPassword: "Secure1Pass" };

  it("passes with valid matching passwords", () => {
    expect(changePasswordSchema.safeParse(valid).success).toBe(true);
  });

  it("fails when password is shorter than 8 characters", () => {
    const result = changePasswordSchema.safeParse({
      ...valid,
      newPassword: "Ab1",
      confirmPassword: "Ab1",
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      const messages = result.error.issues.map((e) => e.message);
      expect(messages).toContain("Password must be at least 8 characters");
    }
  });

  it("fails when password has no uppercase letter", () => {
    const result = changePasswordSchema.safeParse({
      newPassword: "secure1pass",
      confirmPassword: "secure1pass",
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      const messages = result.error.issues.map((e) => e.message);
      expect(messages).toContain(
        "Password must contain at least one uppercase letter",
      );
    }
  });

  it("fails when password has no lowercase letter", () => {
    const result = changePasswordSchema.safeParse({
      newPassword: "SECURE1PASS",
      confirmPassword: "SECURE1PASS",
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      const messages = result.error.issues.map((e) => e.message);
      expect(messages).toContain(
        "Password must contain at least one lowercase letter",
      );
    }
  });

  it("fails when password has no number", () => {
    const result = changePasswordSchema.safeParse({
      newPassword: "SecurePass",
      confirmPassword: "SecurePass",
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      const messages = result.error.issues.map((e) => e.message);
      expect(messages).toContain("Password must contain at least one number");
    }
  });

  it("fails when passwords do not match", () => {
    const result = changePasswordSchema.safeParse({
      newPassword: "Secure1Pass",
      confirmPassword: "Different1Pass",
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      const messages = result.error.issues.map((e) => e.message);
      expect(messages).toContain("Passwords do not match");
    }
  });

  it("fails when confirmPassword is empty", () => {
    const result = changePasswordSchema.safeParse({
      newPassword: "Secure1Pass",
      confirmPassword: "",
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      const messages = result.error.issues.map((e) => e.message);
      expect(messages).toContain("Please confirm your password");
    }
  });

  it("reports confirmPassword path for mismatch error", () => {
    const result = changePasswordSchema.safeParse({
      newPassword: "Secure1Pass",
      confirmPassword: "WrongPass1",
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      const paths = result.error.issues.map((e) => e.path.join("."));
      expect(paths).toContain("confirmPassword");
    }
  });
});

describe("profileFormSchema", () => {
  const valid = {
    fullName: "John Doe",
    email: "john@example.com",
    phone: "+1234567890",
  };

  it("passes with valid data", () => {
    expect(profileFormSchema.safeParse(valid).success).toBe(true);
  });

  it("passes without phone (optional)", () => {
    const { phone: _p, ...noPhone } = valid;
    expect(profileFormSchema.safeParse(noPhone).success).toBe(true);
  });

  it("fails when fullName is empty", () => {
    const result = profileFormSchema.safeParse({ ...valid, fullName: "" });
    expect(result.success).toBe(false);
    if (!result.success) {
      const messages = result.error.issues.map((e) => e.message);
      expect(messages).toContain("Full name is required");
    }
  });

  it("fails when fullName has more than 2 words", () => {
    const result = profileFormSchema.safeParse({
      ...valid,
      fullName: "John Michael Doe",
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      const messages = result.error.issues.map((e) => e.message);
      expect(messages).toContain("Full name cannot have more than 2 words");
    }
  });

  it("passes with single-word fullName", () => {
    expect(
      profileFormSchema.safeParse({ ...valid, fullName: "John" }).success,
    ).toBe(true);
  });

  it("fails with invalid email", () => {
    const result = profileFormSchema.safeParse({
      ...valid,
      email: "not-an-email",
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      const messages = result.error.issues.map((e) => e.message);
      expect(messages).toContain("Invalid email address");
    }
  });

  it("passes phone as undefined", () => {
    expect(
      profileFormSchema.safeParse({ ...valid, phone: undefined }).success,
    ).toBe(true);
  });
});
