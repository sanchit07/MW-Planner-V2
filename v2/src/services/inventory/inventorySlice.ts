import axiosBaseQuery, { SuccessResponse } from "@api/axiosBaseQuery";
import { createApi } from "@reduxjs/toolkit/query/react";
import { InventoryAvailabilityResponse } from "src/types/price-management.types";

import {
  LocationCsvFileListResponse,
  LocationCsvFileListParams,
  GeoImportLocationResponse,
} from "../../pages/campaigns/geofencing/types/location-csv.types";
import {
  InventoryListResponse,
  InventoryFilterParams,
  InventorySelectionRequest,
  InventorySelectionResponse,
  InventoryBulkSelectionRequest,
  InventoryBulkSelectByReferenceIdsRequest,
  CampaignForecastData,
  InventoryReachFrequencyRequest,
  InventoryReachFrequencyResponse,
  InventoryCsvUploadResponse,
  InventoryCsvFileListResponse,
  InventoryCsvFileListParams,
  InventoryFileListResponse,
  InventorySchedules,
  InventorySchedulePayload,
  ScheduleDays,
  CampaignSchedulePriceResponse,
  CampaignSchedulePriceParams,
  ScheduleAdjustmentRequest,
  PriceHistoryResponse,
  PriceHistoryParams,
  UpdateInventoryDiscountRequest,
  PriceSummaryResponse,
  PriceSummaryCustomFee,
  InventoryRecommendationResponse,
  InventoryRecommendationListResponse,
  InventoryDetailsResponse,
  SelectedInventoryPerformanceItem,
  ReachSaturationCurveRequest,
  ReachSaturationCurveResponse,
  InventoryMappingItem,
} from "../../types/inventory.types";

export interface VenueItem {
  enumerationId: number;
  tier: number;
  name: string;
  definition: string | null;
  stringValue: string;
  children: VenueItem[];
}

