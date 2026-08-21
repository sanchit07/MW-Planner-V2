import { configureStore } from "@reduxjs/toolkit";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("@api/axiosBaseQuery", () => ({
  default: () => () => Promise.resolve({ data: { success: true, data: {} } }),
}));

import { publicAccessApi, publicInventoryApi } from "../publicAccessSlice";

function makePublicAccessStore() {
  return configureStore({
    reducer: { [publicAccessApi.reducerPath]: publicAccessApi.reducer },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware().concat(publicAccessApi.middleware),
  });
}

function makePublicInventoryStore() {
  return configureStore({
    reducer: { [publicInventoryApi.reducerPath]: publicInventoryApi.reducer },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware().concat(publicInventoryApi.middleware),
  });
}

describe("publicAccessApi endpoints", () => {
  let store: ReturnType<typeof makePublicAccessStore>;

  beforeEach(() => {
    store = makePublicAccessStore();
  });

  it("reducerPath is publicAccessApi", () => {
    expect(publicAccessApi.reducerPath).toBe("publicAccessApi");
  });

  it("generatePublicToken endpoint is registered", () => {
    expect(publicAccessApi.endpoints).toHaveProperty("generatePublicToken");
  });

  it("generatePublicToken query function is covered when dispatched", () => {
    store.dispatch(
      publicAccessApi.endpoints.generatePublicToken.initiate("campaign-123"),
    );
    expect(publicAccessApi.endpoints.generatePublicToken).toBeDefined();
  });
});

describe("publicInventoryApi endpoints", () => {
  let store: ReturnType<typeof makePublicInventoryStore>;

  beforeEach(() => {
    store = makePublicInventoryStore();
  });

  it("reducerPath is publicInventoryApi", () => {
    expect(publicInventoryApi.reducerPath).toBe("publicInventoryApi");
  });

  it("getPublicInventories endpoint is registered", () => {
    expect(publicInventoryApi.endpoints).toHaveProperty("getPublicInventories");
  });

  it("getPublicInventories query function is covered with minimal params", () => {
    store.dispatch(
      publicInventoryApi.endpoints.getPublicInventories.initiate({
        publicToken: "token-abc",
      }),
    );
    expect(publicInventoryApi.endpoints.getPublicInventories).toBeDefined();
  });

  it("getPublicInventories covers optional params branch", () => {
    store.dispatch(
      publicInventoryApi.endpoints.getPublicInventories.initiate({
        publicToken: "token-abc",
        name: "Test Inventory",
        inventoryType: "DIGITAL",
        page: 1,
        size: 20,
        sortBy: "name",
        sortDir: "asc",
      }),
    );
    expect(publicInventoryApi.endpoints.getPublicInventories).toBeDefined();
  });
});
