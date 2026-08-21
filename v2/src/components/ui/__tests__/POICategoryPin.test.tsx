import { render } from "@testing-library/react";
import { describe, it, expect } from "vitest";

import { POI_PIN_ICONS } from "../../../constants/poi-pin-icons.generated";
import POICategoryPin, {
  resolvePinCategory,
  hasPOIPin,
} from "../POICategoryPin";

describe("resolvePinCategory", () => {
  it("matches direct pin keys (snake of label)", () => {
    expect(resolvePinCategory("restaurant", null)).toBe("restaurant");
    expect(resolvePinCategory("Shopping Mall", null)).toBe("shopping_mall");
    expect(resolvePinCategory(null, "Train Station")).toBe("train_station");
  });

  it("maps Google-Places synonyms/spellings via aliases", () => {
    expect(resolvePinCategory("bus_station", null)).toBe("bus_stand");
    expect(resolvePinCategory("dentist", null)).toBe("dental");
    expect(resolvePinCategory("jewelry_store", null)).toBe("jewellery_shop");
    expect(resolvePinCategory("movie_theater", null)).toBe("movie_theatre");
    expect(resolvePinCategory("police", null)).toBe("police_station");
    expect(resolvePinCategory("electronics_store", null)).toBe(
      "electronic_store",
    );
  });

  it("falls back to others for unknown-but-present input", () => {
    expect(resolvePinCategory("something_weird", null)).toBe("others");
    expect(resolvePinCategory(null, "Random Place")).toBe("others");
  });

  it("returns null when there is nothing to resolve", () => {
    expect(resolvePinCategory(null, null)).toBeNull();
    expect(resolvePinCategory("", "")).toBeNull();
  });

  it("prefers primaryType over displayName", () => {
    expect(resolvePinCategory("bank", "Restaurant")).toBe("bank");
  });
});

describe("POI_PIN_ICONS", () => {
  it("has all 101 categories with colour + glyph", () => {
    const keys = Object.keys(POI_PIN_ICONS);
    expect(keys).toHaveLength(101);
    for (const k of keys) {
      const icon = POI_PIN_ICONS[k as keyof typeof POI_PIN_ICONS];
      expect(icon.color).toMatch(/^#[0-9A-Fa-f]{6}$/);
      expect(icon.glyph.length).toBeGreaterThan(0);
    }
  });

  it("hasPOIPin reflects registry membership", () => {
    expect(hasPOIPin("restaurant")).toBe(true);
    expect(hasPOIPin(null)).toBe(false);
  });
});

describe("POICategoryPin", () => {
  it("renders an svg pin with the category colour", () => {
    const { container } = render(<POICategoryPin category="restaurant" />);
    const svg = container.querySelector("svg");
    expect(svg).toBeInTheDocument();
    expect(svg?.innerHTML).toContain(POI_PIN_ICONS.restaurant.color);
  });
});
