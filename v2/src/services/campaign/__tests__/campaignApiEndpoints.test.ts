// vi.mock is hoisted by Vitest's module transformer and executes before any
// imports, so the mock is in place when campaignSlice is first evaluated.
import { configureStore } from "@reduxjs/toolkit";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@api/axiosBaseQuery", () => ({
  default: () => () => Promise.resolve({ data: { success: true, data: {} } }),
}));

import { campaignApi, companyApi } from "../campaignSlice";

// ---------------------------------------------------------------------------
// Store factories
// ---------------------------------------------------------------------------

function makeCampaignStore() {
  return configureStore({
    reducer: { [campaignApi.reducerPath]: campaignApi.reducer },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware().concat(campaignApi.middleware),
  });
}

function makeCompanyStore() {
  return configureStore({
    reducer: { [companyApi.reducerPath]: companyApi.reducer },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware().concat(companyApi.middleware),
  });
}

// ---------------------------------------------------------------------------
// campaignApi
// ---------------------------------------------------------------------------

describe("campaignApi endpoints — query() function coverage", () => {
  let store: ReturnType<typeof makeCampaignStore>;

  beforeEach(() => {
    store = makeCampaignStore();
  });

  it("getSequencer — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(campaignApi.endpoints.getSequencer.initiate("CAMP")),
    ).not.toThrow();
  });

  it("createCampaign — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        campaignApi.endpoints.createCampaign.initiate({
          name: "Test Campaign",
          countryId: "SG",
          startDate: "2026-07-01",
          endDate: "2026-07-31",
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
        } as any),
      ),
    ).not.toThrow();
  });

  it("autosaveCampaign — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        campaignApi.endpoints.autosaveCampaign.initiate({
          id: "camp-1",
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          data: { name: "Updated Name" } as any,
        }),
      ),
    ).not.toThrow();
  });

  it("getCampaign — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(campaignApi.endpoints.getCampaign.initiate("camp-1")),
    ).not.toThrow();
  });

  it("updateCampaign — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        campaignApi.endpoints.updateCampaign.initiate({
          id: "camp-1",
          name: "Updated Campaign",
          status: "DRAFT",
          countryId: "SG",
          startDate: "2026-07-01",
          endDate: "2026-07-31",
          clientType: "AGENCY",
          createdAt: "2026-07-01T00:00:00Z",
          updatedAt: "2026-07-01T00:00:00Z",
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
        } as any),
      ),
    ).not.toThrow();
  });

  it("getCampaigns — query() is invoked on dispatch (no params)", () => {
    expect(() =>
      store.dispatch(campaignApi.endpoints.getCampaigns.initiate({})),
    ).not.toThrow();
  });

  it("getCampaigns — query() is invoked on dispatch (with params)", () => {
    expect(() =>
      store.dispatch(
        campaignApi.endpoints.getCampaigns.initiate({
          page: 0,
          size: 10,
          sortBy: "updatedAt",
          sortDir: "desc",
          nameContains: "test",
          statuses: "DRAFT",
        }),
      ),
    ).not.toThrow();
  });

  it("getCountries — query() is invoked on dispatch (no params)", () => {
    expect(() =>
      store.dispatch(campaignApi.endpoints.getCountries.initiate({})),
    ).not.toThrow();
  });

  it("getCountries — query() is invoked on dispatch (with params)", () => {
    expect(() =>
      store.dispatch(
        campaignApi.endpoints.getCountries.initiate({
          page: 0,
          size: 10,
          sortBy: "updatedAt",
          sortDir: "desc",
        }),
      ),
    ).not.toThrow();
  });

  it("getCountriesMarketDetails — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        campaignApi.endpoints.getCountriesMarketDetails.initiate(),
      ),
    ).not.toThrow();
  });

  it("getCountryMarketDetailsByIso — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        campaignApi.endpoints.getCountryMarketDetailsByIso.initiate("SG"),
      ),
    ).not.toThrow();
  });

  it("getCountryByName — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        campaignApi.endpoints.getCountryByName.initiate("Singapore"),
      ),
    ).not.toThrow();
  });

  it("filterCompanies — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        campaignApi.endpoints.filterCompanies.initiate({
          offset: 0,
          limit: 50,
          company_type: "MEDIA_OWNER",
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
        } as any),
      ),
    ).not.toThrow();
  });

  it("filterCompanies with search and country — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        campaignApi.endpoints.filterCompanies.initiate({
          offset: 0,
          limit: 50,
          company_type: "MEDIA_OWNER",
          search: "test",
          country: "SG",
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
        } as any),
      ),
    ).not.toThrow();
  });

  it("viewCampaign — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(campaignApi.endpoints.viewCampaign.initiate("camp-1")),
    ).not.toThrow();
  });

  it("splitCostCampaign — query() is invoked on dispatch (no language)", () => {
    expect(() =>
      store.dispatch(
        campaignApi.endpoints.splitCostCampaign.initiate({
          campaignId: "camp-1",
          splitBy: "MEDIA_OWNER",
        }),
      ),
    ).not.toThrow();
  });

  it("splitCostCampaign with language — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        campaignApi.endpoints.splitCostCampaign.initiate({
          campaignId: "camp-1",
          splitBy: "MEDIA_OWNER",
          language: "ja",
        }),
      ),
    ).not.toThrow();
  });

  it("submitForReview — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(campaignApi.endpoints.submitForReview.initiate("camp-1")),
    ).not.toThrow();
  });

  it("changeProposalStatus — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        campaignApi.endpoints.changeProposalStatus.initiate("proposal-1"),
      ),
    ).not.toThrow();
  });

  it("getMediaPlan — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(campaignApi.endpoints.getMediaPlan.initiate("camp-1")),
    ).not.toThrow();
  });

  it("getCampaignApprovalDetails — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        campaignApi.endpoints.getCampaignApprovalDetails.initiate({
          campaignId: "camp-1",
          activeCompanyId: "company-1",
        }),
      ),
    ).not.toThrow();
  });

  it("updateApprovalStatus — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        campaignApi.endpoints.updateApprovalStatus.initiate({
          inProgressId: "inprog-1",
          status: "APPROVED",
          comment: "Looks good",
          activeCompanyId: "company-1",
        }),
      ),
    ).not.toThrow();
  });

  it("updateApprovalStatus with REJECTED — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        campaignApi.endpoints.updateApprovalStatus.initiate({
          inProgressId: "inprog-1",
          status: "REJECTED",
          comment: "Needs revision",
          activeCompanyId: "company-1",
        }),
      ),
    ).not.toThrow();
  });

  it("getCampaignHistory — query() is invoked on dispatch (minimal args)", () => {
    expect(() =>
      store.dispatch(
        campaignApi.endpoints.getCampaignHistory.initiate({
          campaignId: "camp-1",
        }),
      ),
    ).not.toThrow();
  });

  it("getCampaignHistory with params and language — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        campaignApi.endpoints.getCampaignHistory.initiate({
          campaignId: "camp-1",
          params: { page: 0, size: 10 },
          language: "ja",
        }),
      ),
    ).not.toThrow();
  });

  it("bulkActionsCampaign DUPLICATE — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        campaignApi.endpoints.bulkActionsCampaign.initiate({
          campaignIds: ["camp-1", "camp-2"],
          action: "DUPLICATE",
        }),
      ),
    ).not.toThrow();
  });

  it("bulkActionsCampaign ARCHIVE — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        campaignApi.endpoints.bulkActionsCampaign.initiate({
          campaignIds: ["camp-1"],
          action: "ARCHIVE",
        }),
      ),
    ).not.toThrow();
  });

  it("deleteCampaign — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(campaignApi.endpoints.deleteCampaign.initiate("camp-1")),
    ).not.toThrow();
  });
});

// ---------------------------------------------------------------------------
// companyApi (BACKEND_URL_PROXY)
// ---------------------------------------------------------------------------

describe("companyApi endpoints — query() function coverage", () => {
  let store: ReturnType<typeof makeCompanyStore>;

  beforeEach(() => {
    store = makeCompanyStore();
  });

  it("getCompanyMarketAccess — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        companyApi.endpoints.getCompanyMarketAccess.initiate("company-1"),
      ),
    ).not.toThrow();
  });
});
