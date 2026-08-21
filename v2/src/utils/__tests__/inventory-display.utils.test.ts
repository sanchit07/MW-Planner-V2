import { describe, it, expect } from "vitest";

import type {
  InventoryItem,
  InventoryRecommendationItem,
} from "../../types/inventory.types";
import {
  formatInventoryLocation,
  fromInventoryItem,
  fromRecommendationItem,
  getChipOverflow,
} from "../inventory-display.utils";

const baseInventoryItem: InventoryItem = {
  id: "1",
  detail: {
    id: "d1",
    name: "Test Inventory",
    externalId: "",
    referenceId: "REF-001",
    mediaOwnerId: "mo-1",
    mediaOwnerName: "Owner Co",
    inventoryType: "DIGITAL",
    category: "billboard",
    venueType: ["outdoor"],
    thumbnail: "https://example.com/img.png",
    images: [],
    format: "Digital",
    environment: "OUTDOOR",
    size: "M",
    operationMode: "",
    execution: "",
    screens: 5,
    sov: 10,
    isSelected: false,
    isCompliant: true,
    bookingMode: "",
    panels: [],
  },
  location: {
    location: {
      address: "123 Main St",
      country: "US",
      state: "CA",
      city: "LA",
      zipCode: "90001",
      locationCoordinates: { coordinates: [], type: "" },
    },
    poi: { types: [], nearbyPOIs: [], categories: [] },
    demographics: {
      age: "",
      gender: "",
      overall: "",
      ageGender: "",
      income: "",
      behaviour: "",
      interest: "",
      highestIndexScore: "",
    },
  },
  performance: {
    cpmRate: 100,
    estimatedCost: 5000,
    perDayCost: 200,
    perDayAdPlays: 10,
    totalAdPlays: 300,
    plannedSot: 2,
    totalSot: 24,
  },
  operations: {
    slotDuration: 15,
    clientPerLoop: 4,
  } as InventoryItem["operations"],
  schedules: [],
} as InventoryItem;

describe("fromInventoryItem", () => {
  it("maps spotRate when present", () => {
    const item: InventoryItem = {
      ...baseInventoryItem,
      performance: { ...baseInventoryItem.performance, spotRate: 12.5 },
    };
    const result = fromInventoryItem(item);
    expect(result.performance.spotRate).toBe(12.5);
  });

  it("maps spotRate as undefined when absent", () => {
    const result = fromInventoryItem(baseInventoryItem);
    expect(result.performance.spotRate).toBeUndefined();
  });

  it("maps cpmRate correctly", () => {
    const result = fromInventoryItem(baseInventoryItem);
    expect(result.performance.cpmRate).toBe(100);
  });

  it("defaults cpmRate to 0 when missing", () => {
    const item: InventoryItem = {
      ...baseInventoryItem,
      performance: {
        ...baseInventoryItem.performance,
        cpmRate: undefined as unknown as number,
      },
    };
    const result = fromInventoryItem(item);
    expect(result.performance.cpmRate).toBe(0);
  });

  it("maps estimatedCost correctly", () => {
    const result = fromInventoryItem(baseInventoryItem);
    expect(result.performance.estimatedCost).toBe(5000);
  });

  it("maps source as inventory", () => {
    const result = fromInventoryItem(baseInventoryItem);
    expect(result.source).toBe("inventory");
  });

  it("maps operations through directly", () => {
    const result = fromInventoryItem(baseInventoryItem);
    expect(result.operations).toBe(baseInventoryItem.operations);
  });

  it("maps estimatedImpression (singular) to estimatedImpressions", () => {
    const item: InventoryItem = {
      ...baseInventoryItem,
      performance: {
        ...baseInventoryItem.performance,
        estimatedImpression: 249550,
      },
    };
    const result = fromInventoryItem(item);
    expect(result.performance.estimatedImpressions).toBe(249550);
  });

  it("maps estimatedReach and estimatedFrequency", () => {
    const item: InventoryItem = {
      ...baseInventoryItem,
      performance: {
        ...baseInventoryItem.performance,
        estimatedReach: 127060,
        estimatedFrequency: 1.96,
      },
    };
    const result = fromInventoryItem(item);
    expect(result.performance.estimatedReach).toBe(127060);
    expect(result.performance.estimatedFrequency).toBe(1.96);
  });

  it("maps estimated reach/frequency/impressions as undefined when absent", () => {
    const result = fromInventoryItem(baseInventoryItem);
    expect(result.performance.estimatedImpressions).toBeUndefined();
    expect(result.performance.estimatedReach).toBeUndefined();
    expect(result.performance.estimatedFrequency).toBeUndefined();
  });

  it("passes detail.size through directly", () => {
    const result = fromInventoryItem(baseInventoryItem);
    expect(result.detail.size).toBe("M");
  });
});

const baseRecommendationItem: InventoryRecommendationItem = {
  inventoryId: "inv-r1",
  referenceId: "REF-R01",
  name: "Rec Inventory",
  selectionMode: "NOT_SELECTED",
  performance: {
    cpmRate: 80,
    estimatedCost: 4000,
    perDayCost: 150,
    perDayAdPlays: 8,
    totalAdPlays: 240,
    plannedSot: 1,
    totalSot: 20,
    estimatedReach: 1000,
    estimatedFrequency: 2,
    estimatedImpressions: 2000,
  },
  inventoryDetails: {
    internalId: "internal-r1",
    classification: "DIGITAL",
    type: "billboard",
    venueTypes: ["outdoor"],
    format: "Digital",
    environment: "OUTDOOR",
    mediaOwnerId: "mo-2",
    mediaOwnerName: "Rec Owner",
    thumbnailUrl: "https://example.com/rec.png",
    address: "456 Rec Ave",
    location: {
      countryName: "US",
      stateName: "NY",
      cityName: "NYC",
      locationCoordinates: undefined,
    },
    digitalFields: {
      spotDuration: 10,
      spotsPerLoop: 6,
    },
  },
} as unknown as InventoryRecommendationItem;

