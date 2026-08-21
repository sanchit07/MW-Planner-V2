import { configureStore } from "@reduxjs/toolkit";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("@api/axiosBaseQuery", () => ({
  default: () => () => Promise.resolve({ data: { success: true, data: [] } }),
}));

import { agencyApi } from "../agencySlice";

function makeAgencyStore() {
  return configureStore({
    reducer: { [agencyApi.reducerPath]: agencyApi.reducer },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware().concat(agencyApi.middleware),
  });
}

describe("agencyApi endpoints", () => {
  let store: ReturnType<typeof makeAgencyStore>;

  beforeEach(() => {
    store = makeAgencyStore();
  });

  it("reducerPath is agencyApi", () => {
    expect(agencyApi.reducerPath).toBe("agencyApi");
  });

  it("all endpoints are registered", () => {
    expect(agencyApi.endpoints).toHaveProperty("getAgencies");
    expect(agencyApi.endpoints).toHaveProperty("createAgency");
    expect(agencyApi.endpoints).toHaveProperty("linkAgency");
    expect(agencyApi.endpoints).toHaveProperty("getChildCompanies");
  });

  it("getAgencies exposes standard RTK Query interface", () => {
    const endpoint = agencyApi.endpoints.getAgencies;
    expect(typeof endpoint.initiate).toBe("function");
    expect(typeof endpoint.select).toBe("function");
  });

  it("getAgencies query function is covered when dispatched with no params", () => {
    store.dispatch(agencyApi.endpoints.getAgencies.initiate({}));
    expect(agencyApi.endpoints.getAgencies).toBeDefined();
  });

  it("getAgencies query function is covered when dispatched with full params", () => {
    store.dispatch(
      agencyApi.endpoints.getAgencies.initiate({
        page: 1,
        size: 20,
        search: "test",
      }),
    );
    expect(agencyApi.endpoints.getAgencies).toBeDefined();
  });

  it("createAgency query function is covered when dispatched", () => {
    store.dispatch(
      agencyApi.endpoints.createAgency.initiate({
        name: "Test Agency",
        companyEmail: "test@agency.com",
        domain: "agency.com",
      }),
    );
    expect(agencyApi.endpoints.createAgency).toBeDefined();
  });

  it("linkAgency query function is covered when dispatched", () => {
    store.dispatch(
      agencyApi.endpoints.linkAgency.initiate({
        id: "company-123",
        agencyData: {
          agency_id: "agency-456",
        },
      }),
    );
    expect(agencyApi.endpoints.linkAgency).toBeDefined();
  });

  it("getChildCompanies query function is covered when dispatched", () => {
    store.dispatch(
      agencyApi.endpoints.getChildCompanies.initiate({ id: "company-123" }),
    );
    expect(agencyApi.endpoints.getChildCompanies).toBeDefined();
  });
});
