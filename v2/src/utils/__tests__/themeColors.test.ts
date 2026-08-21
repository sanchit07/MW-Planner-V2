import { describe, it, expect } from "vitest";

import {
  getThemeColorFamily,
  getThemePrimaryColorVar,
  getThemeSecondaryColorVar,
  getThemeAccentColorVar,
  getCssVariableValue,
  getThemePrimaryColor,
  getThemeSecondaryColor,
  getThemeAccentColor,
  getThemeTextColorClass,
  getThemeBgColorClass,
  hexToRgbString,
  initializeThemeColors,
} from "../themeColors";

describe("themeColors", () => {
  describe("getThemeColorFamily", () => {
    it("should return primary for primary theme", () => {
      expect(getThemeColorFamily("primary")).toBe("primary");
    });

    it("should return warning for warning theme", () => {
      expect(getThemeColorFamily("warning")).toBe("warning");
    });

    it("should return success for success theme", () => {
      expect(getThemeColorFamily("success")).toBe("success");
    });

    it("should return primary as default for unknown theme", () => {
      expect(getThemeColorFamily("unknown")).toBe("primary");
    });

    it("should map data-blue to primary", () => {
      expect(getThemeColorFamily("data-blue")).toBe("primary");
    });

    it("should map dashboard-green to success", () => {
      expect(getThemeColorFamily("dashboard-green")).toBe("success");
    });
  });

  describe("getThemePrimaryColorVar", () => {
    it("should return correct CSS variable name", () => {
      expect(getThemePrimaryColorVar("primary")).toBe("--color-mw-primary-500");
      expect(getThemePrimaryColorVar("success")).toBe("--color-mw-success-500");
    });
  });

  describe("getThemeSecondaryColorVar", () => {
    it("should return correct CSS variable name", () => {
      expect(getThemeSecondaryColorVar("primary")).toBe(
        "--color-mw-primary-400",
      );
    });
  });

  describe("getThemeAccentColorVar", () => {
    it("should return correct CSS variable name", () => {
      expect(getThemeAccentColorVar("primary")).toBe("--color-mw-primary-300");
    });
  });

  describe("getCssVariableValue", () => {
    it("should return hex color for known variable", () => {
      expect(getCssVariableValue("--color-mw-primary-500")).toBe("#2176cc");
      expect(getCssVariableValue("--color-mw-success-500")).toBe("#2d7d32");
    });

    it("should return default color for unknown variable", () => {
      expect(getCssVariableValue("--color-unknown")).toBe("#2176cc");
    });
  });

  describe("getThemePrimaryColor", () => {
    it("should return hex color for theme", () => {
      expect(getThemePrimaryColor("primary")).toBe("#2176cc");
    });
  });

  describe("getThemeSecondaryColor", () => {
    it("should return hex color for theme", () => {
      expect(getThemeSecondaryColor("primary")).toBe("#4a84bf");
    });
  });

  describe("getThemeAccentColor", () => {
    it("should return hex color for theme", () => {
      expect(getThemeAccentColor("primary")).toBe("#6898c9");
    });
  });

  describe("getThemeTextColorClass", () => {
    it("should return correct Tailwind class", () => {
      expect(getThemeTextColorClass("primary")).toBe("text-mw-primary-500");
      expect(getThemeTextColorClass("success")).toBe("text-mw-success-500");
    });
  });

  describe("getThemeBgColorClass", () => {
    it("should return correct Tailwind class", () => {
      expect(getThemeBgColorClass("primary")).toBe("bg-mw-primary-50");
      expect(getThemeBgColorClass("success")).toBe("bg-mw-success-50");
    });
  });

  describe("hexToRgbString", () => {
    it("should convert 6-char hex to uppercase", () => {
      expect(hexToRgbString("#2176cc")).toBe("2176CC");
      expect(hexToRgbString("2176cc")).toBe("2176CC");
    });

    it("should convert 3-char hex to 6-char", () => {
      expect(hexToRgbString("#abc")).toBe("AABBCC");
      expect(hexToRgbString("abc")).toBe("AABBCC");
    });

    it("should handle uppercase input", () => {
      expect(hexToRgbString("#ABC")).toBe("AABBCC");
    });
  });

  describe("initializeThemeColors", () => {
    it("should return default colors when no params provided", () => {
      const result = initializeThemeColors();
      expect(result.primary).toBe("FF4472C4");
      expect(result.secondary).toBe("FF2E75B6");
      expect(result.lightGray).toBe("FFF2F2F2");
    });

    it("should use defaultColors when provided", () => {
      const defaultColors = {
        primary: "FF123456",
        secondary: "FF789ABC",
        lightGray: "FFDEFDEF",
      };
      const result = initializeThemeColors(defaultColors);
      expect(result.primary).toBe("FF123456");
      expect(result.secondary).toBe("FF789ABC");
      expect(result.lightGray).toBe("FFDEFDEF");
    });

    it("should use theme when provided", () => {
      const theme = {
        colors: {
          primary: "--color-mw-primary-500",
          secondary: "--color-mw-secondary-500",
        },
      };
      const result = initializeThemeColors(undefined, theme);
      expect(result.primary).toBeDefined();
      expect(result.secondary).toBeDefined();
      expect(result.lightGray).toBe("FFF2F2F2");
    });
  });
});
