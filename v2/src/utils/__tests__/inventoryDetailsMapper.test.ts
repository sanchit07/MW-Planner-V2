import { describe, it, expect, vi } from "vitest";

import {
  parseFirstGeomToLatLong,
  mapInventoryDetailsResponseToInventoryItem,
} from "../inventoryDetailsMapper";

vi.mock("@constants/inventory.constants", () => ({
  InventoryClassification: { DIGITAL: "Digital", CLASSIC: "Classic" },
}));

describe("parseFirstGeomToLatLong", () => {
  it("returns zeros when geoms is undefined", () => {
    expect(parseFirstGeomToLatLong(undefined)).toEqual({ lat: 0, long: 0 });
  });

  it("returns zeros when geoms is empty", () => {
    expect(parseFirstGeomToLatLong([])).toEqual({ lat: 0, long: 0 });
  });

  it("parses valid WKT POINT string", () => {
    expect(parseFirstGeomToLatLong(["POINT(103.8 1.35)"])).toEqual({
      lat: 1.35,
      long: 103.8,
    });
  });

  it("returns zeros when geom has no POINT(", () => {
    expect(parseFirstGeomToLatLong(["INVALID"])).toEqual({ lat: 0, long: 0 });
  });

  it("returns zeros when coordinates are not numbers", () => {
    expect(parseFirstGeomToLatLong(["POINT(abc xyz)"])).toEqual({
      lat: 0,
      long: 0,
    });
  });

  it("uses first geom when multiple are present", () => {
    expect(parseFirstGeomToLatLong(["POINT(10 20)", "POINT(30 40)"])).toEqual({
      lat: 20,
      long: 10,
    });
  });
});

describe("mapInventoryDetailsResponseToInventoryItem", () => {
  const minimalResponse = {
    id: "id-1",
    name: "Test",
    referenceId: "ref-1",
    mediaOwnerId: "mo-1",
    mediaOwnerName: "Owner",
    typeName: "Digital",
    typePath: "Path",
    displayFormatName: "Format",
    environment: "Outdoor",
    address: "Addr",
    adminLevel0Name: "Country",
    adminLevel1Name: "State",
    adminLevel2Name: "City",
    postalCode: "123",
    thumbnailUrl: "",
    medias: [],
    geoms: ["POINT(103.8 1.35)"],
    schedule: {
      operatingTimes: { MONDAY: [{ start: "08:00", end: "22:00" }] },
    },
    panels: [],
    prices: [{ cpm: 10 }],
    digitalFields: {
      bookingMode: "spot",
      playerCount: 1,
      loopDuration: 60,
      spotDuration: 15,
      spotsPerLoop: 4,
    },
    venues: [{ name: "Venue" }],
  } as unknown as Parameters<
    typeof mapInventoryDetailsResponseToInventoryItem
  >[0];

  it("returns null when response is undefined", () => {
    expect(mapInventoryDetailsResponseToInventoryItem(undefined)).toBeNull();
  });

  it("returns InventoryItem shape with detail, location, performance, operations", () => {
    const result = mapInventoryDetailsResponseToInventoryItem(minimalResponse);
    expect(result).not.toBeNull();
    expect(result?.detail.name).toBe("Test");
    expect(result?.detail.externalId).toBe("id-1");
    expect(result?.location.location.address).toBe("Addr");
    expect(result?.performance.cpmRate).toBe(10);
    expect(result?.operations.startTime).toBe("08:00");
    expect(result?.operations.endTime).toBe("22:00");
  });

  it("parses geoms into location coordinates", () => {
    const result = mapInventoryDetailsResponseToInventoryItem(minimalResponse);
    expect(result?.location.location.locationCoordinates.coordinates).toEqual([
      { latitude: 1.35, longitude: 103.8 },
    ]);
  });

  it("returns empty coordinates when geoms are invalid", () => {
    const res = { ...minimalResponse, geoms: ["INVALID"] } as Parameters<
      typeof mapInventoryDetailsResponseToInventoryItem
    >[0];
    const result = mapInventoryDetailsResponseToInventoryItem(res);
    expect(result?.location.location.locationCoordinates.coordinates).toEqual(
      [],
    );
  });

  it("passes the response's flat size code through to detail.size", () => {
    const res = { ...minimalResponse, size: "xl" } as Parameters<
      typeof mapInventoryDetailsResponseToInventoryItem
    >[0];
    const result = mapInventoryDetailsResponseToInventoryItem(res);
    expect(result?.detail.size).toBe("xl");
  });

  it("defaults detail.size to an empty string when the response has none", () => {
    const result = mapInventoryDetailsResponseToInventoryItem(minimalResponse);
    expect(result?.detail.size).toBe("");
  });
});
