import { describe, it, expect } from "vitest";

import type { MapInventoryItem } from "../../../../types/inventory.types";
import {
  computeMediaPlanMapBounds,
  computeMediaPlanMapView,
  WORLD_VIEW,
} from "../mapView.utils";

const itemWithCoords = (lat: number, lng: number): MapInventoryItem =>
  ({
    location: {
      location: {
        locationCoordinates: {
          coordinates: [{ latitude: lat, longitude: lng }],
        },
      },
    },
  }) as unknown as MapInventoryItem;

const itemWithoutCoords = (): MapInventoryItem =>
  ({
    location: {
      location: { locationCoordinates: { coordinates: [] }, country: "UAE" },
    },
  }) as unknown as MapInventoryItem;

// Feedback SI 41: media plan map must never present a wrong country
// (previously fell back to Tokyo — a UAE plan rendered a map of Japan).
describe("computeMediaPlanMapView", () => {
  it("returns the centroid of valid coordinates with detail zoom", () => {
    const view = computeMediaPlanMapView([
      itemWithCoords(24.0, 54.0),
      itemWithCoords(26.0, 56.0),
    ]);
    expect(view.center).toEqual([55.0, 25.0]); // [lng, lat]
    expect(view.zoom).toBe(8);
  });

  it("ignores items without valid coordinates when computing the centroid", () => {
    const view = computeMediaPlanMapView([
      itemWithoutCoords(),
      itemWithCoords(24.0, 54.0),
    ]);
    expect(view.center).toEqual([54.0, 24.0]);
    expect(view.zoom).toBe(8);
  });

  it("falls back to a neutral world view when there are no locations", () => {
    expect(computeMediaPlanMapView([])).toEqual(WORLD_VIEW);
  });

  it("falls back to a neutral world view when no coordinates are parseable", () => {
    const view = computeMediaPlanMapView([
      itemWithoutCoords(),
      itemWithoutCoords(),
    ]);
    expect(view).toEqual(WORLD_VIEW);
    // never a specific country's coordinates (old bug: Tokyo)
    expect(view.center).not.toEqual([139.7532144, 35.704686]);
  });
});

describe("computeMediaPlanMapBounds", () => {
  it("returns the bounding box spanning every valid coordinate", () => {
    const bounds = computeMediaPlanMapBounds([
      itemWithCoords(24.0, 54.0),
      itemWithCoords(26.0, 90.0),
      itemWithCoords(30.0, 70.0),
    ]);
    expect(bounds).toEqual({
      sw: [54.0, 24.0],
      ne: [90.0, 30.0],
    });
  });

  it("ignores items without valid coordinates", () => {
    const bounds = computeMediaPlanMapBounds([
      itemWithoutCoords(),
      itemWithCoords(24.0, 54.0),
      itemWithCoords(26.0, 56.0),
    ]);
    expect(bounds).toEqual({ sw: [54.0, 24.0], ne: [56.0, 26.0] });
  });

  it("returns null for a single distinct point (centroid framing already fits it)", () => {
    expect(computeMediaPlanMapBounds([itemWithCoords(24.0, 54.0)])).toBeNull();
  });

  it("returns null when every point is identical", () => {
    expect(
      computeMediaPlanMapBounds([
        itemWithCoords(24.0, 54.0),
        itemWithCoords(24.0, 54.0),
      ]),
    ).toBeNull();
  });

  it("returns null when there are no locations", () => {
    expect(computeMediaPlanMapBounds([])).toBeNull();
  });

  it("returns null when no coordinates are parseable", () => {
    expect(
      computeMediaPlanMapBounds([itemWithoutCoords(), itemWithoutCoords()]),
    ).toBeNull();
  });
});
