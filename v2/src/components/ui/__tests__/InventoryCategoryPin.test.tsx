import { render } from "@testing-library/react";
import { describe, it, expect } from "vitest";

import InventoryCategoryPin, {
  getInventoryCategory,
} from "../InventoryCategoryPin";

describe("getInventoryCategory", () => {
  it("derives base from inventoryType (digital vs classic)", () => {
    expect(getInventoryCategory("Digital", undefined).base).toBe("digital");
    expect(getInventoryCategory("Classic", undefined).base).toBe("classic");
    expect(getInventoryCategory(undefined, undefined).base).toBe("classic");
  });

  it("detects transit and retail from venueType (case-insensitive, array or string)", () => {
    expect(getInventoryCategory("Digital", "Transit Station").variant).toBe(
      "transit",
    );
    expect(getInventoryCategory("Classic", ["Mall", "retail"]).variant).toBe(
      "retail",
    );
    expect(getInventoryCategory("Digital", ["Leisure"]).variant).toBe("plain");
  });

  it("transit wins when both match", () => {
    expect(
      getInventoryCategory("Digital", ["retail", "classic-transit"]).variant,
    ).toBe("transit");
  });

  it("ignores non-string venueType", () => {
    expect(getInventoryCategory("Classic", 123).variant).toBe("plain");
    expect(getInventoryCategory("Classic", null).variant).toBe("plain");
  });
});

describe("InventoryCategoryPin", () => {
  it("renders an svg pin", () => {
    const { container } = render(
      <InventoryCategoryPin inventoryType="Digital" venueType="Transit" />,
    );
    expect(container.querySelector("svg")).toBeInTheDocument();
  });
});
