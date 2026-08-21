import { describe, expect, it, vi, beforeEach } from "vitest";

// Regression: the two direct planner-backend download clients must inject the
// active company header. Without `injectActiveCompanyId`, a switched user's
// downloads would silently scope to the PRIMARY company instead of the acting
// company, bypassing the backend's acting-company guards.
vi.mock("../../api/axiosInstance", () => ({
  createAxiosInstance: vi.fn(() => ({
    get: vi.fn().mockResolvedValue({ data: new Blob(["x"]) }),
  })),
}));

import { createAxiosInstance } from "../../api/axiosInstance";
import { inventoryApi } from "@services/inventory/inventorySlice";
import { store } from "@store";

const mockedCreate = vi.mocked(createAxiosInstance);

describe("inventory download clients", () => {
  beforeEach(() => {
    mockedCreate.mockClear();
  });

  it("CSV import download uses an axios instance with active-company header injection", async () => {
    await store.dispatch(
      inventoryApi.endpoints.downloadInventoryCsvFile.initiate({
        fileId: "file-1",
      }),
    );
    expect(mockedCreate).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1"),
      expect.objectContaining({ injectActiveCompanyId: true }),
    );
  });

  it("geo import download uses an axios instance with active-company header injection", async () => {
    await store.dispatch(
      inventoryApi.endpoints.downloadGeoImportFile.initiate({
        geoImportId: "geo-1",
      }),
    );
    expect(mockedCreate).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1"),
      expect.objectContaining({ injectActiveCompanyId: true }),
    );
  });
});
