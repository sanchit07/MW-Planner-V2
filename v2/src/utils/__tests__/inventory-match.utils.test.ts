import type { InventoryItem } from "src/types/inventory.types";
import { describe, expect, it } from "vitest";

import {
  findClosestWithinThreshold,
  findInventoryForPOI,
  haversineMeters,
} from "../inventory-match.utils";

const makeItem = (
  id: string,
  lat: number | undefined,
  lng: number | undefined,
): InventoryItem =>
  ({
    detail: { id, name: id, thumbnail: "" },
    location: {
      location: {
        state: "",
        locationCoordinates:
          lat === undefined || lng === undefined
            ? { coordinates: [], type: "Point" }
            : {
                coordinates: [{ latitude: lat, longitude: lng }],
                type: "Point",
              },
      },
    },
    performance: {
      cpmRate: 0,
      estimatedCost: 0,
      perDayCost: 0,
      perDayAdPlays: 0,
      totalAdPlays: 0,
      plannedSot: 0,
      totalSot: 0,
    },
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
  }) as any;

describe("haversineMeters", () => {
  it("returns 0 for identical points", () => {
    expect(haversineMeters(3.14, 101.69, 3.14, 101.69)).toBe(0);
  });

  it("measures ~111 km for 1° of latitude", () => {
    const d = haversineMeters(0, 0, 1, 0);
    expect(d).toBeGreaterThan(110_000);
    expect(d).toBeLessThan(112_000);
  });
});

describe("findInventoryForPOI", () => {
  const poi = { locationLat: 3.14, locationLng: 101.69 };

  it("returns an item within the 150m threshold", () => {
    // ~11m east of the POI
    const item = makeItem("near", 3.14, 101.6901);
    expect(findInventoryForPOI([item], poi)).toBe(item);
  });

  it("returns null when closest item is beyond the threshold", () => {
    // ~1 km away
    const item = makeItem("far", 3.149, 101.69);
    expect(findInventoryForPOI([item], poi)).toBeNull();
  });

  it("prefers the closest item when multiple are within threshold", () => {
    const close = makeItem("close", 3.1400005, 101.6900005);
    const closer = makeItem("closer", 3.14, 101.69);
    const result = findInventoryForPOI([close, closer], poi);
    expect(result).toBe(closer);
  });

  it("skips items with missing coordinates", () => {
    const bad = makeItem("bad", undefined, undefined);
    const good = makeItem("good", 3.14, 101.6901);
    expect(findInventoryForPOI([bad, good], poi)).toBe(good);
  });

  it("returns null on an empty list", () => {
    expect(findInventoryForPOI([], poi)).toBeNull();
  });

  it("honours a custom threshold", () => {
    const item = makeItem("here", 3.14, 101.6901); // ~11m away
    expect(findInventoryForPOI([item], poi, 5)).toBeNull();
    expect(findInventoryForPOI([item], poi, 50)).toBe(item);
  });
});

describe("findClosestWithinThreshold (generic)", () => {
  const poi = { locationLat: 3.14, locationLng: 101.69 };

  it("picks the closest item using a GeoJSON [lng, lat] extractor", () => {
    const items = [
      { id: "a", coords: [101.6901, 3.14] as [number, number] }, // ~11m
      { id: "b", coords: [101.69, 3.14] as [number, number] }, // 0m
    ];
    const result = findClosestWithinThreshold(
      items,
      (it) => ({ lat: it.coords[1], lng: it.coords[0] }),
      poi,
    );
    expect(result?.id).toBe("b");
  });

  it("returns null when the extractor yields null for every item", () => {
    const items = [{ id: "x" }, { id: "y" }];
    const result = findClosestWithinThreshold(items, () => null, poi);
    expect(result).toBeNull();
  });

  it("returns null when all extracted coords exceed the threshold", () => {
    const items = [{ lat: 3.2, lng: 101.69 }]; // ~6.6 km
    const result = findClosestWithinThreshold(
      items,
      (it) => ({ lat: it.lat, lng: it.lng }),
      poi,
      150,
    );
    expect(result).toBeNull();
  });
});
