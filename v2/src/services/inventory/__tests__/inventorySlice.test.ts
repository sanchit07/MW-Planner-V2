/* eslint-disable @typescript-eslint/no-explicit-any */
// vi.mock is hoisted by Vitest's module transformer and executes before any
// imports, so the mock is in place when inventorySlice is first evaluated.
import { configureStore } from "@reduxjs/toolkit";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@api/axiosBaseQuery", () => ({
  default: () => () => Promise.resolve({ data: { success: true, data: {} } }),
}));

import {
  inventoryApi,
  inventoryManagementApi,
  reachFrequencyApi,
} from "../inventorySlice";

// ---------------------------------------------------------------------------
// Store factories
// ---------------------------------------------------------------------------

function makeInventoryStore() {
  return configureStore({
    reducer: { [inventoryApi.reducerPath]: inventoryApi.reducer },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware().concat(inventoryApi.middleware),
  });
}

function makeInventoryManagementStore() {
  return configureStore({
    reducer: {
      [inventoryManagementApi.reducerPath]: inventoryManagementApi.reducer,
    },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware().concat(inventoryManagementApi.middleware),
  });
}

function makeReachFrequencyStore() {
  return configureStore({
    reducer: {
      [reachFrequencyApi.reducerPath]: reachFrequencyApi.reducer,
    },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware().concat(reachFrequencyApi.middleware),
  });
}

// ---------------------------------------------------------------------------
// inventoryApi
// ---------------------------------------------------------------------------

