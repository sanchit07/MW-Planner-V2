import { describe, it, expect } from "vitest";

import { createAgencySchema } from "../agencies/agency.schema";

const t = (key: string) => key;

describe("createAgencySchema", () => {
  it("creates a schema with t function", () => {
    const schema = createAgencySchema(t);
    expect(schema).toBeDefined();
  });

  it("accepts valid agency data", () => {
    const schema = createAgencySchema(t);
    const result = schema.safeParse({
      name: "Test Agency",
      companyEmail: "agency@example.com",
    });
    expect(result.success).toBe(true);
  });

  it("accepts with optional domain", () => {
    const schema = createAgencySchema(t);
    const result = schema.safeParse({
      name: "Test Agency",
      companyEmail: "agency@example.com",
      domain: "example.com",
    });
    expect(result.success).toBe(true);
  });

  it("rejects empty name", () => {
    const schema = createAgencySchema(t);
    const result = schema.safeParse({
      name: "",
      companyEmail: "agency@example.com",
    });
    expect(result.success).toBe(false);
  });

  it("rejects invalid email", () => {
    const schema = createAgencySchema(t);
    const result = schema.safeParse({
      name: "Test Agency",
      companyEmail: "not-an-email",
    });
    expect(result.success).toBe(false);
  });

  it("rejects missing required fields", () => {
    const schema = createAgencySchema(t);
    const result = schema.safeParse({});
    expect(result.success).toBe(false);
  });

  it("creates different schemas with different t functions", () => {
    const schema1 = createAgencySchema((key) => `en:${key}`);
    const schema2 = createAgencySchema((key) => `ja:${key}`);
    // Both should create valid schemas that reject empty names
    expect(
      schema1.safeParse({ name: "", companyEmail: "test@example.com" }).success,
    ).toBe(false);
    expect(
      schema2.safeParse({ name: "", companyEmail: "test@example.com" }).success,
    ).toBe(false);
  });
});