// Inventory API
export const inventoryApi = createApi({
  reducerPath: "inventoryApi",
  baseQuery: axiosBaseQuery(),
  tagTypes: ["Inventory"],
  endpoints: (builder) => ({
    // Get inventory list with filters and pagination
    getInventoryList: builder.query<
      SuccessResponse<InventoryListResponse>,
      { campaignId: string; params: InventoryFilterParams }
    >({
      query: ({ campaignId, params }) => {
        const {
          page = 0,
          size = 10,
          sortBy = "name",
          sortDir = "asc",
          ...filters
        } = params;
        return {
          url: `/campaign-inventory/${campaignId}/filter`,
          method: "POST",
          params: {
            page,
            size,
            sortBy,
            sortDir,
          },
          data: filters,
        };
      },
      providesTags: ["Inventory"],
    }),

    // Select/Deselect single inventory item
    selectInventory: builder.mutation<
      SuccessResponse<InventorySelectionResponse>,
      InventorySelectionRequest
    >({
      query: (data) => ({
        url: `/campaign-inventory/${data.campaignId}/select`,
        method: "POST",
        data,
      }),
      // No cache invalidation - selection state managed locally
    }),

    // Bulk select/deselect inventory items with filters
    bulkSelectInventory: builder.mutation<
      SuccessResponse<InventorySelectionResponse>,
      InventoryBulkSelectionRequest
    >({
      query: ({ campaignId, operationType, filters }) => ({
        url: `/campaign-inventory/${campaignId}/select-all?operationType=${operationType}`,
        method: "POST",
        data: {
          ...filters,
        },
        timeout: 180000,
      }),
    }),

    // Bulk select/deselect inventory items by pasted reference IDs — matching
    // happens server-side against the full campaign inventory set.
    bulkSelectInventoryByReferenceIds: builder.mutation<
      SuccessResponse<InventorySelectionResponse>,
      InventoryBulkSelectByReferenceIdsRequest
    >({
      query: ({ campaignId, referenceIds, operationType }) => ({
        url: `/campaign-inventory/${campaignId}/bulk-select`,
        method: "POST",
        data: { campaignId, referenceIds, operationType },
      }),
    }),
    // Persist an explicit set of inventory ids in one call (client-side manual
    // selection). Distinct from bulkSelectInventory which posts /select-all with
    // filters. operationType applies to the whole id set (additive server-side).
    bulkSelectByIds: builder.mutation<
      SuccessResponse<InventorySelectionResponse>,
      {
        campaignId: string;
        inventoryIds: string[];
        operationType: "SELECT" | "DESELECT";
      }
    >({
      query: ({ campaignId, inventoryIds, operationType }) => ({
        url: `/campaign-inventory/${campaignId}/bulk-select`,
        method: "POST",
        data: { campaignId, inventoryIds, operationType },
        timeout: 180000,
      }),
    }),

    // Get campaign forecast data
    getCampaignForecast: builder.query<
      SuccessResponse<CampaignForecastData>,
      {
        campaignId: string;
        mediaOwnerIds?: string[];
        forceRegenerate?: boolean;
      }
    >({
      query: ({ campaignId, mediaOwnerIds, forceRegenerate }) => ({
        url: `/campaign-inventory/${campaignId}/forecast`,
        method: "POST",
        params: forceRegenerate ? { forceRegenerate: true } : undefined,
        // Media owner login → scope the forecast to their inventory. Non-media
        // owners send no payload.
        data:
          mediaOwnerIds && mediaOwnerIds.length > 0
            ? { mediaOwnerIds }
            : undefined,
      }),
    }),

    // Get inventory recommendation status (poll until COMPLETED)
    generateInventoryRecommendation: builder.query<
      SuccessResponse<InventoryRecommendationResponse>,
      {
        campaignId: string;
        _requestId?: number;
        mediaOwnerIds?: string[];
        // Force a brand-new recommendation run, discarding the cached one.
        // Sent ONLY on the first /generate call of a Restore-AI-recommendation
        // click; never during the subsequent status-poll calls.
        forceRegenerate?: boolean;
      }
    >({
      query: ({ campaignId, mediaOwnerIds, forceRegenerate }) => ({
        url: `/recommendation/campaigns/${campaignId}/generate${
          forceRegenerate ? "?forceRegenerate=true" : ""
        }`,
        method: "POST",
        data:
          mediaOwnerIds && mediaOwnerIds.length > 0
            ? { mediaOwnerIds }
            : undefined,
      }),
    }),

    getInventoryRecommendationList: builder.query<
      SuccessResponse<InventoryRecommendationListResponse>,
      {
        campaignId: string;
        runId: string;
        page?: number;
        size?: number;
        search?: string;
        params?: InventoryFilterParams;
      }
    >({
      query: ({ campaignId, runId, page, size, search, params }) => ({
        url: `/recommendation/campaigns/${campaignId}/runs/${runId}/results?sort=selectionMode&sort=finalScore&${search ? `search=${search}&` : ""}page=${page ?? 0}&size=${size ?? 20}`,
        method: "POST",
        data: params,
      }),
    }),

    autoOptimizeSchedules: builder.mutation<
      SuccessResponse<string>,
      {
        campaignId: string;
      }
    >({
      query: ({ campaignId }) => ({
        url: `/recommendation/campaigns/${campaignId}/auto-optimize-schedules`,
        method: "POST",
      }),
    }),

    // Verify CSV file for inventory import
    verifyInventoryCsv: builder.mutation<
      SuccessResponse<InventoryCsvUploadResponse>,
      { campaignId: string; file: File; country: string }
    >({
      query: ({ campaignId, file, country }) => {
        const formData = new FormData();
        formData.append("csvFile", file);
        formData.append("countryName", country);
        return {
          url: `/campaign-inventory/${campaignId}/verify-csv`,
          method: "POST",
          data: formData,
        };
      },
    }),

    // Upload CSV file for inventory import
    uploadInventoryCsv: builder.mutation<
      SuccessResponse<{ totalValidInventory?: number; message?: string }>,
      { campaignId: string; file: File; country: string }
    >({
      query: ({ campaignId, file, country }) => {
        const formData = new FormData();
        formData.append("csvFile", file);
        formData.append("countryName", country);
        return {
          url: `/campaign-inventory/${campaignId}/upload-csv`,
          method: "POST",
          data: formData,
        };
      },
    }),

    // Get existing CSV files for inventory import
    getInventoryCsvFiles: builder.query<
      SuccessResponse<InventoryCsvFileListResponse>,
      { params: InventoryCsvFileListParams }
    >({
      query: ({ params }) => {
        const {
          page = 0,
          size = 10,
          sortBy = "uploadedOn",
          sortDir = "desc",
          countryName,
        } = params;
        return {
          url: `/campaign-inventory/inventory-imports`,
          method: "GET",
          params: {
            countryName,
            page,
            size,
            sortBy,
            sortDir,
          },
        };
      },
      providesTags: ["Inventory"],
    }),

    // Delete CSV file
    deleteInventoryCsvFile: builder.mutation<
      SuccessResponse<{ success: boolean; message?: string }>,
      { fileId: string }
    >({
      query: ({ fileId }) => ({
        url: `/campaign-inventory/import/${fileId}`,
        method: "DELETE",
      }),
    }),

    // Get inventory list by CSV file ID
    getInventoryByFileId: builder.query<
      SuccessResponse<InventoryFileListResponse>,
      {
        fileId: string;
        params: {
          page?: number;
          size?: number;
          sortBy?: string;
          sortDir?: "asc" | "desc" | "ASC" | "DESC";
        };
      }
    >({
      query: ({ fileId, params }) => {
        const {
          page = 0,
          size = 10,
          sortBy = "name",
          sortDir = "asc",
        } = params;
        return {
          url: `/campaign-inventory/import/${fileId}/inventories`,
          method: "GET",
          params: {
            page,
            size,
            sortBy,
            sortDir,
          },
        };
      },
      providesTags: ["Inventory"],
    }),

    // Get selected inventory list with pagination
    getSelectedInventory: builder.query<
      SuccessResponse<InventoryListResponse>,
      {
        campaignId: string;
        params: {
          page?: number;
          size?: number;
          sortBy?: string;
          sortDir?: "asc" | "desc" | "ASC" | "DESC";
        };
        // Media owner login → scope to their inventory (body). Omitted otherwise.
        mediaOwnerIds?: string[];
      }
    >({
      query: ({ campaignId, params, mediaOwnerIds }) => {
        const {
          page = 0,
          size = 10,
          sortBy = "name",
          sortDir = "asc",
        } = params;
        return {
          url: `/campaign-inventory/${campaignId}/selected-inventory`,
          method: "POST",
          params: {
            page,
            size,
            sortBy,
            sortDir,
          },
          data:
            mediaOwnerIds && mediaOwnerIds.length > 0
              ? { mediaOwnerIds }
              : undefined,
        };
      },
      providesTags: ["Inventory"],
    }),

    // All selected inventory for a campaign (unpaginated) — per-inventory
    // performance (estimatedReach + estimatedCost) used to build the Reach
    // Build chart payload.
    getAllSelectedInventory: builder.query<
      SuccessResponse<SelectedInventoryPerformanceItem[]>,
      { campaignId: string; mediaOwnerIds?: string[] }
    >({
      query: ({ campaignId, mediaOwnerIds }) => ({
        url: `/campaign-inventory/${campaignId}/selected-inventory/all`,
        method: "POST",
        // Media owner login → scope to their inventory. Omitted otherwise.
        data:
          mediaOwnerIds && mediaOwnerIds.length > 0
            ? { mediaOwnerIds }
            : undefined,
      }),
      providesTags: ["Inventory"],
    }),

    // Download CSV file
    downloadInventoryCsvFile: builder.mutation<Blob, { fileId: string }>({
      queryFn: async ({ fileId }, _queryApi, _extraOptions) => {
        try {
          // Use axios instance directly for blob response
          const { createAxiosInstance } = await import(
            "../../api/axiosInstance"
          );
          const { CONFIG } = await import("../../config/index");
          const axiosInstance = createAxiosInstance(
            `${CONFIG.BACKEND_URL}/api/v1`,
            { injectActiveCompanyId: true },
          );

          const response = await axiosInstance.get(
            `/campaign-inventory/import/${fileId}/download`,
            {
              responseType: "blob",
            },
          );

          return { data: response.data as Blob };
        } catch (error) {
          const errorMessage =
            error instanceof Error ? error.message : "Failed to download file";
          return {
            error: {
              success: false,
              error: {
                errorCode: "DOWNLOAD_ERROR",
                message: errorMessage,
                path: `/campaign-inventory/import/${fileId}/download`,
                timestamp: new Date().toISOString(),
                details: {
                  password: "",
                },
              },
            },
          };
        }
      },
    }),

    // Use CSV file for inventory import
    useInventoryCsvFile: builder.mutation<
      SuccessResponse<{ success: boolean; message?: string }>,
      { fileId: string; campaignId: string }
    >({
      query: ({ fileId, campaignId }) => ({
        url: `/campaign-inventory/${campaignId}/import/${fileId}/use`,
        method: "POST",
      }),
      invalidatesTags: ["Inventory"],
    }),

    // Get geo-import files (location CSV files)
    getGeoImportFiles: builder.query<
      SuccessResponse<LocationCsvFileListResponse>,
      { params: LocationCsvFileListParams }
    >({
      query: ({ params }) => {
        const {
          page = 0,
          size = 10,
          sortBy = "createdAt",
          sortDir = "desc",
          countryName,
        } = params;
        return {
          url: `/campaign-inventory/geo-imports`,
          method: "GET",
          params: {
            countryName,
            page,
            size,
            sortBy,
            sortDir,
          },
        };
      },
      providesTags: ["Inventory"],
    }),

    // Get geo-import file locations (view file content)
    getGeoImportLocations: builder.query<
      GeoImportLocationResponse,
      { geoImportId: string }
    >({
      query: ({ geoImportId }) => ({
        url: `/campaign-inventory/import-geo-coordinates/${geoImportId}`,
        method: "GET",
      }),
      providesTags: ["Inventory"],
    }),

    // Download geo-import file
    downloadGeoImportFile: builder.mutation<Blob, { geoImportId: string }>({
      queryFn: async ({ geoImportId }, _queryApi, _extraOptions) => {
        try {
          const { createAxiosInstance } = await import(
            "../../api/axiosInstance"
          );
          const { CONFIG } = await import("../../config/index");
          const axiosInstance = createAxiosInstance(
            `${CONFIG.BACKEND_URL}/api/v1`,
            { injectActiveCompanyId: true },
          );

          const response = await axiosInstance.get(
            `/campaign-inventory/import-geo-coordinates/${geoImportId}/download`,
            {
              responseType: "blob",
            },
          );

          return { data: response.data as Blob };
        } catch (error) {
          const errorMessage =
            error instanceof Error ? error.message : "Failed to download file";
          return {
            error: {
              success: false,
              error: {
                errorCode: "DOWNLOAD_ERROR",
                message: errorMessage,
                path: `/campaign-inventory/import-geo-coordinates/${geoImportId}/download`,
                timestamp: new Date().toISOString(),
                details: {
                  password: "",
                },
              },
            },
          };
        }
      },
    }),

    // Delete geo-import file
    deleteGeoImportFile: builder.mutation<
      SuccessResponse<{ success: boolean; message?: string }>,
      { geoImportId: string }
    >({
      query: ({ geoImportId }) => ({
        url: `/campaign-inventory/import-geo-coordinates/${geoImportId}`,
        method: "DELETE",
      }),
      invalidatesTags: ["Inventory"],
    }),

    // Import geo-coordinates (upload CSV file)
    importGeoCoordinates: builder.mutation<
      SuccessResponse<{ success: boolean; message?: string }>,
      {
        requestBody: {
          fileName: string;
          countryName: string;
          geoDetails: Array<{
            locationName: string;
            radius: string;
            latitude: string;
            longitude: string;
            siteType: string;
          }>;
        };
      }
    >({
      query: ({ requestBody }) => ({
        url: `/campaign-inventory/import-geo-coordinates`,
        method: "POST",
        data: requestBody,
      }),
      invalidatesTags: ["Inventory"],
    }),

    // Get selected inventory list with pagination
    getSelectedInventorySchedules: builder.query<
      SuccessResponse<InventorySchedules[]>,
      {
        campaignId: string;
        inventories: string[];
      }
    >({
      query: ({ campaignId, inventories }) => {
        return {
          url: `/campaign-inventory/${campaignId}/schedules`,
          method: "POST",
          data: { inventoryIds: inventories },
        };
      },
    }),
    deleteInventorySchedule: builder.mutation<
      SuccessResponse<{ success: boolean; message?: string }>,
      { campaignId: string; scheduleId: string }
    >({
      query: ({ campaignId, scheduleId }) => ({
        url: `/campaign-inventory/${campaignId}/schedules/${scheduleId}`,
        method: "DELETE",
      }),
    }),
    // Add schedules for campaign inventory
    addInventorySchedules: builder.mutation<
      SuccessResponse<{ success: boolean; data?: string }>,
      {
        campaignId: string;
        data: InventorySchedulePayload;
      }
    >({
      query: ({ campaignId, data }) => ({
        url: `/campaign-inventory/${campaignId}/schedules/add`,
        method: "POST",
        data,
      }),
      invalidatesTags: ["Inventory"],
    }),
    // Update schedules for campaign inventory
    updateInventorySchedules: builder.mutation<
      SuccessResponse<{ success: boolean; data?: string }>,
      {
        campaignId: string;
        data: InventorySchedulePayload;
        scheduleId: string | undefined;
      }
    >({
      query: ({ campaignId, data, scheduleId }) => ({
        url: `/campaign-inventory/${campaignId}/schedules/${scheduleId}`,
        method: "PUT",
        data,
      }),
      invalidatesTags: ["Inventory"],
    }),

    // Optimize schedules for campaign inventory
    optimizeInventorySchedules: builder.mutation<
      SuccessResponse<string>,
      {
        campaignId: string;
        data: {
          inventoryIds: string[];
          clearSchedules: boolean;
          schedule: {
            startDate: string;
            endDate: string;
            scheduleDays: ScheduleDays;
            bookingMatrix: Record<string, number[]>;
          };
        };
      }
    >({
      query: ({ campaignId, data }) => ({
        url: `/campaign-inventory/${campaignId}/inventory/bulk-schedules`,
        method: "POST",
        data,
      }),
    }),

    // Get campaign schedule prices
    getCampaignSchedulePrices: builder.query<
      SuccessResponse<CampaignSchedulePriceResponse>,
      {
        campaignId: string;
        params: CampaignSchedulePriceParams;
      }
    >({
      query: ({ campaignId, params }) => {
        const {
          page = 0,
          size = 10,
          sortBy = "name",
          sortDir = "asc",
          filters,
        } = params;
        return {
          url: `/price-management/campaigns/${campaignId}/schedule-prices`,
          method: "POST",
          params: {
            page,
            size,
            sortBy,
            sortDir,
          },
          data: filters || {},
        };
      },
      providesTags: ["Inventory"],
    }),

    // Apply schedule discount or bonus
    applyScheduleAdjustment: builder.mutation<
      SuccessResponse<{ success: boolean; message?: string }>,
      {
        campaignId: string;
        data: ScheduleAdjustmentRequest;
      }
    >({
      query: ({ campaignId, data }) => ({
        url: `/price-management/campaigns/${campaignId}/schedules/apply-discount-or-bonus`,
        method: "POST",
        data,
      }),
      invalidatesTags: ["Inventory"],
      // Price changes affect the Plan Approval inbox's "prices pending" state.
      async onQueryStarted(_arg, { queryFulfilled, dispatch }) {
        try {
          await queryFulfilled;
          const { campaignApi } = await import(
            "@services/campaign/campaignSlice"
          );
          dispatch(campaignApi.util.invalidateTags(["ApprovalInbox"]));
        } catch {
          // Mutation failed — nothing to invalidate.
        }
      },
    }),

    // Get price history for a campaign inventory schedule
    getPriceHistory: builder.query<
      SuccessResponse<PriceHistoryResponse>,
      {
        campaignInventoryScheduleId: string;
        params: PriceHistoryParams;
      }
    >({
      query: ({ campaignInventoryScheduleId, params }) => {
        const { page = 0, size = 10 } = params;
        return {
          url: `/price-management/campaign-inventory-schedules/${campaignInventoryScheduleId}/price-history`,
          method: "GET",
          params: {
            campaignInventoryScheduleId,
            page,
            size,
          },
        };
      },
    }),

    // Update inventory discount (proposed price)
    updateInventoryDiscount: builder.mutation<
      SuccessResponse<{ success: boolean; message?: string }>,
      {
        campaignInventoryScheduleId: string;
        data: UpdateInventoryDiscountRequest;
      }
    >({
      query: ({ campaignInventoryScheduleId, data }) => ({
        url: `/price-management/campaign-inventory-schedules/${campaignInventoryScheduleId}/update-discount`,
        method: "PUT",
        data,
      }),
      invalidatesTags: ["Inventory"],
      // Price changes affect the Plan Approval inbox's "prices pending" state.
      async onQueryStarted(_arg, { queryFulfilled, dispatch }) {
        try {
          await queryFulfilled;
          const { campaignApi } = await import(
            "@services/campaign/campaignSlice"
          );
          dispatch(campaignApi.util.invalidateTags(["ApprovalInbox"]));
        } catch {
          // Mutation failed — nothing to invalidate.
        }
      },
    }),

    // Accept all prices for a campaign
    acceptAllPrices: builder.mutation<
      SuccessResponse<{ success: boolean; message?: string }>,
      {
        campaignId: string;
        data: {
          campaignInventorySchedulesIds: string[];
        };
      }
    >({
      query: ({ campaignId, data }) => ({
        url: `/price-management/campaigns/${campaignId}/accept`,
        method: "POST",
        data,
        // Accept runs as the last step of the summary save, after the prices
        // have already been persisted. A failure here must not toast - the
        // prices did save, and the caller reports the outcome itself.
        suppressErrorToast: true,
      }),
      invalidatesTags: ["Inventory"],
      // Accepting prices unblocks approval actions — refresh the Plan
      // Approval inbox so its "prices pending" flags update.
      async onQueryStarted(_arg, { queryFulfilled, dispatch }) {
        try {
          await queryFulfilled;
          const { campaignApi } = await import(
            "@services/campaign/campaignSlice"
          );
          dispatch(campaignApi.util.invalidateTags(["ApprovalInbox"]));
        } catch {
          // Accept failed — nothing to invalidate.
        }
      },
    }),

    // Get price summary for a campaign
    getPriceSummary: builder.query<
      SuccessResponse<PriceSummaryResponse>,
      {
        campaignId: string;
      }
    >({
      query: ({ campaignId }) => ({
        url: `/price-management/campaigns/${campaignId}/price-summary`,
        method: "GET",
      }),
      providesTags: ["Inventory"],
    }),

    // Get inventories mapping (geo/location mapping) for a campaign
    getInventoriesMapping: builder.query<
      SuccessResponse<InventoryMappingItem[]>,
      {
        campaignId: string;
      }
    >({
      query: ({ campaignId }) => ({
        url: `/campaign-inventory/${campaignId}/inventories-mapping`,
        method: "GET",
      }),
      providesTags: ["Inventory"],
    }),

    // Bulk update custom fees
    bulkUpdateCustomFees: builder.mutation<
      SuccessResponse<{ success: boolean; message?: string }>,
      {
        data: PriceSummaryCustomFee[];
      }
    >({
      query: ({ data }) => ({
        url: `/price-management/custom-fees/bulk`,
        method: "POST",
        data,
      }),
      invalidatesTags: ["Inventory"],
    }),

    // Update/Delete custom fee (delete by setting isActive: false)
    updateCustomFee: builder.mutation<
      SuccessResponse<{ success: boolean; message?: string }>,
      {
        id: string;
        data: Omit<PriceSummaryCustomFee, "id">;
      }
    >({
      query: ({ id, data }) => ({
        url: `/price-management/custom-fees/${id}`,
        method: "PUT",
        data,
      }),
      invalidatesTags: ["Inventory"],
    }),

    getVenues: builder.query<VenueItem[], { language?: string }>({
      query: ({ language } = {}) => ({
        url: "/venues",
        method: "GET",
        headers: language ? { "Accept-Language": language } : undefined,
      }),
      transformResponse: (response: { success: boolean; data: VenueItem[] }) =>
        response.data ?? [],
    }),
  }),
});