describe("fromRecommendationItem", () => {
  it("maps spotRate when present", () => {
    const item: InventoryRecommendationItem = {
      ...baseRecommendationItem,
      performance: { ...baseRecommendationItem.performance, spotRate: 9.75 },
    };
    const result = fromRecommendationItem(item);
    expect(result.performance.spotRate).toBe(9.75);
  });

  it("maps spotRate as undefined when absent", () => {
    const result = fromRecommendationItem(baseRecommendationItem);
    expect(result.performance.spotRate).toBeUndefined();
  });

  it("maps cpmRate correctly", () => {
    const result = fromRecommendationItem(baseRecommendationItem);
    expect(result.performance.cpmRate).toBe(80);
  });

  it("maps cpmRate as undefined when missing", () => {
    const item: InventoryRecommendationItem = {
      ...baseRecommendationItem,
      performance: {
        ...baseRecommendationItem.performance,
        cpmRate: undefined as unknown as number,
      },
    };
    const result = fromRecommendationItem(item);
    expect(result.performance.cpmRate).toBeUndefined();
  });

  it("maps source as recommendation", () => {
    const result = fromRecommendationItem(baseRecommendationItem);
    expect(result.source).toBe("recommendation");
  });

  it("maps operations from digitalFields", () => {
    const result = fromRecommendationItem(baseRecommendationItem);
    expect(result.operations).toEqual({ slotDuration: 10, clientPerLoop: 6 });
  });

  it("sets operations to undefined when digitalFields is absent", () => {
    const item: InventoryRecommendationItem = {
      ...baseRecommendationItem,
      inventoryDetails: {
        ...baseRecommendationItem.inventoryDetails,
        digitalFields: undefined,
      },
    } as unknown as InventoryRecommendationItem;
    const result = fromRecommendationItem(item);
    expect(result.operations).toBeUndefined();
  });

  it("maps estimatedCost correctly", () => {
    const result = fromRecommendationItem(baseRecommendationItem);
    expect(result.performance.estimatedCost).toBe(4000);
  });

  it("maps inventoryDetails.sizes into panels carrying size", () => {
    const item: InventoryRecommendationItem = {
      ...baseRecommendationItem,
      inventoryDetails: {
        ...baseRecommendationItem.inventoryDetails,
        sizes: ["L", "XL"],
      },
    } as unknown as InventoryRecommendationItem;
    const result = fromRecommendationItem(item);
    expect(result.detail.panels).toEqual([{ size: "L" }, { size: "XL" }]);
  });

  it("maps panels to empty array when sizes is absent", () => {
    const result = fromRecommendationItem(baseRecommendationItem);
    expect(result.detail.panels).toEqual([]);
  });

  it("sets detail.size to the first of inventoryDetails.sizes", () => {
    const item: InventoryRecommendationItem = {
      ...baseRecommendationItem,
      inventoryDetails: {
        ...baseRecommendationItem.inventoryDetails,
        sizes: ["L", "XL"],
      },
    } as unknown as InventoryRecommendationItem;
    const result = fromRecommendationItem(item);
    expect(result.detail.size).toBe("L");
  });

  it("sets detail.size to undefined when sizes is absent", () => {
    const result = fromRecommendationItem(baseRecommendationItem);
    expect(result.detail.size).toBeUndefined();
  });
});

describe("formatInventoryLocation", () => {
  it("returns the address when present", () => {
    expect(
      formatInventoryLocation({
        address: "1 Main St",
        country: "US",
        state: "CA",
      }),
    ).toBe("1 Main St");
  });

  it("joins country and state when address is absent", () => {
    expect(formatInventoryLocation({ country: "US", state: "CA" })).toBe(
      "US, CA",
    );
  });

  it("omits missing state instead of rendering 'undefined'", () => {
    expect(formatInventoryLocation({ country: "US" })).toBe("US");
    expect(formatInventoryLocation({ country: "US", state: "" })).toBe("US");
  });

  it("returns an empty string when nothing is available", () => {
    expect(formatInventoryLocation({})).toBe("");
    expect(formatInventoryLocation(undefined)).toBe("");
  });
});

describe("getChipOverflow", () => {
  it("returns a single visible label with no overflow", () => {
    expect(getChipOverflow(["Outdoor"], 2)).toEqual({
      visibleText: "Outdoor",
      overflowCount: 0,
      allText: "Outdoor",
      labels: ["Outdoor"],
      count: 1,
    });
  });

  it("collapses extra labels into +N and lists all in allText/labels", () => {
    expect(getChipOverflow(["Outdoor", "Billboards", "Highway"], 2)).toEqual({
      visibleText: "Outdoor, Billboards",
      overflowCount: 1,
      allText: "Outdoor, Billboards, Highway",
      labels: ["Outdoor", "Billboards", "Highway"],
      count: 3,
    });
  });

  it("filters out falsy labels and handles empty/missing input", () => {
    expect(getChipOverflow(["", "Mall", ""], 2)).toEqual({
      visibleText: "Mall",
      overflowCount: 0,
      allText: "Mall",
      labels: ["Mall"],
      count: 1,
    });
    expect(getChipOverflow([], 2)).toEqual({
      visibleText: "",
      overflowCount: 0,
      allText: "",
      labels: [],
      count: 0,
    });
    expect(getChipOverflow(null, 2).count).toBe(0);
  });
});
