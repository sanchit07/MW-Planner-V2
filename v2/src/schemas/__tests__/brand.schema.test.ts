import { describe, it, expect } from "vitest";

import brandSchema from "../brands/brand.schema";

describe("brandSchema", () => {
  it("accepts valid brand data", () => {
    const result = brandSchema.safeParse({
      name: "My Brand",
      iabCategoryId: "IAB1",
    });
    expect(result.success).toBe(true);
  });

  it("accepts with all optional fields", () => {
    const result = brandSchema.safeParse({
      name: "My Brand",
      iabCategoryId: "IAB1",
      iabSubcategoryId: "IAB1-1",
      description: "A brand description",
      website: "https://example.com",
    });
    expect(result.success).toBe(true);
  });

  it("accepts empty string for optional fields", () => {
    const result = brandSchema.safeParse({
      name: "My Brand",
      iabCategoryId: "IAB1",
      iabSubcategoryId: "",
      description: "",
      website: "",
    });
    expect(result.success).toBe(true);
  });

  it("rejects empty name", () => {
    const result = brandSchema.safeParse({ name: "", iabCategoryId: "IAB1" });
    expect(result.success).toBe(false);
  });

  it("rejects name shorter than 2 characters", () => {
    const result = brandSchema.safeParse({ name: "A", iabCategoryId: "IAB1" });
    expect(result.success).toBe(false);
  });

  it("rejects name longer than 100 characters", () => {
    const longName = "A".repeat(101);
    const result = brandSchema.safeParse({
      name: longName,
      iabCategoryId: "IAB1",
    });
    expect(result.success).toBe(false);
  });

  it("rejects empty iabCategoryId", () => {
    const result = brandSchema.safeParse({
      name: "My Brand",
      iabCategoryId: "",
    });
    expect(result.success).toBe(false);
  });

  it("rejects description over 500 chars", () => {
    const result = brandSchema.safeParse({
      name: "My Brand",
      iabCategoryId: "IAB1",
      description: "X".repeat(501),
    });
    expect(result.success).toBe(false);
  });
});
