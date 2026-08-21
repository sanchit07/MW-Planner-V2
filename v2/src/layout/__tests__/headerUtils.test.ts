import { describe, it, expect } from "vitest";

import { getUserInitials } from "../headerUtils";

describe("headerUtils", () => {
  describe("getUserInitials", () => {
    it("returns first letter of first and last name uppercased", () => {
      expect(getUserInitials("Jane", "Doe")).toBe("JD");
    });

    it("returns G when both names are empty", () => {
      expect(getUserInitials("", "")).toBe("G");
    });

    it("returns G when first name is undefined", () => {
      expect(getUserInitials(undefined, "Doe")).toBe("G");
    });

    it("returns G when last name is undefined", () => {
      expect(getUserInitials("Jane", undefined)).toBe("G");
    });

    it("returns G when both are undefined", () => {
      expect(getUserInitials(undefined, undefined)).toBe("G");
    });

    it("uppercases lowercase initials", () => {
      expect(getUserInitials("jane", "doe")).toBe("JD");
    });
  });
});