// Inventory API
export const inventoryManagementApi = createApi({
  reducerPath: "inventoryManagementApi",
  baseQuery: axiosBaseQuery("BACKEND_URL_PROXY"),
  tagTypes: ["InventoryManagement"],
  endpoints: (builder) => ({
    // Apply schedule adjustment (discount or bonus)
    getInventoryAvailability: builder.query<
      SuccessResponse<InventoryAvailabilityResponse>,
      {
        data: { inventoryIds: string[]; startTime: string; endTime: string };
      }
    >({
      query: ({ data }) => ({
        url: `inventory-api/api/v1/inventories/availability`,
        method: "POST",
        data,
      }),
      providesTags: ["InventoryManagement"],
    }),

    // Manually trigger a full IMS availability sync; invalidates availability reads.
    triggerInventoryAvailabilitySync: builder.mutation<
      SuccessResponse<Record<string, unknown>>,
      void
    >({
      query: () => ({
        url: `inventory-api/api/v1/inventories/availability/sync`,
        method: "POST",
        data: {},
      }),
      invalidatesTags: ["InventoryManagement"],
    }),

    // Current IMS availability sync status (RUNNING/SUCCESS/FAILED); used to poll
    // after a manual sync trigger until the run reaches a terminal state.
    getInventoryAvailabilitySyncStatus: builder.query<
      SuccessResponse<Record<string, unknown>>,
      void
    >({
      query: () => ({
        url: `inventory-api/api/v1/inventories/availability/sync-status`,
        method: "GET",
      }),
    }),

    getInventoryDetails: builder.query<
      SuccessResponse<[InventoryDetailsResponse]>,
      {
        inventoryId: string;
      }
    >({
      query: ({ inventoryId }) => ({
        url: `inventory-api/api/v1/inventories/search?sort=name&limit=1`,
        method: "POST",
        data: { inventoryIds: [inventoryId] },
      }),
      providesTags: ["InventoryManagement"],
    }),
  }),
});

