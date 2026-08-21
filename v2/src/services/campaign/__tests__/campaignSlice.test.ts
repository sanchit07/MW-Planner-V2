import { describe, it, expect } from "vitest";

import { CampaignCreateResponse } from "../../../types/campaign.types";
import { CampaignForecastData } from "../../../types/inventory.types";
import {
  campaignApi,
  campaignSlice,
  setCampaignData,
  setCampaignId,
  setIsEditMode,
  setForecastData,
  resetCampaignState,
  CampaignState,
} from "../campaignSlice";

const reducer = campaignSlice.reducer;

/** Minimal valid CampaignForecastData for tests */
function makeForecastData(
  overrides: Partial<CampaignForecastData> = {},
): CampaignForecastData {
  return {
    totalInventories: 5,
    estimatedImpression: 10000,
    estimatedReach: 8000,
    estimatedFrequency: 1.5,
    estimatedAdPlays: 500,
    sov: 20,
    avgCpm: 15.5,
    avgECpm: 12.3,
    totalCost: 5000,
    plannedSot: 40,
    totalSot: 100,
    warnings: [],
    ...overrides,
  };
}

/** Minimal valid CampaignCreateResponse for tests */
function makeCampaignData(
  overrides: Partial<CampaignCreateResponse> = {},
): CampaignCreateResponse {
  return {
    id: "camp-1",
    name: "Test Campaign",
    status: "DRAFT",
    countryId: "SG",
    startDate: "2026-05-01",
    endDate: "2026-05-31",
    clientType: "AGENCY",
    createdAt: "2026-04-14T00:00:00Z",
    updatedAt: "2026-04-14T00:00:00Z",
    ...overrides,
  };
}

const initialState: CampaignState = {
  currentCampaignName: "",
  campaignId: null,
  isCreating: false,
  createError: null,
  isEditMode: false,
  campaignData: null,
  forecastData: null,
  recommendationRun: null,
};

