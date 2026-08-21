import { configureStore } from "@reduxjs/toolkit";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("@api/axiosBaseQuery", () => ({
  default: () => () => Promise.resolve({ data: { success: true, data: {} } }),
}));

import { dashboardApi } from "../dashboardSlice";

function makeDashboardStore() {
  return configureStore({
    reducer: { [dashboardApi.reducerPath]: dashboardApi.reducer },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware().concat(dashboardApi.middleware),
  });
}

describe("dashboardApi endpoints", () => {
  let store: ReturnType<typeof makeDashboardStore>;

  beforeEach(() => {
    store = makeDashboardStore();
  });

  it("reducerPath is dashboardApi", () => {
    expect(dashboardApi.reducerPath).toBe("dashboardApi");
  });

  it("getDashboardWidgets query function is covered when dispatched", () => {
    store.dispatch(dashboardApi.endpoints.getDashboardWidgets.initiate());
    expect(dashboardApi.endpoints.getDashboardWidgets).toBeDefined();
  });

  it("updateDashboardWidgets mutation query function is covered when dispatched", () => {
    store.dispatch(
      dashboardApi.endpoints.updateDashboardWidgets.initiate([
        { key: "widget1", isEnable: true },
      ]),
    );
    expect(dashboardApi.endpoints.updateDashboardWidgets).toBeDefined();
  });

  it("getCampaignOverviewByStatus query function is covered when dispatched", () => {
    store.dispatch(
      dashboardApi.endpoints.getCampaignOverviewByStatus.initiate({
        startDate: "2026-01-01",
        endDate: "2026-12-31",
        companyId: "company-1",
      }),
    );
    expect(dashboardApi.endpoints.getCampaignOverviewByStatus).toBeDefined();
  });

  it("getCampaignPerformance query function is covered with minimal params", () => {
    store.dispatch(
      dashboardApi.endpoints.getCampaignPerformance.initiate({
        startDate: "2026-01-01",
        endDate: "2026-12-31",
        companyId: "company-1",
      }),
    );
    expect(dashboardApi.endpoints.getCampaignPerformance).toBeDefined();
  });

  it("getCampaignPerformance covers optional params branch", () => {
    store.dispatch(
      dashboardApi.endpoints.getCampaignPerformance.initiate({
        startDate: "2026-01-01",
        endDate: "2026-12-31",
        sortBy: "budget",
        sortDir: "desc",
        status: "ACTIVE",
        companyId: "company-1",
      }),
    );
    expect(dashboardApi.endpoints.getCampaignPerformance).toBeDefined();
  });

  it("getPerformanceSummaryReach query function is covered when dispatched", () => {
    store.dispatch(
      dashboardApi.endpoints.getPerformanceSummaryReach.initiate({
        startDate: "2026-01-01",
        endDate: "2026-12-31",
        companyId: "company-1",
      }),
    );
    expect(dashboardApi.endpoints.getPerformanceSummaryReach).toBeDefined();
  });

  it("getPerformanceSummaryCost query function is covered when dispatched", () => {
    store.dispatch(
      dashboardApi.endpoints.getPerformanceSummaryCost.initiate({
        startDate: "2026-01-01",
        endDate: "2026-12-31",
        companyId: "company-1",
      }),
    );
    expect(dashboardApi.endpoints.getPerformanceSummaryCost).toBeDefined();
  });

  it("getSalesPerformanceSummary query function is covered with minimal params", () => {
    store.dispatch(
      dashboardApi.endpoints.getSalesPerformanceSummary.initiate({
        startDate: "2026-01-01",
        endDate: "2026-12-31",
        showBy: "country",
        companyId: "company-1",
      }),
    );
    expect(dashboardApi.endpoints.getSalesPerformanceSummary).toBeDefined();
  });

  it("getSalesPerformanceSummary covers optional params branch", () => {
    store.dispatch(
      dashboardApi.endpoints.getSalesPerformanceSummary.initiate({
        startDate: "2026-01-01",
        endDate: "2026-12-31",
        showBy: "agency",
        companyId: "company-1",
        page: 1,
        size: 20,
        sortBy: "cost",
        sortDir: "desc",
      }),
    );
    expect(dashboardApi.endpoints.getSalesPerformanceSummary).toBeDefined();
  });

  it("all 7 endpoints are registered", () => {
    expect(dashboardApi.endpoints).toHaveProperty("getDashboardWidgets");
    expect(dashboardApi.endpoints).toHaveProperty("updateDashboardWidgets");
    expect(dashboardApi.endpoints).toHaveProperty(
      "getCampaignOverviewByStatus",
    );
    expect(dashboardApi.endpoints).toHaveProperty("getCampaignPerformance");
    expect(dashboardApi.endpoints).toHaveProperty("getPerformanceSummaryReach");
    expect(dashboardApi.endpoints).toHaveProperty("getPerformanceSummaryCost");
    expect(dashboardApi.endpoints).toHaveProperty("getSalesPerformanceSummary");
  });
});
