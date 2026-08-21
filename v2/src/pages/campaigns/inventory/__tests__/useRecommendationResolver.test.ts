import { renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const fetchInventoryListTrigger = vi.fn();

vi.mock("@services/inventory/inventorySlice", () => ({
  useLazyGetInventoryListQuery: () => [fetchInventoryListTrigger],
}));

// Import after the mock is registered.
import { useRecommendationResolver } from "../useRecommendationResolver";

const makeItem = (
  id: string,
  lat: number,
  lng: number,
  overrides?: {
    name?: string;
    stateName?: string;
    thumbnail?: string;
    totalAdPlays?: number;
    perDayAdPlays?: number;
    estimatedCost?: number;
  },
) =>
  ({
    detail: {
      id,
      name: overrides?.name ?? `name-${id}`,
      thumbnail: overrides?.thumbnail ?? "https://x/thumb.jpg",
    },
    location: {
      location: {
        state: overrides?.stateName ?? "Osaka",
        locationCoordinates: {
          type: "Point",
          coordinates: [{ latitude: lat, longitude: lng }],
        },
      },
    },
    performance: {
      totalAdPlays: overrides?.totalAdPlays,
      perDayAdPlays: overrides?.perDayAdPlays,
      estimatedCost: overrides?.estimatedCost,
    },
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
  }) as any;

const campaign = {
  id: "camp-1",
  countryId: "JP",
  currency: "JPY",
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
} as any;

beforeEach(() => {
  fetchInventoryListTrigger.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("useRecommendationResolver (inventory-list source)", () => {
  it("returns null when no campaignId is provided", async () => {
    const { result } = renderHook(() =>
      useRecommendationResolver(undefined, campaign),
    );
    expect(
      await result.current.resolveForPOI({
        locationLat: 34.7,
        locationLng: 135.5,
      }),
    ).toBeNull();
    expect(fetchInventoryListTrigger).not.toHaveBeenCalled();
  });

  it("maps metrics from an item when one is found", async () => {
    fetchInventoryListTrigger.mockReturnValueOnce({
      unwrap: () =>
        Promise.resolve({
          data: {
            content: [
              makeItem("a", 34.7, 135.5001, {
                name: "Osaka Station Billboard",
                stateName: "Osaka",
                totalAdPlays: 3_400_680,
                perDayAdPlays: 42,
                estimatedCost: 59_900,
                thumbnail: "https://cdn/osaka.jpg",
              }),
            ],
          },
        }),
    });

    const { result } = renderHook(() =>
      useRecommendationResolver("camp-1", campaign),
    );
    const out = await result.current.resolveForPOI({
      locationLat: 34.7,
      locationLng: 135.5,
      displayName: "Random POI",
    });
    expect(out).not.toBeNull();
    // stateLabel is intentionally omitted by the resolver — the hash fallback
    // can match a POI to an inventory in a different state, so the popup
    // falls back to poi.primaryTypeDisplayName instead.
    expect(out?.stateLabel).toBeUndefined();
    expect(out?.impressions).toBe(3_400_680);
    expect(out?.spots).toBe(42);
    expect(out?.price).toBe(59_900);
    expect(out?.currency).toBe("JPY");
    expect(out?.thumbnail).toBe("https://cdn/osaka.jpg");
  });

  it("prefers name substring match over hash fallback", async () => {
    fetchInventoryListTrigger.mockReturnValueOnce({
      unwrap: () =>
        Promise.resolve({
          data: {
            content: [
              makeItem("a", 34.7, 135.5, {
                name: "Unrelated Billboard",
                totalAdPlays: 1,
              }),
              makeItem("b", 34.8, 135.5, {
                name: "Osaka Station Digital Board 3",
                totalAdPlays: 100,
              }),
            ],
          },
        }),
    });

    const { result } = renderHook(() =>
      useRecommendationResolver("camp-1", campaign),
    );
    const out = await result.current.resolveForPOI({
      locationLat: 34.7,
      locationLng: 135.5,
      displayName: "Osaka Station",
    });
    expect(out?.impressions).toBe(100);
  });

  it("distributes different POIs across different inventories via hash", async () => {
    fetchInventoryListTrigger.mockReturnValue({
      unwrap: () =>
        Promise.resolve({
          data: {
            content: [
              makeItem("a", 34.7, 135.5, {
                name: "Ad 1",
                totalAdPlays: 1,
              }),
              makeItem("b", 34.71, 135.51, {
                name: "Ad 2",
                totalAdPlays: 2,
              }),
              makeItem("c", 34.72, 135.52, {
                name: "Ad 3",
                totalAdPlays: 3,
              }),
            ],
          },
        }),
    });

    const { result } = renderHook(() =>
      useRecommendationResolver("camp-1", campaign),
    );

    // Distinct POIs should land on a spread of items — not all on the same one.
    const outs = await Promise.all(
      ["Kyukyodo", "Nanaya Kyoto Sanjo", "Sanjo Park", "Gion Temple"].map(
        (displayName, i) =>
          result.current.resolveForPOI({
            locationLat: 35 + i * 0.01,
            locationLng: 135.5 + i * 0.01,
            displayName,
          }),
      ),
    );
    const uniqueImpressions = new Set(outs.map((o) => o?.impressions));
    expect(uniqueImpressions.size).toBeGreaterThan(1);
  });

  it("resolves the same POI to the same inventory on repeat calls", async () => {
    fetchInventoryListTrigger.mockReturnValueOnce({
      unwrap: () =>
        Promise.resolve({
          data: {
            content: [
              makeItem("a", 0, 0, { totalAdPlays: 1 }),
              makeItem("b", 0, 0, { totalAdPlays: 2 }),
              makeItem("c", 0, 0, { totalAdPlays: 3 }),
            ],
          },
        }),
    });

    const { result } = renderHook(() =>
      useRecommendationResolver("camp-1", campaign),
    );
    const poi = {
      locationLat: 34.7,
      locationLng: 135.5,
      displayName: "Kyukyodo",
    };
    const first = await result.current.resolveForPOI(poi);
    const second = await result.current.resolveForPOI(poi);
    expect(first?.impressions).toBe(second?.impressions);
  });

  it("caches the list so a second POI click does not re-fetch", async () => {
    fetchInventoryListTrigger.mockReturnValueOnce({
      unwrap: () =>
        Promise.resolve({
          data: {
            content: [makeItem("a", 34.7, 135.5001, { totalAdPlays: 1 })],
          },
        }),
    });

    const { result } = renderHook(() =>
      useRecommendationResolver("camp-1", campaign),
    );
    const first = await result.current.resolveForPOI({
      locationLat: 34.7,
      locationLng: 135.5,
    });
    const second = await result.current.resolveForPOI({
      locationLat: 34.7,
      locationLng: 135.5001,
    });
    expect(first?.impressions).toBe(1);
    expect(second?.impressions).toBe(1);
    expect(fetchInventoryListTrigger).toHaveBeenCalledTimes(1);
  });

  it("includes inventoryCluster from targeting.inventoryCluster", async () => {
    fetchInventoryListTrigger.mockReturnValueOnce({
      unwrap: () => Promise.resolve({ data: { content: [] } }),
    });

    const campaignWithClusters = {
      ...campaign,
      targeting: {
        inventoryCluster: ["DIGITAL", "CLASSIC_NETWORK", "CLASSIC_TRANSIT"],
      },
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any;

    const { result } = renderHook(() =>
      useRecommendationResolver("camp-1", campaignWithClusters),
    );
    await result.current.resolveForPOI({
      locationLat: 34.7,
      locationLng: 135.5,
    });

    expect(fetchInventoryListTrigger).toHaveBeenCalledWith(
      expect.objectContaining({
        params: expect.objectContaining({
          inventoryCluster: ["DIGITAL", "CLASSIC_NETWORK", "CLASSIC_TRANSIT"],
        }),
      }),
      true,
    );
  });

  it("omits inventoryCluster when no inventory clusters are selected", async () => {
    fetchInventoryListTrigger.mockReturnValueOnce({
      unwrap: () => Promise.resolve({ data: { content: [] } }),
    });

    const { result } = renderHook(() =>
      useRecommendationResolver("camp-1", campaign),
    );
    await result.current.resolveForPOI({
      locationLat: 34.7,
      locationLng: 135.5,
    });

    const calledParams = fetchInventoryListTrigger.mock.calls[0][0].params;
    expect(calledParams).not.toHaveProperty("inventoryCluster");
  });
});