describe("campaignSlice", () => {
  describe("setCampaignData — brand/agency name preservation", () => {
    it("stores brand and agency names from the payload when valid", () => {
      const state = reducer(
        initialState,
        setCampaignData(
          makeCampaignData({
            brand: { id: "brand-1", name: "Acme Corp" },
            agency: { id: "agency-1", name: "MediaAgency" },
          }),
        ),
      );

      expect(state.campaignData?.brand?.name).toBe("Acme Corp");
      expect(state.campaignData?.agency?.name).toBe("MediaAgency");
    });

    it('treats lowercase "unknown" from API as invalid and preserves existing names', () => {
      const stateWithNames: CampaignState = {
        ...initialState,
        campaignData: makeCampaignData({
          brand: { id: "brand-1", name: "Acme Corp" },
          agency: { id: "agency-1", name: "MediaAgency" },
        }),
      };

      const nextState = reducer(
        stateWithNames,
        setCampaignData(
          makeCampaignData({
            brand: { id: "brand-1", name: "unknown" },
            agency: { id: "agency-1", name: "unknown" },
          }),
        ),
      );

      expect(nextState.campaignData?.brand?.name).toBe("Acme Corp");
      expect(nextState.campaignData?.agency?.name).toBe("MediaAgency");
    });

    it('treats capitalized "Unknown" from API as invalid and preserves existing names', () => {
      const stateWithNames: CampaignState = {
        ...initialState,
        campaignData: makeCampaignData({
          brand: { id: "brand-1", name: "Acme Corp" },
          agency: { id: "agency-1", name: "MediaAgency" },
        }),
      };

      const nextState = reducer(
        stateWithNames,
        setCampaignData(
          makeCampaignData({
            brand: { id: "brand-1", name: "Unknown" },
            agency: { id: "agency-1", name: "Unknown" },
          }),
        ),
      );

      expect(nextState.campaignData?.brand?.name).toBe("Acme Corp");
      expect(nextState.campaignData?.agency?.name).toBe("MediaAgency");
    });

    it("preserves existing names when payload omits them (undefined)", () => {
      const stateWithNames: CampaignState = {
        ...initialState,
        campaignData: makeCampaignData({
          brand: { id: "brand-1", name: "Acme Corp" },
          agency: { id: "agency-1", name: "MediaAgency" },
        }),
      };

      const nextState = reducer(
        stateWithNames,
        setCampaignData(
          makeCampaignData({
            brand: { id: "brand-1", name: undefined as unknown as string },
            agency: { id: "agency-1", name: undefined as unknown as string },
          }),
        ),
      );

      expect(nextState.campaignData?.brand?.name).toBe("Acme Corp");
      expect(nextState.campaignData?.agency?.name).toBe("MediaAgency");
    });

    it("overwrites existing names when payload has valid new names", () => {
      const stateWithNames: CampaignState = {
        ...initialState,
        campaignData: makeCampaignData({
          brand: { id: "brand-1", name: "Old Brand" },
          agency: { id: "agency-1", name: "Old Agency" },
        }),
      };

      const nextState = reducer(
        stateWithNames,
        setCampaignData(
          makeCampaignData({
            brand: { id: "brand-1", name: "New Brand" },
            agency: { id: "agency-1", name: "New Agency" },
          }),
        ),
      );

      expect(nextState.campaignData?.brand?.name).toBe("New Brand");
      expect(nextState.campaignData?.agency?.name).toBe("New Agency");
    });

    it("does not preserve stale names when existing names are also 'unknown'", () => {
      const stateWithUnknown: CampaignState = {
        ...initialState,
        campaignData: makeCampaignData({
          brand: { id: "brand-1", name: "unknown" },
          agency: { id: "agency-1", name: "Unknown" },
        }),
      };

      const nextState = reducer(
        stateWithUnknown,
        setCampaignData(
          makeCampaignData({
            brand: { id: "brand-1", name: undefined as unknown as string },
            agency: { id: "agency-1", name: undefined as unknown as string },
          }),
        ),
      );

      // No valid name exists anywhere, falls through to incoming (undefined)
      expect(nextState.campaignData?.brand?.name).toBeUndefined();
      expect(nextState.campaignData?.agency?.name).toBeUndefined();
    });

    it("handles first-time dispatch (no existing campaignData) with valid names", () => {
      const nextState = reducer(
        initialState,
        setCampaignData(
          makeCampaignData({
            brand: { id: "brand-1", name: "Acme Corp" },
            agency: { id: "agency-1", name: "MediaAgency" },
          }),
        ),
      );

      expect(nextState.campaignData?.brand?.name).toBe("Acme Corp");
      expect(nextState.campaignData?.agency?.name).toBe("MediaAgency");
    });

    it('handles first-time dispatch with "unknown" — no existing data to fall back to', () => {
      const nextState = reducer(
        initialState,
        setCampaignData(
          makeCampaignData({
            brand: { id: "brand-1", name: "unknown" },
            agency: { id: "agency-1", name: "unknown" },
          }),
        ),
      );

      // No existing data, so "unknown" is stored as-is (last fallback)
      expect(nextState.campaignData?.brand?.name).toBe("unknown");
      expect(nextState.campaignData?.agency?.name).toBe("unknown");
    });

    it("simulates autosave cycle: names survive PATCH responses that omit them", () => {
      // Step 1: Campaign created with valid names
      let state = reducer(
        initialState,
        setCampaignData(
          makeCampaignData({
            brand: { id: "brand-1", name: "Acme Corp" },
            agency: { id: "agency-1", name: "MediaAgency" },
          }),
        ),
      );

      expect(state.campaignData?.brand?.name).toBe("Acme Corp");
      expect(state.campaignData?.agency?.name).toBe("MediaAgency");

      // Step 2: Autosave PATCH response — omits brand/agency name
      state = reducer(
        state,
        setCampaignData(
          makeCampaignData({
            brand: { id: "brand-1", name: undefined as unknown as string },
            agency: { id: "agency-1", name: undefined as unknown as string },
            budget: 50000,
          }),
        ),
      );

      expect(state.campaignData?.brand?.name).toBe("Acme Corp");
      expect(state.campaignData?.agency?.name).toBe("MediaAgency");
      expect(state.campaignData?.budget).toBe(50000);

      // Step 3: Autosave PATCH response — returns "unknown"
      state = reducer(
        state,
        setCampaignData(
          makeCampaignData({
            brand: { id: "brand-1", name: "unknown" },
            agency: { id: "agency-1", name: "Unknown" },
            budget: 75000,
          }),
        ),
      );

      expect(state.campaignData?.brand?.name).toBe("Acme Corp");
      expect(state.campaignData?.agency?.name).toBe("MediaAgency");
      expect(state.campaignData?.budget).toBe(75000);
    });

    it("simulates reload cycle: GET response with valid names overwrites", () => {
      let state = reducer(
        initialState,
        setCampaignData(
          makeCampaignData({
            brand: { id: "brand-1", name: "Acme Corp" },
            agency: { id: "agency-1", name: "MediaAgency" },
          }),
        ),
      );

      // GET /campaigns/{id} returns fresh valid names
      state = reducer(
        state,
        setCampaignData(
          makeCampaignData({
            brand: { id: "brand-1", name: "Acme Corp Updated" },
            agency: { id: "agency-1", name: "MediaAgency Updated" },
          }),
        ),
      );

      expect(state.campaignData?.brand?.name).toBe("Acme Corp Updated");
      expect(state.campaignData?.agency?.name).toBe("MediaAgency Updated");
    });

    it("updates currentCampaignName from payload", () => {
      const state = reducer(
        initialState,
        setCampaignData(makeCampaignData({ name: "My Campaign" })),
      );

      expect(state.currentCampaignName).toBe("My Campaign");
    });
  });

  describe("setCampaignData — dsp preservation", () => {
    it("stores dsp from the payload", () => {
      const state = reducer(
        initialState,
        setCampaignData(makeCampaignData({ dsp: "ACTIVATE" })),
      );

      expect(state.campaignData?.dsp).toBe("ACTIVATE");
    });

    it("preserves the existing dsp when a reload response omits it", () => {
      let state = reducer(
        initialState,
        setCampaignData(makeCampaignData({ dsp: "ACTIVATE" })),
      );

      // Simulates reloadCampaignData() re-fetching the campaign (e.g. after an
      // inventory select/deselect) via a GET response that doesn't echo dsp.
      state = reducer(
        state,
        setCampaignData(makeCampaignData({ budget: 75000 })),
      );

      expect(state.campaignData?.dsp).toBe("ACTIVATE");
      expect(state.campaignData?.budget).toBe(75000);
    });

    it("overwrites dsp when the new payload provides a different value", () => {
      let state = reducer(
        initialState,
        setCampaignData(makeCampaignData({ dsp: "ACTIVATE" })),
      );

      state = reducer(
        state,
        setCampaignData(makeCampaignData({ dsp: "DEACTIVATE" })),
      );

      expect(state.campaignData?.dsp).toBe("DEACTIVATE");
    });
  });

  describe("other reducers (unchanged behavior)", () => {
    it("setCampaignId sets the campaign ID", () => {
      const state = reducer(initialState, setCampaignId("camp-123"));
      expect(state.campaignId).toBe("camp-123");
    });

    it("setIsEditMode toggles edit mode", () => {
      const state = reducer(initialState, setIsEditMode(true));
      expect(state.isEditMode).toBe(true);
    });

    it("resetCampaignState clears all state including forecastData", () => {
      const dirtyState: CampaignState = {
        currentCampaignName: "Test",
        campaignId: "camp-1",
        isCreating: false,
        createError: "some error",
        isEditMode: true,
        campaignData: makeCampaignData(),
        forecastData: makeForecastData(),
        recommendationRun: {
          runId: "run-1",
          signature: "sig",
          mediaChannels: [],
        },
      };

      const state = reducer(dirtyState, resetCampaignState());
      expect(state.isEditMode).toBe(false);
      expect(state.campaignData).toBeNull();
      expect(state.forecastData).toBeNull();
      expect(state.isCreating).toBe(false);
      expect(state.createError).toBeNull();
    });
  });

  describe("campaignApi — getCountryMarketDetailsByIso endpoint", () => {
    it("is registered on campaignApi", () => {
      expect(campaignApi.endpoints).toHaveProperty(
        "getCountryMarketDetailsByIso",
      );
    });

    it("exposes the standard RTK Query endpoint interface", () => {
      const endpoint = campaignApi.endpoints.getCountryMarketDetailsByIso;
      expect(typeof endpoint.initiate).toBe("function");
      expect(typeof endpoint.select).toBe("function");
    });
  });

  describe("setForecastData", () => {
    it("stores forecast data in state", () => {
      const forecast = makeForecastData();
      const state = reducer(initialState, setForecastData(forecast));
      expect(state.forecastData).toEqual(forecast);
    });

    it("overwrites previously stored forecast data", () => {
      const first = makeForecastData({ totalInventories: 3 });
      const second = makeForecastData({ totalInventories: 10 });
      const stateAfterFirst = reducer(initialState, setForecastData(first));
      const stateAfterSecond = reducer(
        stateAfterFirst,
        setForecastData(second),
      );
      expect(stateAfterSecond.forecastData?.totalInventories).toBe(10);
    });

    it("does not affect other state fields", () => {
      const stateWithCampaign = reducer(
        initialState,
        setCampaignData(makeCampaignData()),
      );
      const state = reducer(
        stateWithCampaign,
        setForecastData(makeForecastData()),
      );
      expect(state.campaignData).not.toBeNull();
      expect(state.forecastData).not.toBeNull();
    });
  });
});
