import { describe, it, expect } from "vitest";

import {
  capitalizeFirst,
  toKebabKey,
  toPascalCase,
} from "../stringManipulation.utils";

describe("toKebabKey", () => {
  it("converts spaces to hyphens and lowercases", () => {
    expect(toKebabKey("Hello World")).toBe("hello-world");
  });

  it("replaces commas and spaces with a single hyphen", () => {
    expect(toKebabKey("One, Two, Three")).toBe("one-two-three");
  });

  it("strips dots entirely", () => {
    expect(toKebabKey("file.name.txt")).toBe("filenametxt");
  });

  it("handles consecutive spaces", () => {
    expect(toKebabKey("a  b")).toBe("a-b");
  });

  it("returns lowercase for already-kebab input", () => {
    expect(toKebabKey("already-lower")).toBe("already-lower");
  });

  it("handles empty string", () => {
    expect(toKebabKey("")).toBe("");
  });
});

describe("toPascalCase", () => {
  it("capitalizes each word", () => {
    expect(toPascalCase("hello world")).toBe("HelloWorld");
  });

  it("handles hyphen-separated words", () => {
    expect(toPascalCase("foo-bar-baz")).toBe("FooBarBaz");
  });

  it("handles mixed separators", () => {
    expect(toPascalCase("one  two--three")).toBe("OneTwoThree");
  });

  it("lowercases letters within each word", () => {
    expect(toPascalCase("HELLO WORLD")).toBe("HelloWorld");
  });

  it("trims leading and trailing whitespace", () => {
    expect(toPascalCase("  trim  ")).toBe("Trim");
  });

  it("handles empty string", () => {
    expect(toPascalCase("")).toBe("");
  });
});

describe("capitalizeFirst", () => {
  it("uppercases the first character", () => {
    expect(capitalizeFirst("loop")).toBe("Loop");
  });

  it("leaves the rest of the string untouched", () => {
    expect(capitalizeFirst("spot basis")).toBe("Spot basis");
  });

  it("does not lowercase remaining uppercase letters", () => {
    expect(capitalizeFirst("iPhone")).toBe("IPhone");
  });

  it("returns empty string for empty / nullish input", () => {
    expect(capitalizeFirst("")).toBe("");
    expect(capitalizeFirst(undefined)).toBe("");
    expect(capitalizeFirst(null)).toBe("");
  });
});
