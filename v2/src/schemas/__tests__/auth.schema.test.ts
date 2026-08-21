import { describe, it, expect } from "vitest";

import loginSchema from "../auth/auth.schema";

describe("loginSchema", () => {
  it("accepts valid credentials", () => {
    const result = loginSchema.safeParse({
      username: "user@example.com",
      password: "pass123",
    });
    expect(result.success).toBe(true);
  });

  it("rejects empty username", () => {
    const result = loginSchema.safeParse({ username: "", password: "pass123" });
    expect(result.success).toBe(false);
  });

  it("rejects empty password", () => {
    const result = loginSchema.safeParse({
      username: "user@example.com",
      password: "",
    });
    expect(result.success).toBe(false);
  });

  it("rejects missing both fields", () => {
    const result = loginSchema.safeParse({});
    expect(result.success).toBe(false);
  });
});