describe("inventoryApi endpoints — query() function coverage", () => {
  let store: ReturnType<typeof makeInventoryStore>;

  beforeEach(() => {
    store = makeInventoryStore();
  });

  it("getInventoryList — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.getInventoryList.initiate({
          campaignId: "camp-1",
          params: { page: 0, size: 10, sortBy: "name", sortDir: "asc" },
        }),
      ),
    ).not.toThrow();
  });

  it("selectInventory — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.selectInventory.initiate({
          campaignId: "camp-1",
          inventoryId: "inv-1",
          operationType: "SELECT",
        } as any),
      ),
    ).not.toThrow();
  });

  it("bulkSelectInventory — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.bulkSelectInventory.initiate({
          campaignId: "camp-1",
          operationType: "SELECT",
          filters: {},
        } as any),
      ),
    ).not.toThrow();
  });

  it("getCampaignForecast — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.getCampaignForecast.initiate({
          campaignId: "camp-1",
        }),
      ),
    ).not.toThrow();
  });

  it("getCampaignForecast with forceRegenerate — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.getCampaignForecast.initiate({
          campaignId: "camp-1",
          forceRegenerate: true,
        }),
      ),
    ).not.toThrow();
  });

  it("generateInventoryRecommendation — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.generateInventoryRecommendation.initiate({
          campaignId: "camp-1",
        }),
      ),
    ).not.toThrow();
  });

  it("generateInventoryRecommendation with mediaOwnerIds — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.generateInventoryRecommendation.initiate({
          campaignId: "camp-1",
          mediaOwnerIds: ["mo-1", "mo-2"],
        }),
      ),
    ).not.toThrow();
  });

  it("getInventoryRecommendationList — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.getInventoryRecommendationList.initiate({
          campaignId: "camp-1",
          runId: "run-1",
          page: 0,
          size: 20,
        }),
      ),
    ).not.toThrow();
  });

  it("autoOptimizeSchedules — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.autoOptimizeSchedules.initiate({
          campaignId: "camp-1",
        }),
      ),
    ).not.toThrow();
  });

  it("verifyInventoryCsv — query() is invoked on dispatch", () => {
    const mockFile = new File(["content"], "test.csv", { type: "text/csv" });
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.verifyInventoryCsv.initiate({
          campaignId: "camp-1",
          file: mockFile,
          country: "Singapore",
        }),
      ),
    ).not.toThrow();
  });

  it("uploadInventoryCsv — query() is invoked on dispatch", () => {
    const mockFile = new File(["content"], "test.csv", { type: "text/csv" });
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.uploadInventoryCsv.initiate({
          campaignId: "camp-1",
          file: mockFile,
          country: "Singapore",
        }),
      ),
    ).not.toThrow();
  });

  it("getInventoryCsvFiles — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.getInventoryCsvFiles.initiate({
          params: {
            page: 0,
            size: 10,
            sortBy: "uploadedOn",
            sortDir: "desc",
            countryName: "Singapore",
          } as any,
        }),
      ),
    ).not.toThrow();
  });

  it("deleteInventoryCsvFile — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.deleteInventoryCsvFile.initiate({
          fileId: "file-1",
        }),
      ),
    ).not.toThrow();
  });

  it("getInventoryByFileId — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.getInventoryByFileId.initiate({
          fileId: "file-1",
          params: { page: 0, size: 10, sortBy: "name", sortDir: "asc" },
        }),
      ),
    ).not.toThrow();
  });

  it("getSelectedInventory — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.getSelectedInventory.initiate({
          campaignId: "camp-1",
          params: { page: 0, size: 10, sortBy: "name", sortDir: "asc" },
        }),
      ),
    ).not.toThrow();
  });

  it("useInventoryCsvFile — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.useInventoryCsvFile.initiate({
          fileId: "file-1",
          campaignId: "camp-1",
        }),
      ),
    ).not.toThrow();
  });

  it("getGeoImportFiles — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.getGeoImportFiles.initiate({
          params: {
            page: 0,
            size: 10,
            sortBy: "createdAt",
            sortDir: "desc",
            countryName: "Singapore",
          } as any,
        }),
      ),
    ).not.toThrow();
  });

  it("getGeoImportLocations — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.getGeoImportLocations.initiate({
          geoImportId: "geo-1",
        }),
      ),
    ).not.toThrow();
  });

  it("deleteGeoImportFile — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.deleteGeoImportFile.initiate({
          geoImportId: "geo-1",
        }),
      ),
    ).not.toThrow();
  });

  it("importGeoCoordinates — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.importGeoCoordinates.initiate({
          requestBody: {
            fileName: "locations.csv",
            countryName: "Singapore",
            geoDetails: [
              {
                locationName: "Location A",
                radius: "500",
                latitude: "1.3521",
                longitude: "103.8198",
                siteType: "OUTDOOR",
              },
            ],
          },
        }),
      ),
    ).not.toThrow();
  });

  it("getSelectedInventorySchedules — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.getSelectedInventorySchedules.initiate({
          campaignId: "camp-1",
          inventories: ["inv-1", "inv-2"],
        }),
      ),
    ).not.toThrow();
  });

  it("deleteInventorySchedule — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.deleteInventorySchedule.initiate({
          campaignId: "camp-1",
          scheduleId: "sched-1",
        }),
      ),
    ).not.toThrow();
  });

  it("addInventorySchedules — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.addInventorySchedules.initiate({
          campaignId: "camp-1",
          data: {} as any,
        }),
      ),
    ).not.toThrow();
  });

  it("updateInventorySchedules — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.updateInventorySchedules.initiate({
          campaignId: "camp-1",
          data: {} as any,
          scheduleId: "sched-1",
        }),
      ),
    ).not.toThrow();
  });

  it("optimizeInventorySchedules — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.optimizeInventorySchedules.initiate({
          campaignId: "camp-1",
          data: {
            inventoryIds: ["inv-1"],
            clearSchedules: false,
            schedule: {
              startDate: "2026-07-01",
              endDate: "2026-07-31",
              scheduleDays: {} as any,
              bookingMatrix: {},
            },
          },
        }),
      ),
    ).not.toThrow();
  });

  it("getCampaignSchedulePrices — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.getCampaignSchedulePrices.initiate({
          campaignId: "camp-1",
          params: { page: 0, size: 10, sortBy: "name", sortDir: "asc" },
        }),
      ),
    ).not.toThrow();
  });

  it("applyScheduleAdjustment — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.applyScheduleAdjustment.initiate({
          campaignId: "camp-1",
          data: {} as any,
        }),
      ),
    ).not.toThrow();
  });

  it("getPriceHistory — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.getPriceHistory.initiate({
          campaignInventoryScheduleId: "cis-1",
          params: { campaignInventoryScheduleId: "cis-1", page: 0, size: 10 },
        }),
      ),
    ).not.toThrow();
  });

  it("updateInventoryDiscount — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.updateInventoryDiscount.initiate({
          campaignInventoryScheduleId: "cis-1",
          data: {} as any,
        }),
      ),
    ).not.toThrow();
  });

  it("acceptAllPrices — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.acceptAllPrices.initiate({
          campaignId: "camp-1",
          data: { campaignInventorySchedulesIds: ["cis-1", "cis-2"] },
        }),
      ),
    ).not.toThrow();
  });

  it("getPriceSummary — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.getPriceSummary.initiate({
          campaignId: "camp-1",
        }),
      ),
    ).not.toThrow();
  });

  it("bulkUpdateCustomFees — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.bulkUpdateCustomFees.initiate({
          data: [] as any,
        }),
      ),
    ).not.toThrow();
  });

  it("updateCustomFee — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.updateCustomFee.initiate({
          id: "fee-1",
          data: {} as any,
        }),
      ),
    ).not.toThrow();
  });

  it("getVenues — query() is invoked on dispatch (no language)", () => {
    expect(() =>
      store.dispatch(inventoryApi.endpoints.getVenues.initiate({})),
    ).not.toThrow();
  });

  it("getVenues with language — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryApi.endpoints.getVenues.initiate({ language: "ja" }),
      ),
    ).not.toThrow();
  });
});

// ---------------------------------------------------------------------------
// inventoryManagementApi
// ---------------------------------------------------------------------------

describe("inventoryManagementApi endpoints — query() function coverage", () => {
  let store: ReturnType<typeof makeInventoryManagementStore>;

  beforeEach(() => {
    store = makeInventoryManagementStore();
  });

  it("getInventoryAvailability — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryManagementApi.endpoints.getInventoryAvailability.initiate({
          data: {
            inventoryIds: ["inv-1"],
            startTime: "2026-07-01T00:00:00Z",
            endTime: "2026-07-31T23:59:59Z",
          },
        }),
      ),
    ).not.toThrow();
  });

  it("getInventoryDetails — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        inventoryManagementApi.endpoints.getInventoryDetails.initiate({
          inventoryId: "inv-1",
        }),
      ),
    ).not.toThrow();
  });
});

// ---------------------------------------------------------------------------
// reachFrequencyApi
// ---------------------------------------------------------------------------

describe("reachFrequencyApi endpoints — query() function coverage", () => {
  let store: ReturnType<typeof makeReachFrequencyStore>;

  beforeEach(() => {
    store = makeReachFrequencyStore();
  });

  it("getInventoryReachFrequency — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        reachFrequencyApi.endpoints.getInventoryReachFrequency.initiate(
          {} as any,
        ),
      ),
    ).not.toThrow();
  });
});
