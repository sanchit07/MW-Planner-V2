import { describe, it, expect } from "vitest";

import type { CampaignCreateResponse } from "../../types/campaign.types";
import {
  extractGeofencingPOIs,
  isPointInPolygon,
  filterPOIPlacesInsidePolygon,
} from "../geofencing-pois";

const place = (displayName: string) => ({
  displayName,
  primaryType: "park",
  primaryTypeDisplayName: "Park",
  locationLat: 35.6,
  locationLng: 139.7,
});

function campaign(geofencing: unknown): CampaignCreateResponse {
  return { targeting: { geofencing } } as unknown as CampaignCreateResponse;
}

describe("extractGeofencingPOIs", () => {
  it("returns [] when there is no campaign / geofencing", () => {
    expect(extractGeofencingPOIs(null)).toEqual([]);
    expect(extractGeofencingPOIs(undefined)).toEqual([]);
    expect(extractGeofencingPOIs(campaign(undefined))).toEqual([]);
  });

  it("parses places from locations and geometries metadata JSON", () => {
    const data = campaign({
      locations: [
        {
          poi: ["k1"],
          metadata: { k1: JSON.stringify({ places: [place("A")] }) },
        },
      ],
      geometries: [
        {
          poi: ["k2"],
          metadata: { k2: JSON.stringify({ places: [place("B")] }) },
        },
      ],
    });
    const result = extractGeofencingPOIs(data);
    expect(result.map((p) => p.displayName)).toEqual(["A", "B"]);
  });

  it("ignores entries without poi/metadata and malformed JSON", () => {
    const data = campaign({
      locations: [
        { poi: [], metadata: {} },
        { poi: ["bad"], metadata: { bad: "{not json" } },
        {
          poi: ["ok"],
          metadata: { ok: JSON.stringify({ places: [place("C")] }) },
        },
      ],
    });
    const result = extractGeofencingPOIs(data);
    expect(result.map((p) => p.displayName)).toEqual(["C"]);
  });
});

describe("isPointInPolygon", () => {
  // A simple square: [lng, lat] pairs from (0,0) to (10,10).
  const square: number[][] = [
    [0, 0],
    [10, 0],
    [10, 10],
    [0, 10],
    [0, 0],
  ];

  // A triangle to mirror the reported bug (POIs in the bbox corners must be out).
  const triangle: number[][] = [
    [0, 0],
    [10, 0],
    [5, 10],
    [0, 0],
  ];

  it("returns true for a point inside the polygon", () => {
    expect(isPointInPolygon([5, 5], square)).toBe(true);
    expect(isPointInPolygon([5, 2], triangle)).toBe(true);
  });

  it("returns false for a point outside the polygon", () => {
    expect(isPointInPolygon([20, 20], square)).toBe(false);
    // Top-left/top-right corners of the triangle's bounding box are outside it.
    expect(isPointInPolygon([1, 9], triangle)).toBe(false);
    expect(isPointInPolygon([9, 9], triangle)).toBe(false);
  });

  it("returns false for a degenerate or missing ring", () => {
    expect(isPointInPolygon([5, 5], [])).toBe(false);
    expect(
      isPointInPolygon(
        [5, 5],
        [
          [0, 0],
          [10, 0],
        ],
      ),
    ).toBe(false);
  });
});

describe("filterPOIPlacesInsidePolygon", () => {
  const triangle: number[][] = [
    [0, 0],
    [10, 0],
    [5, 10],
    [0, 0],
  ];
  const inside = { displayName: "in", locationLng: 5, locationLat: 2 };
  const outside = { displayName: "out", locationLng: 9, locationLat: 9 };

  it("keeps only places inside the polygon", () => {
    const result = filterPOIPlacesInsidePolygon([inside, outside], triangle);
    expect(result.map((p) => p.displayName)).toEqual(["in"]);
  });

  it("returns places unchanged when the ring is missing/degenerate", () => {
    const places = [inside, outside];
    expect(filterPOIPlacesInsidePolygon(places, null)).toBe(places);
    expect(filterPOIPlacesInsidePolygon(places, [[0, 0]])).toBe(places);
  });
});
