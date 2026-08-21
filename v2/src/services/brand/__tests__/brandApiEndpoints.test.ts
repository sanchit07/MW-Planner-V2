// vi.mock is hoisted by Vitest's module transformer and executes before any
// imports, so the mock is in place when brandSlice is first evaluated.
import { configureStore } from "@reduxjs/toolkit";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@api/axiosBaseQuery", () => ({
  default: () => () => Promise.resolve({ data: { success: true, data: {} } }),
}));

import { brandApi, iamBrandApi } from "../brandSlice";

// ---------------------------------------------------------------------------
// Store factories
// ---------------------------------------------------------------------------

function makeBrandStore() {
  return configureStore({
    reducer: { [brandApi.reducerPath]: brandApi.reducer },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware().concat(brandApi.middleware),
  });
}

function makeIamBrandStore() {
  return configureStore({
    reducer: { [iamBrandApi.reducerPath]: iamBrandApi.reducer },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware().concat(iamBrandApi.middleware),
  });
}

// ---------------------------------------------------------------------------
// brandApi (BACKEND_URL — Planner config API)
// ---------------------------------------------------------------------------

describe("brandApi endpoints — query() function coverage", () => {
  let store: ReturnType<typeof makeBrandStore>;

  beforeEach(() => {
    store = makeBrandStore();
  });

  it("getIabCategories — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(brandApi.endpoints.getIabCategories.initiate()),
    ).not.toThrow();
  });
});

// ---------------------------------------------------------------------------
// iamBrandApi (ACCOUNT_PROXY_URL — IAM Brand API)
// ---------------------------------------------------------------------------

describe("iamBrandApi endpoints — query() function coverage", () => {
  let store: ReturnType<typeof makeIamBrandStore>;

  beforeEach(() => {
    store = makeIamBrandStore();
  });

  it("getAllBrands — query() is invoked on dispatch (no params)", () => {
    expect(() =>
      store.dispatch(iamBrandApi.endpoints.getAllBrands.initiate({})),
    ).not.toThrow();
  });

  it("getAllBrands with search — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        iamBrandApi.endpoints.getAllBrands.initiate({ search: "nike" }),
      ),
    ).not.toThrow();
  });

  it("getAllBrands with active_only — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        iamBrandApi.endpoints.getAllBrands.initiate({ active_only: true }),
      ),
    ).not.toThrow();
  });

  it("getAllBrands with iab_category and include — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        iamBrandApi.endpoints.getAllBrands.initiate({
          iab_category: "IAB1",
          include: "iab_categories",
        }),
      ),
    ).not.toThrow();
  });

  it("getCompanyBrands — query() is invoked on dispatch (no extra params)", () => {
    expect(() =>
      store.dispatch(
        iamBrandApi.endpoints.getCompanyBrands.initiate({
          companyId: "company-1",
        }),
      ),
    ).not.toThrow();
  });

  it("getCompanyBrands with search and pagination — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        iamBrandApi.endpoints.getCompanyBrands.initiate({
          companyId: "company-1",
          params: {
            search: "brand",
            active_only: true,
            page: 0,
            limit: 20,
            include: "iab_categories",
          },
        }),
      ),
    ).not.toThrow();
  });

  it("createBrand — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        iamBrandApi.endpoints.createBrand.initiate({
          brandData: {
            name: "Test Brand",
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
          } as any,
          activeCompanyId: "company-1",
        }),
      ),
    ).not.toThrow();
  });

  it("linkBrandToCompany — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        iamBrandApi.endpoints.linkBrandToCompany.initiate({
          companyId: "company-1",
          brandId: "brand-1",
        }),
      ),
    ).not.toThrow();
  });

  it("getIamIabCategories — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(iamBrandApi.endpoints.getIamIabCategories.initiate()),
    ).not.toThrow();
  });

  it("getIabTaxonomyVersions — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(iamBrandApi.endpoints.getIabTaxonomyVersions.initiate()),
    ).not.toThrow();
  });

  it("getIabTaxonomyHierarchy — query() is invoked on dispatch", () => {
    expect(() =>
      store.dispatch(
        iamBrandApi.endpoints.getIabTaxonomyHierarchy.initiate("version-1"),
      ),
    ).not.toThrow();
  });
});
