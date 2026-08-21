import { describe, it, expect } from "vitest";

import { cn } from "../tailwindMerge";

describe("tailwindMerge", () => {
  describe("cn", () => {
    it("should join class names", () => {
      expect(cn("class1", "class2", "class3")).toBe("class1 class2 class3");
    });

    it("should filter out falsy values", () => {
      expect(cn("class1", null, undefined, false, "class2")).toBe(
        "class1 class2",
      );
    });

    it("should handle empty array", () => {
      expect(cn()).toBe("");
    });

    it("should handle only falsy values", () => {
      expect(cn(null, undefined, false)).toBe("");
    });

    it("should handle mixed truthy and falsy values", () => {
      expect(cn("class1", null, "class2", undefined, "class3", false)).toBe(
        "class1 class2 class3",
      );
    });
  });
});
