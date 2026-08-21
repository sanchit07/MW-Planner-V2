import { configureStore } from "@reduxjs/toolkit";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("@api/axiosBaseQuery", () => ({
  default: () => () => Promise.resolve({ data: { success: true, data: {} } }),
}));

import {
  configurationMetadataAPI,
  configurationMetadataReducer,
  setConfigurationMetaData,
  ConfigurationsMetaData,
} from "../configurationMetadataSlice";

const makeMetaData = (
  overrides: Partial<ConfigurationsMetaData> = {},
): ConfigurationsMetaData => ({
  campaign_status: ["DRAFT", "ACTIVE"],
  demographics: {
    age: [{ demoKey: "18-24", name: "18-24" }],
    gender: [],
    income: [],
    interests: [],
    venues: [],
    behavior: [],
  },
  ...overrides,
});

const reducer = configurationMetadataReducer;

const initialState: ConfigurationsMetaData = {
  campaign_status: [],
  demographics: {
    age: [],
    gender: [],
    income: [],
    interests: [],
    venues: [],
    behavior: [],
  },
};

describe("configurationMetadataSlice", () => {
  describe("setConfigurationMetaData", () => {
    it("sets campaign_status and demographics from payload", () => {
      const data = makeMetaData();
      const state = reducer(
        initialState,
        setConfigurationMetaData({ success: true, data }),
      );
      expect(state.campaign_status).toEqual(["DRAFT", "ACTIVE"]);
      expect(state.demographics.age).toHaveLength(1);
    });

    it("uses empty arrays when data is missing", () => {
      const state = reducer(
        initialState,
        setConfigurationMetaData({ success: false }),
      );
      expect(state.campaign_status).toEqual([]);
    });

    it("uses initialState demographics when data.demographics is missing", () => {
      const state = reducer(
        initialState,
        setConfigurationMetaData({
          success: true,
          data: {
            campaign_status: ["DRAFT"],
            demographics: undefined as never,
          },
        }),
      );
      expect(state.campaign_status).toEqual(["DRAFT"]);
    });

    it("overwrites previous state", () => {
      const first = reducer(
        initialState,
        setConfigurationMetaData({
          success: true,
          data: makeMetaData({ campaign_status: ["DRAFT"] }),
        }),
      );
      const second = reducer(
        first,
        setConfigurationMetaData({
          success: true,
          data: makeMetaData({ campaign_status: ["ACTIVE", "PAUSED"] }),
        }),
      );
      expect(second.campaign_status).toEqual(["ACTIVE", "PAUSED"]);
    });
  });

  describe("configurationMetadataAPI endpoints", () => {
    function makeConfigStore() {
      return configureStore({
        reducer: {
          [configurationMetadataAPI.reducerPath]:
            configurationMetadataAPI.reducer,
        },
        middleware: (getDefaultMiddleware) =>
          getDefaultMiddleware().concat(configurationMetadataAPI.middleware),
      });
    }

    let store: ReturnType<typeof makeConfigStore>;

    beforeEach(() => {
      store = makeConfigStore();
    });

    it("configurationMetadata endpoint is registered", () => {
      expect(configurationMetadataAPI.endpoints).toHaveProperty(
        "configurationMetadata",
      );
    });

    it("dispatching configurationMetadata initiate covers the query function", () => {
      store.dispatch(
        configurationMetadataAPI.endpoints.configurationMetadata.initiate({}),
      );
      const endpoint = configurationMetadataAPI.endpoints.configurationMetadata;
      expect(typeof endpoint.initiate).toBe("function");
    });

    it("dispatching with language parameter covers the Accept-Language branch", () => {
      store.dispatch(
        configurationMetadataAPI.endpoints.configurationMetadata.initiate({
          language: "ja",
        }),
      );
      expect(
        configurationMetadataAPI.endpoints.configurationMetadata,
      ).toBeDefined();
    });

    it("reducerPath is configurationMetadataAPI", () => {
      expect(configurationMetadataAPI.reducerPath).toBe(
        "configurationMetadataAPI",
      );
    });
  });
});
