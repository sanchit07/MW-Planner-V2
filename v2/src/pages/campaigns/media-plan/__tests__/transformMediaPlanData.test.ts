import { describe, it, expect, vi } from "vitest";

import { transformMediaPlanData } from "../transformMediaPlanData";
import type { UnifiedMediaPlanData } from "../useMediaPlanData";

vi.mock("@utils/budget.utils", () => ({
  formatNumber: vi.fn((n: number | null | undefined) =>
    n != null ? String(n) : "0",
  ),
  normalizeGoalType: vi.fn((goalType?: string) => {
    if (!goalType) return undefined;
    const normalized = goalType.toUpperCase().replace(/[\s%]+/g, "");
    if (normalized === "ADPLAYS" || normalized === "ADPLAY") return "ADPLAYS";
    if (normalized === "SOV" || normalized === "SHAREOFVOICE") return "SOV";
    if (normalized === "IMPRESSIONS" || normalized === "NUMBEROFIMPRESSIONS")
      return "IMPRESSIONS";
    if (normalized === "REACH" || normalized === "UNIQUEUSERS") return "REACH";
    return normalized;
  }),
}));

vi.mock("@utils/dateUtils", () => ({
  formatDisplayDate: vi.fn((date: string | null | undefined) => date ?? ""),
}));

// ---------------------------------------------------------------------------
// Factory helpers
// ---------------------------------------------------------------------------

const minimalData = (): UnifiedMediaPlanData =>
  ({
    mediaPlan: { brandDetails: {}, headerInfo: {} },
    costSplitByState: [],
    costSplitByCity: [],
    costSplitByInventoryType: [],
    costSplitByVenueType: [],
    forecastData: null,
    selectedInventory: { summaryStatistics: {}, locations: [] },
    headerInfo: {},
    performanceMetrics: null,
    geographicTargeting: null,
    priceSummary: null,
    isLoading: false,
    isError: false,
  }) as unknown as UnifiedMediaPlanData;

/**
 * Creates a minimal InventoryItem-shaped object.
 * Override any field via the top-level overrides bag (inventoryType, schedules
 * are promoted for convenience; everything else is spread onto the root).
 */
