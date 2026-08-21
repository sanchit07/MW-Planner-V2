import { describe, it, expect } from "vitest";

import { THEMES_COLORS } from "../constants";
import { PresentationTheme } from "../types";

describe("media-plan constants", () => {
  describe("THEMES_COLORS", () => {
    it("exports exactly four themes", () => {
      expect(THEMES_COLORS).toHaveLength(4);
    });

    it("each theme has id, name, and colors with primary, secondary, accent", () => {
      const themeIds = ["primary", "warning", "success", "purple-warning"];
      THEMES_COLORS.forEach((theme, index) => {
        expect(theme).toHaveProperty("id", themeIds[index]);
        expect(theme).toHaveProperty("name");
        expect(typeof theme.name).toBe("string");
        expect(theme.name.length).toBeGreaterThan(0);
        expect(theme).toHaveProperty("colors");
        expect(theme.colors).toHaveProperty("primary");
        expect(theme.colors).toHaveProperty("secondary");
        expect(theme.colors).toHaveProperty("accent");
        expect(typeof theme.colors.primary).toBe("string");
        expect(typeof theme.colors.secondary).toBe("string");
        expect(typeof theme.colors.accent).toBe("string");
      });
    });

    it("theme primary color var follows --color-mw- pattern", () => {
      THEMES_COLORS.forEach((theme: PresentationTheme) => {
        expect(theme.colors.primary).toMatch(/^--color-mw-/);
        expect(theme.colors.secondary).toMatch(/^--color-mw-/);
        expect(theme.colors.accent).toMatch(/^--color-mw-/);
      });
    });

    it("theme names match expected definitions", () => {
      const names = [
        "Modern Executive",
        "Vibrant Orange",
        "Professional Green",
        "Elegant Purple",
      ];
      expect(THEMES_COLORS.map((t) => t.name)).toEqual(names);
    });
  });
});