/**
 * Parse the /reach-saturation-curve response, tolerating non-finite tokens.
 *
 * The backend can emit bare `NaN` / `Infinity` for inventories with no
 * forecast — those are invalid JSON, so axios (silentJSONParsing) leaves the
 * body as a raw string instead of throwing. We swap the non-finite tokens for
 * `null` and parse it ourselves. When axios already parsed a clean payload the
 * value is an object/array and is passed through untouched.
 *
 * Only per-inventory `saturatedReach` arrays carry these tokens today (the
 * `overallReach` that feeds the chart is always finite), and the frontend does
 * not read those arrays — this keeps a bad element from breaking the whole
 * response.
 */
export function parseReachSaturationResponse(
  response: unknown,
): ReachSaturationCurveResponse {
  if (typeof response !== "string") {
    return response as ReachSaturationCurveResponse;
  }
  const sanitized = response.replace(/-?\bInfinity\b|\bNaN\b/g, "null");
  return JSON.parse(sanitized) as ReachSaturationCurveResponse;
}

// Reach & Frequency API — separate host (CONFIG.FREQUENCY_URL/v2), no auth
export const reachFrequencyApi = createApi({
  reducerPath: "reachFrequencyApi",
  baseQuery: axiosBaseQuery("FREQUENCY_URL"),
  endpoints: (builder) => ({
    // Get reach and frequency data for inventory
    getInventoryReachFrequency: builder.query<
      SuccessResponse<InventoryReachFrequencyResponse[]>,
      InventoryReachFrequencyRequest
    >({
      query: (data) => ({
        url: `/reach-and-frequency`,
        method: "POST",
        data,
      }),
      // v2 service returns a single raw object (no { success, data } wrapper).
      // Normalize to the SuccessResponse<[]> shape consumers expect.
      transformResponse: (response: SuccessResponse<unknown>) => ({
        success: true,
        data: [response as unknown as InventoryReachFrequencyResponse],
      }),
    }),

    // Reach saturation curve. Base already includes /v2, so the path is just
    // /reach-saturation-curve. Returns a raw array (no success/data wrapper).
    getReachSaturationCurve: builder.query<
      SuccessResponse<ReachSaturationCurveResponse>,
      ReachSaturationCurveRequest
    >({
      query: (data) => ({
        url: `/reach-saturation-curve`,
        method: "POST",
        data,
      }),
      transformResponse: (response: unknown) => ({
        success: true,
        data: parseReachSaturationResponse(response),
      }),
    }),
  }),
});