const makeLocation = ({
  inventoryType = "classic",
  schedules = [],
  operations = {},
  detail: detailOverrides = {},
  location: locationOverrides = {},
  performance: performanceOverrides = {},
}: {
  inventoryType?: string;
  schedules?: unknown[];
  operations?: Record<string, unknown>;
  detail?: Record<string, unknown>;
  location?: Record<string, unknown>;
  performance?: Record<string, unknown>;
} = {}) => ({
  detail: {
    name: "Test Billboard",
    referenceId: "REF-001",
    mediaOwnerName: "Owner Co.",
    inventoryType,
    category: "OOH",
    venueType: "Street",
    thumbnail: "https://example.com/thumb.jpg",
    images: [],
    format: "Billboard",
    screens: 1,
    panels: [
      {
        pixelWidth: 1920,
        pixelHeight: 1080,
        physicalWidth: 600,
        physicalHeight: 300,
      },
    ],
    ...detailOverrides,
  },
  location: {
    location: {
      address: "123 Main St",
      state: "California",
      city: "Los Angeles",
      locationCoordinates: {
        coordinates: [{ latitude: 34.05, longitude: -118.25 }],
      },
      ...locationOverrides,
    },
  },
  performance: {
    cpmRate: 5.0,
    estimatedCost: 1000,
    ...performanceOverrides,
  },
  operations: {
    slotDuration: 15,
    startTime: "09:00",
    endTime: "18:00",
    ...operations,
  },
  schedules,
});

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("transformMediaPlanData", () => {
  // ── null / undefined guard ─────────────────────────────────────────────

  describe("null / undefined guard", () => {
    it("returns {} for null", () => {
      expect(transformMediaPlanData(null)).toEqual({});
    });

    it("returns {} for undefined", () => {
      expect(transformMediaPlanData(undefined)).toEqual({});
    });

    it("returns {} when called with no arguments", () => {
      expect(transformMediaPlanData()).toEqual({});
    });
  });

  // ── output shape ──────────────────────────────────────────────────────

  describe("output shape with minimal valid input", () => {
    it("returns all expected top-level keys", () => {
      const result = transformMediaPlanData(minimalData());
      const expectedKeys = [
        "campaignDetails",
        "estimatedPerformanceMetrics",
        "targetingApplied",
        "deliveryBreakdown",
        "statePlanning",
        "cityPlanning",
        "inventoryPlanning",
        "inventoryMapping",
        "inventoryDetails",
        "costingDetails",
        "classicInventory",
        "classicNetworkInventory",
        "digitalInventory",
        "digitalNetworkInventory",
        "cinemaInventory",
        "operationDetails",
        "doohSchedules",
        "doohScheduleSummary",
        "geographyTargeting",
        "geographyTargetingPoiRows",
      ];
      for (const key of expectedKeys) {
        expect(result).toHaveProperty(key);
      }
    });

    it("does not throw with minimal data and no t() function", () => {
      expect(() => transformMediaPlanData(minimalData())).not.toThrow();
    });
  });

  // ── transformCampaignDetails ──────────────────────────────────────────

  describe("transformCampaignDetails", () => {
    it("maps headerInfo string fields", () => {
      const data = {
        ...minimalData(),
        headerInfo: {
          name: "Q4 Campaign",
          id: "camp-123",
          currency: "USD",
          startDate: "2024-01-01",
          endDate: "2024-03-31",
          preparedBy: "Jane Doe",
        },
      } as unknown as UnifiedMediaPlanData;

      const { campaignDetails } = transformMediaPlanData(data);

      expect(campaignDetails!.campaignName).toBe("Q4 Campaign");
      expect(campaignDetails!.campaignId).toBe("camp-123");
      expect(campaignDetails!.currency).toBe("USD");
      expect(campaignDetails!.startDate).toBe("2024-01-01");
      expect(campaignDetails!.endDate).toBe("2024-03-31");
      expect(campaignDetails!.createdBy).toBe("Jane Doe");
    });

    it("maps createdOn, goal, kpi, company, emailAddress, seatId, and dsp from headerInfo", () => {
      const data = {
        ...minimalData(),
        headerInfo: {
          createdAt: "2026-01-15",
          goalType: "Awareness",
          targetValue: 50000,
          companyDetails: { name: "Acme Media", seatId: "SEAT-42" },
          userEmail: "buyer@example.com",
          dsp: "ACTIVATE",
        },
      } as unknown as UnifiedMediaPlanData;

      const { campaignDetails } = transformMediaPlanData(data);

      expect(campaignDetails!.createdOn).toBe("2026-01-15");
      expect(campaignDetails!.goal).toBe("Awareness");
      expect(campaignDetails!.kpi).toBe("50000");
      expect(campaignDetails!.company).toBe("Acme Media");
      expect(campaignDetails!.emailAddress).toBe("buyer@example.com");
      expect(campaignDetails!.seatId).toBe("SEAT-42");
      expect(campaignDetails!.dsp).toBe("ACTIVATE");
    });

    it("defaults dsp to '--' when headerInfo lacks it", () => {
      const data = {
        ...minimalData(),
        headerInfo: {},
      } as unknown as UnifiedMediaPlanData;

      expect(transformMediaPlanData(data).campaignDetails!.dsp).toBe("--");
    });

    it("maps goal to the translated budget goal type label when tCampaigns is provided", () => {
      const data = {
        ...minimalData(),
        headerInfo: { goalType: "IMPRESSIONS" },
      } as unknown as UnifiedMediaPlanData;
      const tCampaigns = vi.fn((key: string) =>
        key === "budget_goal.goal_types.impressions" ? "Impressions" : key,
      );

      const { campaignDetails } = transformMediaPlanData(
        data,
        undefined,
        tCampaigns,
      );

      expect(tCampaigns).toHaveBeenCalledWith(
        "budget_goal.goal_types.impressions",
      );
      expect(campaignDetails!.goal).toBe("Impressions");
    });

    it("falls back to the raw goalType when tCampaigns is not provided", () => {
      const data = {
        ...minimalData(),
        headerInfo: { goalType: "SOV" },
      } as unknown as UnifiedMediaPlanData;

      const { campaignDetails } = transformMediaPlanData(data);

      expect(campaignDetails!.goal).toBe("SOV");
    });

    it("maps dsp to the translated create-campaign dsp label when tCampaigns is provided", () => {
      const data = {
        ...minimalData(),
        headerInfo: { dsp: "ACTIVATE" },
      } as unknown as UnifiedMediaPlanData;
      const tCampaigns = vi.fn((key: string) =>
        key === "create_campaign.form.dsp_active" ? "Activate" : key,
      );

      const { campaignDetails } = transformMediaPlanData(
        data,
        undefined,
        tCampaigns,
      );

      expect(tCampaigns).toHaveBeenCalledWith(
        "create_campaign.form.dsp_active",
      );
      expect(campaignDetails!.dsp).toBe("Activate");
    });

    it("falls back to the raw dsp value when tCampaigns is not provided", () => {
      const data = {
        ...minimalData(),
        headerInfo: { dsp: "ACTIVATE" },
      } as unknown as UnifiedMediaPlanData;

      const { campaignDetails } = transformMediaPlanData(data);

      expect(campaignDetails!.dsp).toBe("ACTIVATE");
    });

    it("defaults createdOn, goal, kpi, company, emailAddress, and seatId to empty when headerInfo lacks them", () => {
      const data = {
        ...minimalData(),
        headerInfo: {},
      } as unknown as UnifiedMediaPlanData;

      const { campaignDetails } = transformMediaPlanData(data);

      expect(campaignDetails!.goal).toBe("");
      expect(campaignDetails!.kpi).toBe("");
      expect(campaignDetails!.company).toBe("");
      expect(campaignDetails!.emailAddress).toBe("");
      expect(campaignDetails!.seatId).toBe("");
    });

    it("derives brand from mediaPlan.brandDetails.name", () => {
      const data = {
        ...minimalData(),
        mediaPlan: { brandDetails: { name: "Acme Corp" }, headerInfo: {} },
      } as unknown as UnifiedMediaPlanData;

      const { campaignDetails } = transformMediaPlanData(data);
      expect(campaignDetails!.brand).toBe("Acme Corp");
    });

    it("maps impressions, reach, frequency, and cpm from performanceMetrics", () => {
      const data = {
        ...minimalData(),
        headerInfo: { impressions: 150000 },
        performanceMetrics: {
          estimatedReach: 100000,
          estimatedFrequency: 2.5,
          avgCpm: 3.75,
        },
      } as unknown as UnifiedMediaPlanData;

      const { campaignDetails } = transformMediaPlanData(data);

      expect(campaignDetails!.oohImpressions).toBe("150000");
      expect(campaignDetails!.uniqueReach).toBe("100000");
      expect(campaignDetails!.averageFrequency).toBe("2.50");
      expect(campaignDetails!.cpm).toBe("3.75");
    });

    it("leaves optional metric fields empty when performanceMetrics is null", () => {
      const data = {
        ...minimalData(),
        performanceMetrics: null,
      } as unknown as UnifiedMediaPlanData;

      const { campaignDetails } = transformMediaPlanData(data);

      expect(campaignDetails!.oohImpressions).toBe("");
      expect(campaignDetails!.uniqueReach).toBe("");
      expect(campaignDetails!.averageFrequency).toBe("");
      expect(campaignDetails!.cpm).toBe("");
    });
  });

  // ── transformStatePlanning ────────────────────────────────────────────

  describe("transformStatePlanning", () => {
    it("returns [] when costSplitByState is empty", () => {
      expect(transformMediaPlanData(minimalData()).statePlanning).toEqual([]);
    });

    it("returns [] when costSplitByState key is absent from root data", () => {
      const data = {
        ...minimalData(),
        costSplitByState: undefined,
      } as unknown as UnifiedMediaPlanData;
      expect(transformMediaPlanData(data).statePlanning).toEqual([]);
    });

    it("maps state rows with correct field values", () => {
      const data = {
        ...minimalData(),
        costSplitByState: [
          {
            name: "California",
            population: 39_000_000,
            totalInventories: 5,
            impressions: 50_000,
            reach: 30_000,
            frequency: 1.5,
            avgCpm: 2.5,
          },
        ],
      } as unknown as UnifiedMediaPlanData;

      const { statePlanning } = transformMediaPlanData(data);

      expect(statePlanning).toHaveLength(1);
      const row = statePlanning![0];
      expect(row.id).toBe("1");
      expect(row.stateName).toBe("California");
      expect(row.inventories).toBe(5);
      expect(row.frequency).toBe("1.50");
      expect(row.cpm).toBe(2.5);
    });
  });

  // ── transformCityPlanning ─────────────────────────────────────────────

  describe("transformCityPlanning", () => {
    it("returns [] when costSplitByCity is empty", () => {
      expect(transformMediaPlanData(minimalData()).cityPlanning).toEqual([]);
    });

    it("maps city rows the same way as state rows", () => {
      const data = {
        ...minimalData(),
        costSplitByCity: [
          {
            name: "Los Angeles",
            population: 4_000_000,
            totalInventories: 3,
            impressions: 20_000,
            reach: 10_000,
            frequency: 2.0,
            avgCpm: 3.0,
          },
        ],
      } as unknown as UnifiedMediaPlanData;

      const { cityPlanning } = transformMediaPlanData(data);

      expect(cityPlanning).toHaveLength(1);
      expect(cityPlanning![0].stateName).toBe("Los Angeles");
      expect(cityPlanning![0].frequency).toBe("2.00");
    });
  });

  // ── transformInventoryPlanning ────────────────────────────────────────

  describe("transformInventoryPlanning", () => {
    it("returns [] when locations is empty", () => {
      expect(transformMediaPlanData(minimalData()).inventoryPlanning).toEqual(
        [],
      );
    });

    it("maps inventory name and referenceId", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [makeLocation()],
        },
      } as unknown as UnifiedMediaPlanData;

      const { inventoryPlanning } = transformMediaPlanData(data);

      expect(inventoryPlanning).toHaveLength(1);
      expect(inventoryPlanning![0].id).toBe("1");
      expect(inventoryPlanning![0].billboardName).toBe("Test Billboard");
      expect(inventoryPlanning![0].referenceId).toBe("REF-001");
      expect(inventoryPlanning![0].ecpm).toBe(5.0);
    });

    it("maps oohImpressions, uniqueReach, and frequency from performance data", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({
              performance: {
                estimatedImpressions: 150000,
                estimatedReach: 100000,
                estimatedFrequency: 3.5,
              },
            }),
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const { inventoryPlanning } = transformMediaPlanData(data);

      expect(inventoryPlanning![0].oohImpressions).toBe("150000");
      expect(inventoryPlanning![0].uniqueReach).toBe("100000");
      expect(inventoryPlanning![0].frequency).toBe(3.5);
    });

    it("falls back to estimatedImpression (singular) when estimatedImpressions is absent", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({
              performance: { estimatedImpression: 50000 },
            }),
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const { inventoryPlanning } = transformMediaPlanData(data);

      expect(inventoryPlanning![0].oohImpressions).toBe("50000");
    });

    it("defaults oohImpressions, uniqueReach, and frequency to 0 when performance data is absent", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [makeLocation()],
        },
      } as unknown as UnifiedMediaPlanData;

      const { inventoryPlanning } = transformMediaPlanData(data);

      expect(inventoryPlanning![0].oohImpressions).toBe("0");
      expect(inventoryPlanning![0].uniqueReach).toBe("0");
      expect(inventoryPlanning![0].frequency).toBe(0);
    });
  });

  // ── transformInventoryMapping ─────────────────────────────────────────

  describe("transformInventoryMapping", () => {
    it("returns [] when inventoriesMapping is empty", () => {
      expect(transformMediaPlanData(minimalData()).inventoryMapping).toEqual(
        [],
      );
    });

    it("maps name, coordinates, state, district, and distance from the inventories-mapping API", () => {
      const data = {
        ...minimalData(),
        inventoriesMapping: [
          {
            name: "Mumbai",
            latitude: "19.12967",
            longitude: "72.85515",
            billboardName: "Leher CHSL|bf2b96e5-909a-465e-b65c-b5345347b721",
            referenceId: "IND-ADO-D-00000-88842",
            distanceMeters: 9452.9,
            stateName: "Maharashtra",
            districtName: "Mumbai Suburban",
          },
        ],
      } as unknown as UnifiedMediaPlanData;

      const { inventoryMapping } = transformMediaPlanData(data);

      expect(inventoryMapping).toHaveLength(1);
      const row = inventoryMapping![0];
      expect(row.name).toBe("Mumbai");
      expect(row.latitude).toBe(19.12967);
      expect(row.longitude).toBe(72.85515);
      expect(row.billboardName).toBe(
        "Leher CHSL|bf2b96e5-909a-465e-b65c-b5345347b721",
      );
      expect(row.referenceId).toBe("IND-ADO-D-00000-88842");
      expect(row.distanceFromCityCenter).toBe(9452.9);
      expect(row.stateName).toBe("Maharashtra");
      expect(row.districtName).toBe("Mumbai Suburban");
    });
  });

  // ── transformInventoryDetails ─────────────────────────────────────────

  describe("transformInventoryDetails", () => {
    it("returns [] when locations is empty", () => {
      expect(transformMediaPlanData(minimalData()).inventoryDetails).toEqual(
        [],
      );
    });

    it("sets type to 'classic' for a classic inventory", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [makeLocation({ inventoryType: "classic" })],
        },
      } as unknown as UnifiedMediaPlanData;

      expect(transformMediaPlanData(data).inventoryDetails![0].type).toBe(
        "classic",
      );
    });

    it("sets type to 'digital' for a digital inventory", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [makeLocation({ inventoryType: "digital" })],
        },
      } as unknown as UnifiedMediaPlanData;

      expect(transformMediaPlanData(data).inventoryDetails![0].type).toBe(
        "digital",
      );
    });

    it("sets type to 'digital network' for a digital network inventory", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [makeLocation({ inventoryType: "digital network" })],
        },
      } as unknown as UnifiedMediaPlanData;

      expect(transformMediaPlanData(data).inventoryDetails![0].type).toBe(
        "digital network",
      );
    });

    it("sets type to 'classic network' for a classic network inventory", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [makeLocation({ inventoryType: "classic network" })],
        },
      } as unknown as UnifiedMediaPlanData;

      expect(transformMediaPlanData(data).inventoryDetails![0].type).toBe(
        "classic network",
      );
    });

    it("sets type to 'mobile' for a mobile inventory", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [makeLocation({ inventoryType: "mobile" })],
        },
      } as unknown as UnifiedMediaPlanData;

      expect(transformMediaPlanData(data).inventoryDetails![0].type).toBe(
        "mobile",
      );
    });

    it("calculates spotsPerHour from schedules for digital inventory", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({
              inventoryType: "digital",
              schedules: [
                { spotsPerHour: 4, spotsPerLoop: 2 },
                { spotsPerHour: 2, spotsPerLoop: 1 },
              ],
            }),
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const row = transformMediaPlanData(data).inventoryDetails![0];
      expect(row.spotsPerHour).toBe(6); // 4 + 2
      expect(row.totalSpots).toBe(3); // 2 + 1
    });
  });

  // ── transformCostingDetails ───────────────────────────────────────────

  describe("transformCostingDetails", () => {
    it("returns zeroed costing fields when no price data is available", () => {
      const { costingDetails } = transformMediaPlanData(minimalData());
      expect(costingDetails!.subTotal).toBe(0);
      expect(costingDetails!.grandTotal).toBe(0);
      expect(costingDetails!.platformFee).toBe(0);
      expect(costingDetails!.customFees).toBeUndefined();
    });

    it("falls back to performanceMetrics.totalCost when priceSummary is null", () => {
      const data = {
        ...minimalData(),
        performanceMetrics: { totalCost: 8000 },
        priceSummary: null,
      } as unknown as UnifiedMediaPlanData;

      const { costingDetails } = transformMediaPlanData(data);
      expect(costingDetails!.subTotal).toBe(8000);
      expect(costingDetails!.grandTotal).toBe(8000);
    });

    it("prefers priceSummary fields over performanceMetrics", () => {
      const data = {
        ...minimalData(),
        performanceMetrics: { totalCost: 9999 },
        priceSummary: {
          discountedMediaCost: 5000,
          proposedPrice: 6000,
          standardFees: 500,
          customFees: [
            {
              name: "Agency Fee",
              effectiveCustomFee: 200,
              isIncludeInMediaPlan: true,
              description: "Agency commission",
            },
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const { costingDetails } = transformMediaPlanData(data);

      expect(costingDetails!.subTotal).toBe(5000);
      expect(costingDetails!.grandTotal).toBe(6000);
      expect(costingDetails!.platformFee).toBe(500);
      expect(costingDetails!.customFees).toHaveLength(1);
      expect(costingDetails!.customFees![0].name).toBe("Agency Fee");
      expect(costingDetails!.customFees![0].amount).toBe(200);
    });
  });

  // ── transformCostingInventoryRows ─────────────────────────────────────

  describe("transformCostingInventoryRows", () => {
    it("returns [] when locations is empty", () => {
      expect(
        transformMediaPlanData(minimalData()).costingInventoryRows,
      ).toEqual([]);
    });

    it("falls back to performance.cpmRate for base/proposed/accepted when no schedule-price data matches", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({
              detail: { id: "inv-1", name: "Board A" },
              location: { city: "Manhattan" },
              performance: { cpmRate: 18.3, estimatedCost: 70000 },
            }),
          ],
        },
        campaignSchedulePrices: [],
      } as unknown as UnifiedMediaPlanData;

      const [row] = transformMediaPlanData(data).costingInventoryRows!;
      expect(row.name).toBe("Board A");
      expect(row.city).toBe("Manhattan");
      expect(row.baseCpm).toBe(18.3);
      expect(row.proposed).toBe(18.3);
      expect(row.accepted).toBe(18.3);
      expect(row.mediaCost).toBe(70000);
    });

    it("weighted-averages multiple schedule-price rows for the same inventoryId, weighted by cpmRate × reach", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({
              detail: { id: "inv-1", name: "Board A" },
              performance: { cpmRate: 10, estimatedCost: 1000 },
            }),
          ],
        },
        campaignSchedulePrices: [
          {
            inventoryId: "inv-1",
            cpmRate: 10,
            proposedRate: 12,
            currentRate: 11,
            reach: 100,
          },
          {
            inventoryId: "inv-1",
            cpmRate: 20,
            proposedRate: 22,
            currentRate: 21,
            reach: 100,
          },
        ],
      } as unknown as UnifiedMediaPlanData;

      const [row] = transformMediaPlanData(data).costingInventoryRows!;
      // weights: 10*100=1000, 20*100=2000 → weighted avg = (10*1000 + 20*2000)/3000
      expect(row.baseCpm).toBeCloseTo((10 * 1000 + 20 * 2000) / 3000, 5);
      expect(row.proposed).toBeCloseTo((12 * 1000 + 22 * 2000) / 3000, 5);
      expect(row.accepted).toBeCloseTo((11 * 1000 + 21 * 2000) / 3000, 5);
    });

    it("pro-rates standardFees + included customFees across rows by media-cost share", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({
              detail: { id: "inv-1", name: "Board A" },
              performance: { estimatedCost: 3000 },
            }),
            makeLocation({
              detail: { id: "inv-2", name: "Board B" },
              performance: { estimatedCost: 1000 },
            }),
          ],
        },
        campaignSchedulePrices: [],
        priceSummary: {
          standardFees: 400,
          customFees: [
            {
              name: "Included",
              effectiveCustomFee: 100,
              isIncludeInMediaPlan: true,
            },
            {
              name: "Excluded",
              effectiveCustomFee: 500,
              isIncludeInMediaPlan: false,
            },
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const rows = transformMediaPlanData(data).costingInventoryRows!;
      // total fees = 400 + 100 = 500, split 75/25 by media cost (3000 vs 1000 of 4000)
      expect(rows[0].feeShare).toBeCloseTo(375, 5);
      expect(rows[1].feeShare).toBeCloseTo(125, 5);
      expect(rows[0].total).toBeCloseTo(3375, 5);
      expect(rows[1].total).toBeCloseTo(1125, 5);
    });
  });

  // ── transformClassicInventory ─────────────────────────────────────────

  describe("transformClassicInventory", () => {
    it("returns [] when no locations are present", () => {
      expect(transformMediaPlanData(minimalData()).classicInventory).toEqual(
        [],
      );
    });

    it("filters only classic (non-network) inventories", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({ inventoryType: "classic" }),
            makeLocation({ inventoryType: "classic network" }),
            makeLocation({ inventoryType: "digital" }),
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const { classicInventory } = transformMediaPlanData(data);
      expect(classicInventory).toHaveLength(1);
      expect(classicInventory![0].mediaCost).toBe(1000);
    });
  });

  // ── transformClassicNetworkInventory ──────────────────────────────────

  describe("transformClassicNetworkInventory", () => {
    it("filters only classic network inventories", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({ inventoryType: "classic" }),
            makeLocation({ inventoryType: "classic network" }),
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const { classicNetworkInventory } = transformMediaPlanData(data);
      expect(classicNetworkInventory).toHaveLength(1);
      expect(classicNetworkInventory![0].billboardName).toBe("Test Billboard");
    });
  });

  // ── transformDigitalInventory ─────────────────────────────────────────

  describe("transformDigitalInventory", () => {
    it("filters only digital (non-network) inventories", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({ inventoryType: "digital" }),
            makeLocation({ inventoryType: "digital network" }),
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const { digitalInventory } = transformMediaPlanData(data);
      expect(digitalInventory).toHaveLength(1);
      expect(digitalInventory![0].contentManagementFee).toBe(1000);
    });
  });

  // ── transformDigitalNetworkInventory ─────────────────────────────────

  describe("transformDigitalNetworkInventory", () => {
    it("filters only digital network inventories", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({ inventoryType: "digital" }),
            makeLocation({
              inventoryType: "digital network",
              performance: { estimatedCost: 2500, cpmRate: 4.0 },
            }),
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const { digitalNetworkInventory } = transformMediaPlanData(data);
      expect(digitalNetworkInventory).toHaveLength(1);
      expect(digitalNetworkInventory![0].contentManagementFee).toBe(2500);
    });
  });

  // ── transformOperationDetails ─────────────────────────────────────────

  describe("transformOperationDetails", () => {
    it("returns {} when no locations are present", () => {
      expect(transformMediaPlanData(minimalData()).operationDetails).toEqual(
        {},
      );
    });

    it("returns {} when locations have no schedules", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [makeLocation({ schedules: [] })],
        },
      } as unknown as UnifiedMediaPlanData;

      expect(transformMediaPlanData(data).operationDetails).toEqual({});
    });

    it("builds a classic schedule row with correct operation days", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({
              inventoryType: "classic",
              schedules: [
                {
                  startDate: "2024-01-01",
                  endDate: "2024-01-31",
                  bookingMatrix: {},
                  spotsPerHour: 0,
                  spotsPerLoop: 0,
                  duration: 30,
                },
              ],
            }),
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const { operationDetails } = transformMediaPlanData(data);

      expect(operationDetails!.classic).toHaveLength(1);
      expect(operationDetails!.classic![0].inventoryName).toBe(
        "Test Billboard",
      );
      // Jan 1 to Jan 31 inclusive of both start and end dates = 31 days
      expect(operationDetails!.classic![0].operationDays).toBe(31);
      expect(operationDetails!.digital).toBeUndefined();
    });

    it("falls back to schedule.duration when start/end dates are absent", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({
              inventoryType: "classic",
              schedules: [
                {
                  startDate: "",
                  endDate: "",
                  bookingMatrix: {},
                  spotsPerHour: 0,
                  spotsPerLoop: 0,
                  duration: 14,
                },
              ],
            }),
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const { operationDetails } = transformMediaPlanData(data);
      expect(operationDetails!.classic![0].operationDays).toBe(14);
    });

    it("builds a digital schedule row with booking matrix hours", () => {
      const bookingMatrix = {
        "2024-01-01": [9, 10, 11],
        "2024-01-02": [9, 10],
      };
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({
              inventoryType: "digital",
              schedules: [
                {
                  startDate: "2024-01-01",
                  endDate: "2024-01-31",
                  bookingMatrix,
                  spotsPerHour: 4,
                  spotsPerLoop: 2,
                  duration: 30,
                },
              ],
            }),
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const { operationDetails } = transformMediaPlanData(data);

      expect(operationDetails!.digital).toHaveLength(1);
      const row = operationDetails!.digital![0];
      // operationHours = 3 + 2 = 5; totalSpots = 5 * 4 = 20
      expect(row.operationHours).toBe(5);
      expect(row.totalSpots).toBe(20);
      // min hour = 9 → "09:00", max hour = 11 → "11:00"
      expect(row.startTime).toBe("09:00");
      expect(row.endTime).toBe("11:00");
    });
  });

  // ── transformDOOHSchedules ────────────────────────────────────────────

  describe("transformDOOHSchedules", () => {
    it("returns [] when no locations are present", () => {
      expect(transformMediaPlanData(minimalData()).doohSchedules).toEqual([]);
    });

    it("returns [] when no digital locations are present", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [makeLocation({ inventoryType: "classic" })],
        },
      } as unknown as UnifiedMediaPlanData;

      expect(transformMediaPlanData(data).doohSchedules).toEqual([]);
    });

    it("builds a DOOH schedule row for a digital inventory with schedules", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({
              inventoryType: "digital",
              operations: {
                slotDuration: 15,
                startTime: "09:00",
                endTime: "18:00",
                operatingTimes: {
                  MONDAY: [{ start: "09:00:00", end: "18:00:00" }],
                  TUESDAY: [{ start: "09:00:00", end: "18:00:00" }],
                },
              },
              schedules: [
                {
                  startDate: "2024-01-01",
                  endDate: "2024-01-31",
                  bookingMatrix: {},
                  spotsPerHour: 4,
                  spotsPerLoop: 2,
                  duration: 30,
                },
              ],
            }),
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const { doohSchedules } = transformMediaPlanData(data);

      expect(doohSchedules).toHaveLength(1);
      const row = doohSchedules![0];
      expect(row.billboardName).toBe("Test Billboard");
      expect(row.duration).toBe(30); // ceil((Jan31-Jan1)/dayMs) = 30
      // Days from operatingTimes
      expect(row.mon).toBe(true);
      expect(row.tue).toBe(true);
      expect(row.wed).toBe(false);
      expect(row.sat).toBe(false);
      // Operation hours from operatingTimes
      expect(row.operationHours).toBe("09:00 - 18:00");
    });
  });

  // ── transformDOOHScheduleSummary ──────────────────────────────────────

  describe("transformDOOHScheduleSummary", () => {
    it("maps campaignName and calculates duration from header dates", () => {
      const data = {
        ...minimalData(),
        headerInfo: {
          name: "Test Campaign",
          startDate: "2024-01-01",
          endDate: "2024-03-31",
        },
      } as unknown as UnifiedMediaPlanData;

      const { doohScheduleSummary } = transformMediaPlanData(data);

      expect(doohScheduleSummary!.campaignName).toBe("Test Campaign");
      // 2024 is a leap year: Jan(31) + Feb(29) + Mar(30) = 90 days
      expect(doohScheduleSummary!.duration).toBe(90);
    });

    it("formats impressions and adPlays via formatNumber", () => {
      const data = {
        ...minimalData(),
        headerInfo: {
          name: "Impressions Campaign",
          startDate: "2024-01-01",
          endDate: "2024-01-31",
          impressions: 100_000,
        },
        performanceMetrics: { estimatedAdPlays: 5000 },
      } as unknown as UnifiedMediaPlanData;

      const { doohScheduleSummary } = transformMediaPlanData(data);

      expect(doohScheduleSummary!.totalImpressions).toBe("100000");
      expect(doohScheduleSummary!.totalAdPlays).toBe("5000");
    });
  });

  // ── transformDOOHPanels ────────────────────────────────────────────────

  describe("transformDOOHPanels", () => {
    const fullWeekBookingMatrix = (dates: string[]) =>
      Object.fromEntries(
        dates.map((date) => [date, Array.from({ length: 24 }, (_, h) => h)]),
      );

    it("only includes digital inventories", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({
              inventoryType: "classic",
              schedules: [
                {
                  name: "Schedule 1",
                  startDate: "2026-04-01",
                  endDate: "2026-04-02",
                  scheduleDays: ["WEDNESDAY", "THURSDAY"],
                  bookingMatrix: fullWeekBookingMatrix([
                    "2026-04-01",
                    "2026-04-02",
                  ]),
                  spotsPerLoop: 1,
                  spotsPerHour: 1,
                  sov: 100,
                },
              ],
            }),
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      expect(transformMediaPlanData(data).doohPanels).toEqual([]);
    });

    it("classifies a fully-booked (all hours, all days) schedule as 24/7", () => {
      const dates = ["2026-04-01", "2026-04-02", "2026-04-03"];
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({
              inventoryType: "digital",
              detail: { referenceId: "INV-1", name: "Board A" },
              schedules: [
                {
                  name: "Schedule 1",
                  startDate: "2026-04-01",
                  endDate: "2026-04-03",
                  scheduleDays: [
                    "MONDAY",
                    "TUESDAY",
                    "WEDNESDAY",
                    "THURSDAY",
                    "FRIDAY",
                    "SATURDAY",
                    "SUNDAY",
                  ],
                  bookingMatrix: fullWeekBookingMatrix(dates),
                  spotsPerLoop: 1,
                  spotsPerHour: 1,
                  sov: 100,
                },
              ],
            }),
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const [panel] = transformMediaPlanData(data).doohPanels!;
      expect(panel.pattern).toBe("24/7");
      expect(panel.days).toBe(3);
      expect(panel.daysPerWeek).toBe(7);
      expect(panel.segments[0].activeDates).toEqual(dates);
    });

    it("uses inventory-level Op Hrs for the panel while segments keep their own active-hour ranges, and day-weight-averages cadence metrics", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({
              inventoryType: "digital",
              detail: { referenceId: "INV-1", name: "Board A" },
              schedules: [
                {
                  name: "Morning Rush",
                  startDate: "2026-04-01",
                  endDate: "2026-04-10", // 10 days
                  scheduleDays: ["MONDAY", "TUESDAY"],
                  bookingMatrix: { "2026-04-01": [6, 7, 8, 9] },
                  spotsPerLoop: 1,
                  spotsPerHour: 30,
                  sov: 20,
                },
                {
                  name: "Evening Peak",
                  startDate: "2026-04-11",
                  endDate: "2026-04-20", // 10 days
                  scheduleDays: ["WEDNESDAY"],
                  bookingMatrix: { "2026-04-11": [17, 18, 19] },
                  spotsPerLoop: 2,
                  spotsPerHour: 60,
                  sov: 40,
                },
              ],
            }),
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const [panel] = transformMediaPlanData(data).doohPanels!;
      // Panel opHoursLabel comes from the inventory's own operations
      // (item.operations), not derived from segments — default test
      // operations are 09:00–18:00.
      expect(panel.opHoursLabel).toBe("09:00 AM–06:00 PM");
      expect(panel.segments[0].opHoursLabel).toBe("06:00 AM–10:00 AM");
      expect(panel.segments[1].opHoursLabel).toBe("05:00 PM–08:00 PM");
      // Equal day-weights (10 + 10) → plain average
      expect(panel.spotsPerLoop).toBeCloseTo(1.5, 5);
      expect(panel.spotsPerHour).toBeCloseTo(45, 5);
      expect(panel.sov).toBeCloseTo(30, 5);
      // Union of MONDAY/TUESDAY/WEDNESDAY = 3 distinct weekdays
      expect(panel.daysPerWeek).toBe(3);
    });
  });

  // ── transformDOOHRollupHeatmap ──────────────────────────────────────────

  describe("transformDOOHRollupHeatmap", () => {
    it("counts a schedule once per (weekday, hour) even across many weeks of the same weekday", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({
              inventoryType: "digital",
              schedules: [
                {
                  name: "Schedule 1",
                  startDate: "2026-04-01",
                  endDate: "2026-04-30",
                  scheduleDays: ["WEDNESDAY"],
                  // Every Wednesday in April at hour 17 — should count once,
                  // not once per Wednesday.
                  bookingMatrix: {
                    "2026-04-01": [17],
                    "2026-04-08": [17],
                    "2026-04-15": [17],
                    "2026-04-22": [17],
                    "2026-04-29": [17],
                  },
                  spotsPerLoop: 1,
                  spotsPerHour: 1,
                  sov: 100,
                },
              ],
            }),
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const { doohRollupHeatmap } = transformMediaPlanData(data);
      const wedRow = doohRollupHeatmap!.rows.find((r) => r.day === "Wed")!;
      expect(wedRow.cells[17].count).toBe(1);
      expect(doohRollupHeatmap!.maxCount).toBe(1);
      expect(doohRollupHeatmap!.totalSchedules).toBe(1);
    });

    it("sums distinct schedules across multiple digital panels for the same cell, and counts distinct patterns", () => {
      const commonSchedule = (hour: number) => ({
        name: "Schedule 1",
        startDate: "2026-04-01",
        endDate: "2026-04-01",
        scheduleDays: ["WEDNESDAY"],
        bookingMatrix: { "2026-04-01": [hour] },
        spotsPerLoop: 1,
        spotsPerHour: 1,
        sov: 100,
      });
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({
              inventoryType: "digital",
              detail: { referenceId: "INV-1" },
              schedules: [commonSchedule(9)],
            }),
            makeLocation({
              inventoryType: "digital",
              detail: { referenceId: "INV-2" },
              schedules: [commonSchedule(9)],
            }),
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const { doohRollupHeatmap } = transformMediaPlanData(data);
      const wedRow = doohRollupHeatmap!.rows.find((r) => r.day === "Wed")!;
      expect(wedRow.cells[9].count).toBe(2);
      expect(doohRollupHeatmap!.totalSchedules).toBe(2);
      // Both panels' fully-booked-hour schedule classifies the same way →
      // 1 distinct pattern.
      expect(doohRollupHeatmap!.totalPatterns).toBe(1);
    });
  });

  // ── transformGeographyTargeting ─────────────────────────────────────────

  describe("transformGeographyTargeting", () => {
    it("returns [] when there are no selected inventory locations", () => {
      expect(transformMediaPlanData(minimalData()).geographyTargeting).toEqual(
        [],
      );
    });

    it("builds a Country > State > City tree with real per-node sums, sorted by impressions desc", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({
              location: {
                country: "United States",
                state: "New York",
                city: "Manhattan",
              },
              performance: {
                estimatedImpressions: 3_500_000,
                estimatedReach: 1_600_000,
                estimatedCost: 203_265,
              },
            }),
            makeLocation({
              location: {
                country: "United States",
                state: "New York",
                city: "Union Square",
              },
              performance: {
                estimatedImpressions: 1_900_000,
                estimatedReach: 877_500,
                estimatedCost: 119_072,
              },
            }),
            makeLocation({
              location: {
                country: "United States",
                state: "Illinois",
                city: "Downtown",
              },
              performance: {
                estimatedImpressions: 2_300_000,
                estimatedReach: 661_100,
                estimatedCost: 191_360,
              },
            }),
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const rows = transformMediaPlanData(data).geographyTargeting!;

      // Country row is first, aggregating all 3 inventories.
      const country = rows[0];
      expect(country.level).toBe("country");
      expect(country.name).toBe("United States");
      expect(country.inventories).toBe(3);
      expect(country.impressions).toBe(7_700_000);
      expect(country.reach).toBe(3_138_600);

      // New York (5.4M impressions) sorts before Illinois (2.3M).
      const stateNames = rows
        .filter((r) => r.level === "state")
        .map((r) => r.name);
      expect(stateNames).toEqual(["New York", "Illinois"]);

      const newYork = rows.find(
        (r) => r.level === "state" && r.name === "New York",
      )!;
      expect(newYork.inventories).toBe(2);
      expect(newYork.impressions).toBe(5_400_000);
      expect(newYork.depth).toBe(1);

      // Manhattan (3.5M) sorts before Union Square (1.9M) within New York.
      const cityNames = rows
        .filter((r) => r.level === "city")
        .map((r) => r.name);
      expect(cityNames).toEqual(["Manhattan", "Union Square", "Downtown"]);

      const manhattan = rows.find((r) => r.name === "Manhattan")!;
      expect(manhattan.depth).toBe(2);
      expect(manhattan.impressions).toBe(3_500_000);
      expect(manhattan.reach).toBe(1_600_000);
      // eCPM = (cost / impressions) * 1000
      expect(manhattan.ecpm).toBeCloseTo((203_265 / 3_500_000) * 1000, 2);
      // frequency = impressions / reach
      expect(manhattan.frequency).toBeCloseTo(3_500_000 / 1_600_000, 5);
    });

    it("buckets missing country/state/city under the not_available fallback", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({
              location: { country: "", state: "", city: "" },
              performance: { estimatedImpressions: 1000, estimatedReach: 500 },
            }),
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const rows = transformMediaPlanData(
        data,
        undefined,
        (key) => key,
      ).geographyTargeting!;
      expect(rows[0].name).toBe("media_plan.geographic_plan.not_available");
    });
  });

  // ── Cinema as a real channel ────────────────────────────────────────────

  describe("cinema inventory", () => {
    const cinemaData = () =>
      ({
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({ inventoryType: "classic" }),
            makeLocation({
              inventoryType: "cinema",
              detail: {
                id: "cinema-1",
                name: "Cinema Screen",
                inventoryType: "cinema",
                cinemaFields: {
                  operator: "GV Cinemas",
                  cinemaName: "GV Plaza",
                  hallName: "Hall 3",
                  showtimeWindows: [
                    { label: "Matinee", start: "12:00", end: "15:00" },
                    { label: "Evening", start: "18:00", end: "22:00" },
                  ],
                  genres: ["Action", "Drama"],
                  ratings: ["PG", "PG-13"],
                },
              },
              performance: {
                estimatedImpression: 50000,
                cpmRate: 8,
                estimatedCost: 400,
              },
              schedules: [
                {
                  startDate: "2026-04-01",
                  endDate: "2026-04-30",
                  bookingMatrix: { "2026-04-01": [9, 10] },
                  spotsPerHour: 4,
                  spotsPerLoop: 2,
                  duration: 30,
                },
              ],
            }),
          ],
        },
      }) as unknown as UnifiedMediaPlanData;

    it("keeps cinema in the generic tables (inventoryDetails, costingInventoryRows, geographyTargeting)", () => {
      const result = transformMediaPlanData(cinemaData());

      expect(result.inventoryDetails).toHaveLength(2);
      expect(
        result.inventoryDetails!.some(
          (row) => row.billboardName === "Cinema Screen",
        ),
      ).toBe(true);
      expect(
        result.inventoryDetails!.find(
          (row) => row.billboardName === "Cinema Screen",
        )!.type,
      ).toBe("cinema");
      expect(
        result.costingInventoryRows!.some(
          (row) => row.name === "Cinema Screen",
        ),
      ).toBe(true);
      expect(
        result
          .geographyTargeting!.filter((row) => row.level === "city")
          .reduce((sum, row) => sum + row.inventories, 0),
      ).toBe(2);
    });

    it("keeps cinema OUT of classic/digital/DOOH-specific tables", () => {
      const result = transformMediaPlanData(cinemaData());

      expect(
        result.classicInventory!.some((r) => r.billboardName === "Cinema Screen"),
      ).toBe(false);
      expect(
        result.digitalInventory!.some((r) => r.billboardName === "Cinema Screen"),
      ).toBe(false);
      expect(
        (result.doohPanels || []).some(
          (r) => r.inventoryName === "Cinema Screen",
        ),
      ).toBe(false);
      // Cinema has no classic/digital/mobile operation schedule rows.
      expect(
        (result.operationDetails?.classic || []).some(
          (r) => r.inventoryName === "Cinema Screen",
        ),
      ).toBe(false);
    });

    it("builds a dedicated cinemaInventory table from detail.cinemaFields", () => {
      const result = transformMediaPlanData(cinemaData());

      expect(result.cinemaInventory).toHaveLength(1);
      const row = result.cinemaInventory![0];
      expect(row.name).toBe("Cinema Screen");
      expect(row.operator).toBe("GV Cinemas");
      expect(row.cinemaName).toBe("GV Plaza");
      expect(row.hall).toBe("Hall 3");
      expect(row.showtimeWindows).toBe("Matinee, Evening");
      expect(row.genres).toBe("Action, Drama");
      expect(row.ratings).toBe("PG, PG-13");
      expect(row.impressions).toBe(50000);
      expect(row.cpm).toBe(8);
      expect(row.mediaCost).toBe(400);
    });

    it("degrades to blank strings when cinemaFields is absent", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({
              inventoryType: "cinema",
              detail: { id: "c2", name: "Bare Cinema", inventoryType: "cinema" },
            }),
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const result = transformMediaPlanData(data);
      expect(result.cinemaInventory).toHaveLength(1);
      const row = result.cinemaInventory![0];
      expect(row.operator).toBe("");
      expect(row.cinemaName).toBe("");
      expect(row.showtimeWindows).toBe("");
      expect(row.genres).toBe("");
      expect(row.ratings).toBe("");
    });

    it("returns an empty cinemaInventory when no cinema inventory is present", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [makeLocation({ inventoryType: "classic" })],
        },
      } as unknown as UnifiedMediaPlanData;

      expect(transformMediaPlanData(data).cinemaInventory).toEqual([]);
    });
  });

  // ── transformCampaignDetails: new Excel Plan-sheet-only fields ─────────

  describe("transformCampaignDetails — Excel Plan-sheet fields", () => {
    it("maps status via the campaignsList.status i18n key", () => {
      const data = {
        ...minimalData(),
        headerInfo: { status: "DRAFT" },
      } as unknown as UnifiedMediaPlanData;
      const tCampaigns = vi.fn((key: string) =>
        key === "campaignsList.status.DRAFT" ? "Draft" : key,
      );

      const { campaignDetails } = transformMediaPlanData(
        data,
        undefined,
        tCampaigns,
      );
      expect(campaignDetails!.status).toBe("Draft");
    });

    it("maps durationLabel, budget, brandCategory, and agency from real fields", () => {
      const data = {
        ...minimalData(),
        headerInfo: { duration: 91, budget: 50000 },
        mediaPlan: { brandDetails: { category: "Retail" }, headerInfo: {} },
        agency: { id: "a1", name: "Acme Agency" },
      } as unknown as UnifiedMediaPlanData;

      const { campaignDetails } = transformMediaPlanData(data);
      expect(campaignDetails!.durationLabel).toBe("91 days");
      expect(campaignDetails!.budget).toBe(50000);
      expect(campaignDetails!.brandCategory).toBe("Retail");
      expect(campaignDetails!.agency).toBe("Acme Agency");
    });

    it("joins data.brand.categories comma-separated, preferring it over the legacy mediaPlan.brandDetails.category", () => {
      const data = {
        ...minimalData(),
        mediaPlan: { brandDetails: { category: "Retail" }, headerInfo: {} },
        brand: {
          id: "b1",
          name: "B&Q Stores",
          categories: [
            { id: "c1", name: "Pop Culture", fullPath: "Pop Culture", tier: 1 },
            {
              id: "c2",
              name: "Celebrity Homes",
              fullPath: "Pop Culture > Celebrity Homes",
              tier: 2,
            },
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      const { campaignDetails } = transformMediaPlanData(data);
      expect(campaignDetails!.brand).toBe("B&Q Stores");
      expect(campaignDetails!.brandCategory).toBe(
        "Pop Culture, Celebrity Homes",
      );
    });

    it("includes cinema in channelsLabel and formats all channels", () => {
      const data = {
        ...minimalData(),
        mediaChannels: ["DIGITAL_OOH", "CINEMA"],
      } as unknown as UnifiedMediaPlanData;

      const { campaignDetails } = transformMediaPlanData(data);
      expect(campaignDetails!.channelsLabel).toBe("Digital OOH, Cinema");
    });

    it("maps clientTypeLabel for DIRECT_ADVERTISER and AGENCY", () => {
      const tCampaigns = vi.fn((key: string) => {
        if (key.endsWith("clientTypeDirect")) return "Direct";
        if (key.endsWith("clientTypeAgency")) return "Agency";
        return key;
      });

      const direct = transformMediaPlanData(
        {
          ...minimalData(),
          clientType: "DIRECT_ADVERTISER",
        } as unknown as UnifiedMediaPlanData,
        undefined,
        tCampaigns,
      );
      const agency = transformMediaPlanData(
        {
          ...minimalData(),
          clientType: "AGENCY",
        } as unknown as UnifiedMediaPlanData,
        undefined,
        tCampaigns,
      );

      expect(direct.campaignDetails!.clientTypeLabel).toBe("Direct");
      expect(agency.campaignDetails!.clientTypeLabel).toBe("Agency");
    });

    it("builds goalLabel from targetValue and the goal's noun", () => {
      const data = {
        ...minimalData(),
        headerInfo: { goalType: "REACH", targetValue: 4_200_000 },
      } as unknown as UnifiedMediaPlanData;

      const { campaignDetails } = transformMediaPlanData(data);
      expect(campaignDetails!.goalLabel).toBe("4200000 unique reach");
    });
  });

  // ── transformEstimatedPerformanceMetrics ───────────────────────────────

  describe("transformEstimatedPerformanceMetrics", () => {
    it("maps performanceMetrics fields and geography/channel counts", () => {
      const data = {
        ...minimalData(),
        performanceMetrics: {
          estimatedImpression: 1_000_000,
          estimatedReach: 400_000,
          estimatedFrequency: 2.5,
          estimatedAdPlays: 5000,
          avgCpm: 12.5,
          avgECpm: 10,
          sov: 80,
          totalCost: 20000,
          totalInventories: 5,
        },
        geographySummary: { cityCount: 3, countryCount: 1, poiCount: 2 },
        channelCount: 2,
      } as unknown as UnifiedMediaPlanData;

      const { estimatedPerformanceMetrics: epm } = transformMediaPlanData(data);
      expect(epm!.totalImpressions).toBe(1_000_000);
      expect(epm!.estimatedReach).toBe(400_000);
      expect(epm!.avgFrequency).toBe(2.5);
      expect(epm!.cities).toBe(3);
      expect(epm!.channels).toBe(2);
    });

    it("relabels Avg CPM as Avg CPS for SOV/ADPLAYS goals", () => {
      const data = {
        ...minimalData(),
        headerInfo: { goalType: "SOV" },
      } as unknown as UnifiedMediaPlanData;
      const tCampaigns = vi.fn((key: string) =>
        key.endsWith("avgCps") ? "Avg CPS" : key,
      );

      const { estimatedPerformanceMetrics: epm } = transformMediaPlanData(
        data,
        undefined,
        tCampaigns,
      );
      expect(epm!.avgCpmLabel).toBe("Avg CPS");
    });
  });

  // ── transformTargetingApplied ───────────────────────────────────────────

  describe("transformTargetingApplied", () => {
    it("returns {} when targeting is absent", () => {
      expect(transformMediaPlanData(minimalData()).targetingApplied).toEqual(
        {},
      );
    });

    it("joins demographic fields and keeps all venueEnvironments", () => {
      const data = {
        ...minimalData(),
        targeting: {
          demographics: {
            age: ["18-24"],
            gender: ["Male"],
            income: ["High"],
            interests: ["Sports"],
            behavior: ["Commuter", "Tourists"],
          },
          geofencing: { geometries: [], locations: [] },
          signals: ["Weather"],
          venueTypes: { digitalOoh: ["Mall"], classicOoh: ["Cinema Lobby"] },
        },
        geographySummary: { cityCount: 0, countryCount: 0, poiCount: 5 },
      } as unknown as UnifiedMediaPlanData;

      const { targetingApplied } = transformMediaPlanData(data);
      expect(targetingApplied!.demographics).toBe("18-24, Male");
      expect(targetingApplied!.income).toBe("High");
      expect(targetingApplied!.interests).toBe("Sports");
      expect(targetingApplied!.venueEnvironments).toBe("Mall, Cinema Lobby");
      expect(targetingApplied!.behaviour).toBe("Commuter, Tourists");
      expect(targetingApplied!.signals).toBe("Weather");
      expect(targetingApplied!.geography).toBe("5 POIs");
    });

    it("formats age with dashes and title-cases income/venueEnvironments, stripping underscores", () => {
      const data = {
        ...minimalData(),
        targeting: {
          demographics: {
            age: ["35_44", "45_54"],
            gender: ["Female"],
            income: ["lower_middle"],
            interests: [],
            behavior: [],
          },
          geofencing: { geometries: [], locations: [] },
          signals: [],
          venueTypes: {
            digitalOoh: ["shopping_malls"],
            classicOoh: ["transit_mrt"],
          },
        },
        geographySummary: { cityCount: 0, countryCount: 0, poiCount: 0 },
      } as unknown as UnifiedMediaPlanData;

      const { targetingApplied } = transformMediaPlanData(data);
      expect(targetingApplied!.demographics).toBe("35-44, 45-54, Female");
      expect(targetingApplied!.income).toBe("Lower Middle");
      expect(targetingApplied!.venueEnvironments).toBe(
        "Shopping Malls, Transit Mrt",
      );
    });
  });

  // ── transformDeliveryBreakdown ──────────────────────────────────────────

  describe("transformDeliveryBreakdown", () => {
    it("returns [] for an invalid date range", () => {
      expect(transformMediaPlanData(minimalData()).deliveryBreakdown).toEqual(
        [],
      );
    });

    it("builds bins summing to the campaign totals", () => {
      const data = {
        ...minimalData(),
        headerInfo: { startDate: "2026-04-01", endDate: "2026-04-14" },
        performanceMetrics: {
          estimatedImpression: 140_000,
          estimatedReach: 70_000,
        },
      } as unknown as UnifiedMediaPlanData;

      const { deliveryBreakdown } = transformMediaPlanData(data);
      expect(deliveryBreakdown!.length).toBeGreaterThan(0);
      const totalImpressions = deliveryBreakdown!.reduce(
        (sum, bin) => sum + bin.impressions,
        0,
      );
      expect(totalImpressions).toBe(140_000);
    });
  });

  // ── transformGeographyPoiRows (Excel Geography Targeting sheet only) ──

  describe("geographyTargetingPoiRows", () => {
    it("returns [] when the campaign has no geofencing zones", () => {
      expect(
        transformMediaPlanData(minimalData()).geographyTargetingPoiRows,
      ).toEqual([]);
    });

    it("matches a POI zone to its nearest inventory and nests it under the right city", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({
              detail: { id: "inv-1", name: "Board A", category: "Premium" },
              location: {
                country: "United States",
                state: "California",
                city: "Los Angeles",
                locationCoordinates: {
                  coordinates: [{ latitude: 34.05, longitude: -118.25 }],
                },
              },
              performance: {
                estimatedImpressions: 100_000,
                estimatedReach: 40_000,
                estimatedCost: 1_800,
                sov: 100,
              },
            }),
          ],
        },
        targeting: {
          demographics: { age: [], gender: [], income: [], interests: [] },
          geofencing: {
            geometries: [],
            locations: [
              {
                id: "poi-1",
                lat: 34.05,
                lng: -118.25,
                name: "Downtown Core",
                address: "",
                radius: 500,
                included: true,
                isShape: true,
              },
            ],
          },
          signals: [],
        },
      } as unknown as UnifiedMediaPlanData;

      const result = transformMediaPlanData(data);
      const cityRow = result.geographyTargeting!.find(
        (row) => row.level === "city",
      )!;
      expect(result.geographyTargetingPoiRows).toHaveLength(1);
      const [poiRow] = result.geographyTargetingPoiRows!;
      expect(poiRow.parentCityId).toBe(cityRow.id);
      expect(poiRow.name).toContain("Downtown Core");
      expect(poiRow.name).toContain("Board A");
      expect(poiRow.name).toContain("Include");
      expect(poiRow.impressions).toBe(100_000);
    });

    it("omits a zone with no inventory within its radius", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [makeLocation()],
        },
        targeting: {
          demographics: { age: [], gender: [], income: [], interests: [] },
          geofencing: {
            geometries: [],
            locations: [
              {
                id: "poi-far",
                lat: 10,
                lng: 10,
                name: "Far Away Zone",
                address: "",
                radius: 100,
                included: true,
                isShape: true,
              },
            ],
          },
          signals: [],
        },
      } as unknown as UnifiedMediaPlanData;

      expect(transformMediaPlanData(data).geographyTargetingPoiRows).toEqual(
        [],
      );
    });
  });

  // ── transformInventoryDetails: schedulePattern ─────────────────────────

  describe("transformInventoryDetails — schedulePattern", () => {
    it("defaults schedulePattern to '--' when there are no schedules", () => {
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [makeLocation({ schedules: [] })],
        },
      } as unknown as UnifiedMediaPlanData;

      expect(
        transformMediaPlanData(data).inventoryDetails![0].schedulePattern,
      ).toBe("--");
    });

    it("classifies a fully-booked schedule as 24/7", () => {
      const dates = ["2026-04-01", "2026-04-02"];
      const bookingMatrix = Object.fromEntries(
        dates.map((date) => [date, Array.from({ length: 24 }, (_, h) => h)]),
      );
      const data = {
        ...minimalData(),
        selectedInventory: {
          summaryStatistics: {},
          locations: [
            makeLocation({
              inventoryType: "classic",
              schedules: [
                {
                  name: "Schedule 1",
                  startDate: "2026-04-01",
                  endDate: "2026-04-02",
                  scheduleDays: [
                    "MONDAY",
                    "TUESDAY",
                    "WEDNESDAY",
                    "THURSDAY",
                    "FRIDAY",
                    "SATURDAY",
                    "SUNDAY",
                  ],
                  bookingMatrix,
                  spotsPerLoop: 1,
                  spotsPerHour: 1,
                  sov: 100,
                },
              ],
            }),
          ],
        },
      } as unknown as UnifiedMediaPlanData;

      expect(
        transformMediaPlanData(data).inventoryDetails![0].schedulePattern,
      ).toBe("24/7");
    });
  });
});
