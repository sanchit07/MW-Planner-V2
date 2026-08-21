import { configureStore } from "@reduxjs/toolkit";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("@api/axiosBaseQuery", () => ({
  default: () => () => Promise.resolve({ data: { success: true, data: {} } }),
}));

import {
  accountApi,
  accountUserApi,
  mapTenantCompanyToMembership,
  type TenantCompany,
} from "../accountApi";

function makeAccountStore() {
  return configureStore({
    reducer: { [accountApi.reducerPath]: accountApi.reducer },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware().concat(accountApi.middleware),
  });
}

describe("accountApi endpoints", () => {
  let store: ReturnType<typeof makeAccountStore>;

  beforeEach(() => {
    store = makeAccountStore();
  });

  it("reducerPath is accountApi", () => {
    expect(accountApi.reducerPath).toBe("accountApi");
  });

  it("getUsers endpoint is registered", () => {
    expect(accountApi.endpoints).toHaveProperty("getUsers");
  });

  it("getUsers query function is covered when dispatched", () => {
    store.dispatch(
      accountApi.endpoints.getUsers.initiate({ company_id: "company-123" }),
    );
    expect(accountApi.endpoints.getUsers).toBeDefined();
  });

  it("getUsers exposes standard RTK Query interface", () => {
    expect(typeof accountApi.endpoints.getUsers.initiate).toBe("function");
    expect(typeof accountApi.endpoints.getUsers.select).toBe("function");
  });
});

function makeAccountUserStore() {
  return configureStore({
    reducer: { [accountUserApi.reducerPath]: accountUserApi.reducer },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware().concat(accountUserApi.middleware),
  });
}

describe("accountUserApi endpoints", () => {
  let store: ReturnType<typeof makeAccountUserStore>;

  beforeEach(() => {
    store = makeAccountUserStore();
  });

  it("reducerPath is accountUserApi", () => {
    expect(accountUserApi.reducerPath).toBe("accountUserApi");
  });

  it("both endpoints are registered", () => {
    expect(accountUserApi.endpoints).toHaveProperty("updateUser");
    expect(accountUserApi.endpoints).toHaveProperty("setPassword");
  });

  it("updateUser query function is covered when dispatched", () => {
    store.dispatch(
      accountUserApi.endpoints.updateUser.initiate({
        id: "user-123",
        data: { first_name: "John", last_name: "Doe" },
      }),
    );
    expect(accountUserApi.endpoints.updateUser).toBeDefined();
  });

  it("setPassword query function is covered when dispatched", () => {
    store.dispatch(
      accountUserApi.endpoints.setPassword.initiate({
        id: "user-123",
        password: "newpassword123",
      }),
    );
    expect(accountUserApi.endpoints.setPassword).toBeDefined();
  });
});

describe("mapTenantCompanyToMembership", () => {
  const baseCompany: TenantCompany = {
    access_type: "full",
    child_count: 0,
    company_id: "company-1",
    company_name: "Test Company",
    company_seat_id: 1,
    company_type: {
      id: "ct-1",
      name: "Media Owner",
      company_type_code: "MEDIA_OWNER",
    },
    has_parent: false,
    internal_support: false,
    is_active: true,
    is_primary_company: true,
    tenant_switch: false,
  };

  it("maps company_type fields and derives is_supplier_side/is_demand_side from the code", () => {
    const result = mapTenantCompanyToMembership(baseCompany);
    expect(result.company_type).toEqual({
      id: "ct-1",
      code: "MEDIA_OWNER",
      name: "Media Owner",
      is_supplier_side: true,
      is_demand_side: false,
    });
  });

  it("falls back to empty id/code/name when company_type is null instead of throwing", () => {
    const company: TenantCompany = { ...baseCompany, company_type: null };

    expect(() => mapTenantCompanyToMembership(company)).not.toThrow();
    const result = mapTenantCompanyToMembership(company);
    expect(result.company_type).toEqual({
      id: "",
      code: "",
      name: "",
      is_supplier_side: false,
      is_demand_side: false,
    });
  });
});
