import { POI_CATEGORIES } from "@constants/poi-icons.generated";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { __resetPOIIconMissLogForTests, getPOIIconUrl } from "../poi-icon-url";

const PREFIX = "https://planner-stg.movingwalls.com/POI-icons";

const toIconFileName = (category: string): string =>
  category
    .split("_")
    .map((word) => (word ? word[0].toUpperCase() + word.slice(1) : word))
    .join("_");

describe("getPOIIconUrl", () => {
  beforeEach(() => {
    __resetPOIIconMissLogForTests();
  });

  it("resolves a known primaryType to the CloudFront JPG path", () => {
    const icon = getPOIIconUrl("restaurant");
    expect(icon.isGeneric).toBe(false);
    expect(icon.category).toBe("restaurant");
    expect(icon.src).toBe(`${PREFIX}/Restaurant.jpg`);
    expect(icon.fallbackSrcs).toEqual([]);
  });

  it("returns related-category fallbacks for schools", () => {
    const icon = getPOIIconUrl("primary_school");
    expect(icon.src).toBe(`${PREFIX}/Primary_School.jpg`);
    expect(icon.fallbackSrcs).toEqual([`${PREFIX}/School.jpg`]);
  });

  it("returns multi-step fallback chain for store subtypes", () => {
    const icon = getPOIIconUrl("clothing_store");
    expect(icon.src).toBe(`${PREFIX}/Clothing_Store.jpg`);
    expect(icon.fallbackSrcs).toEqual([`${PREFIX}/Store.jpg`]);
  });

  it("returns empty fallbackSrcs for categories with no related match", () => {
    expect(getPOIIconUrl("restaurant").fallbackSrcs).toEqual([]);
    expect(getPOIIconUrl("airport").fallbackSrcs).toEqual([]);
  });

  it("title-cases multi-word categories in the URL", () => {
    expect(getPOIIconUrl("amusement_park").src).toBe(
      `${PREFIX}/Amusement_Park.jpg`,
    );
    expect(getPOIIconUrl("shopping_mall").src).toBe(
      `${PREFIX}/Shopping_Mall.jpg`,
    );
  });

  it("normalizes case on primaryType", () => {
    expect(getPOIIconUrl("Restaurant").category).toBe("restaurant");
  });

  it("converts spaces to underscores when snake-casing display name", () => {
    expect(getPOIIconUrl(undefined, "primary school").category).toBe(
      "primary_school",
    );
  });

  it("resolves display-name aliases that diverge from the type enum", () => {
    expect(getPOIIconUrl(undefined, "RV Park").category).toBe("rv_park");
    expect(getPOIIconUrl(undefined, "Police Station").category).toBe("police");
    expect(getPOIIconUrl(undefined, "ATM").category).toBe("atm");
  });

  it("prefers primaryType over display name when both are present", () => {
    expect(getPOIIconUrl("dentist", "ATM").category).toBe("dentist");
  });

  it("returns the generic inline SVG when no category can be resolved", () => {
    const icon = getPOIIconUrl("completely_unknown_type");
    expect(icon.isGeneric).toBe(true);
    expect(icon.category).toBeNull();
    expect(icon.src.startsWith("data:image/svg+xml")).toBe(true);
    expect(icon.fallbackSrcs).toEqual([]);
  });

  it("handles null/undefined inputs without throwing", () => {
    expect(getPOIIconUrl(null, null).isGeneric).toBe(true);
    expect(getPOIIconUrl(undefined, undefined).isGeneric).toBe(true);
    expect(getPOIIconUrl("", "").isGeneric).toBe(true);
  });

  it("logs unknown categories once (dev only)", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    getPOIIconUrl("unknown_a");
    getPOIIconUrl("unknown_a");
    getPOIIconUrl("unknown_b");
    expect(warn).toHaveBeenCalledTimes(2);
    warn.mockRestore();
  });

  it.each(POI_CATEGORIES)(
    "resolves every manifest category: %s",
    (category) => {
      const icon = getPOIIconUrl(category);
      expect(icon.isGeneric).toBe(false);
      expect(icon.category).toBe(category);
      expect(icon.src).toBe(`${PREFIX}/${toIconFileName(category)}.jpg`);
    },
  );
});