export const {
  useLazyGetInventoryReachFrequencyQuery,
  useLazyGetReachSaturationCurveQuery,
} = reachFrequencyApi;

export const {
  useGetInventoryListQuery,
  useLazyGetInventoryListQuery,
  useSelectInventoryMutation,
  useBulkSelectByIdsMutation,
  useBulkSelectInventoryMutation,
  useBulkSelectInventoryByReferenceIdsMutation,
  useLazyGetCampaignForecastQuery,
  useLazyGenerateInventoryRecommendationQuery,
  useLazyGetInventoryRecommendationListQuery,
  useAutoOptimizeSchedulesMutation,
  useVerifyInventoryCsvMutation,
  useUploadInventoryCsvMutation,
  useGetInventoryCsvFilesQuery,
  useDeleteInventoryCsvFileMutation,
  useDownloadInventoryCsvFileMutation,
  useGetInventoryByFileIdQuery,
  useLazyGetSelectedInventoryQuery,
  useLazyGetAllSelectedInventoryQuery,
  useUseInventoryCsvFileMutation,
  useGetGeoImportFilesQuery,
  useGetGeoImportLocationsQuery,
  useDownloadGeoImportFileMutation,
  useDeleteGeoImportFileMutation,
  useDeleteInventoryScheduleMutation,
  useImportGeoCoordinatesMutation,
  useLazyGetSelectedInventorySchedulesQuery,
  useUpdateInventorySchedulesMutation,
  useOptimizeInventorySchedulesMutation,
  useAddInventorySchedulesMutation,
  useLazyGetCampaignSchedulePricesQuery,
  useApplyScheduleAdjustmentMutation,
  useLazyGetPriceHistoryQuery,
  useUpdateInventoryDiscountMutation,
  useAcceptAllPricesMutation,
  useLazyGetPriceSummaryQuery,
  useLazyGetInventoriesMappingQuery,
  useBulkUpdateCustomFeesMutation,
  useUpdateCustomFeeMutation,
  useGetVenuesQuery,
} = inventoryApi;

export const {
  useGetInventoryAvailabilityQuery,
  useLazyGetInventoryAvailabilityQuery,
  useGetInventoryDetailsQuery,
  useLazyGetInventoryDetailsQuery,
  useTriggerInventoryAvailabilitySyncMutation,
  useLazyGetInventoryAvailabilitySyncStatusQuery,
} = inventoryManagementApi;
