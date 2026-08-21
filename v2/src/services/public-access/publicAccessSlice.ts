import axiosBaseQuery, { SuccessResponse } from "@api/axiosBaseQuery";
import { createApi } from "@reduxjs/toolkit/query/react";

import {
  CampaignCreateResponse,
  CostSplitByCampaignData,
  MediaPlanResponse,
} from "../../types/campaign.types";
import {
  CampaignForecastData,
  PriceSummaryResponse,
  SelectedInventoryPerformanceItem,
} from "../../types/inventory.types";

// Response types
export interface GenerateTokenResponse {
  publicToken: string;
}

export interface PublicInventoryItem {
  detail: {
    id: string;
    name: string;
    externalId: string;
    referenceId: string;
    mediaOwnerId: string;
    mediaOwnerName: string;
    inventoryType: string;
    environment: string;
    venueType: string[];
    thumbnail: string;
    format: string;
    panels: Array<{
      pixelWidth: number;
      pixelHeight: number;
      physicalWidth: number;
      physicalHeight: number;
      panelCount: number;
      unit: string;
      size: string;
    }>;
    bookingMode: string;
    execution: string;
    screens: number;
    sov: number;
    isSelected: boolean;
    isCompliant: boolean;
  };
  location: {
    location: {
      address: string;
      country: string;
      state: string;
      city: string;
      zipCode: string;
      locationCoordinates: {
        type: string;
        coordinates: Array<{
          latitude: number;
          longitude: number;
        }>;
      };
    };
    demographics: {
      age: string;
      gender: string;
      overall: string;
      ageGender: string;
      income: string;
      behaviour: string;
      interest: string;
      highestIndexScore: string;
    };
  };
  performance: {
    cpmRate: number;
    estimatedCost: number;
    perDayCost: number;
    perDayAdPlays: number;
    totalAdPlays: number;
    totalSot: number;
    plannedSot: string;
    sov: string;
    currency?: string;
  };
  operations: {
    operatingTimes: Record<
      string,
      Array<{
        start: string;
        end: string;
      }>
    >;
    maintenanceWindow: string;
    loopSize: number;
    slotDuration: number;
    clientPerLoop: number;
    cycleTime: number;
  };
  schedules: Array<{
    id: string;
    name: string;
    startDate: string;
    endDate: string;
    scheduleDays: string[];
    bookingMatrix: Record<string, number[]>;
    duration: number;
    spotsPerLoop: number;
    spotsPerHour: number;
  }>;
}

export interface PublicInventoriesResponse {
  totalPages: number;
  totalElements: number;
  size: number;
  content: PublicInventoryItem[];
  number: number;
  numberOfElements: number;
  pageable: {
    offset: number;
    pageSize: number;
    pageNumber: number;
    sort: {
      empty: boolean;
      sorted: boolean;
      unsorted: boolean;
    };
    paged: boolean;
    unpaged: boolean;
  };
  sort: {
    empty: boolean;
    sorted: boolean;
    unsorted: boolean;
  };
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface PublicInventoriesParams {
  publicToken: string;
  name?: string;
  inventoryType?: string;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
}

// Public Access API (authenticated — requires logged-in user)
export const publicAccessApi = createApi({
  reducerPath: "publicAccessApi",
  baseQuery: axiosBaseQuery(),
  endpoints: (builder) => ({
    generatePublicToken: builder.mutation<
      SuccessResponse<GenerateTokenResponse>,
      string
    >({
      query: (campaignId) => ({
        url: `/public-access/${campaignId}/generate-token`,
        method: "POST",
      }),
    }),
  }),
});

export const { useGeneratePublicTokenMutation } = publicAccessApi;

// Public Inventory API (unauthenticated — no Bearer token, no 401→logout chain)
export const publicInventoryApi = createApi({
  reducerPath: "publicInventoryApi",
  baseQuery: axiosBaseQuery("PUBLIC_URL"),
  endpoints: (builder) => ({
    getPublicInventories: builder.query<
      SuccessResponse<PublicInventoriesResponse>,
      PublicInventoriesParams
    >({
      query: (params) => {
        const {
          publicToken,
          name,
          inventoryType,
          page = 0,
          size = 10,
          sortBy = "name",
          sortDir = "asc",
        } = params;

        return {
          url: `/public-access/inventories`,
          method: "GET",
          params: {
            ...(name && { name }),
            ...(inventoryType && { inventoryType }),
            page,
            size,
            sortBy,
            sortDir,
          },
          headers: {
            "X-PUBLIC-TOKEN": publicToken,
          },
        };
      },
    }),

    getPublicMediaPlan: builder.query<
      SuccessResponse<MediaPlanResponse>,
      { publicToken: string }
    >({
      query: ({ publicToken }) => ({
        url: `/public-access/media-plan`,
        method: "GET",
        headers: {
          "X-PUBLIC-TOKEN": publicToken,
        },
      }),
    }),

    getPublicCostSplitBy: builder.query<
      SuccessResponse<CostSplitByCampaignData[]>,
      { publicToken: string; splitBy: string }
    >({
      query: ({ publicToken, splitBy }) => ({
        url: `/public-access/cost-split-by`,
        method: "GET",
        params: { splitBy },
        headers: {
          "X-PUBLIC-TOKEN": publicToken,
        },
      }),
    }),

    getPublicForecast: builder.query<
      SuccessResponse<CampaignForecastData>,
      { publicToken: string }
    >({
      query: ({ publicToken }) => ({
        url: `/public-access/forecast`,
        method: "GET",
        headers: {
          "X-PUBLIC-TOKEN": publicToken,
        },
      }),
    }),

    getPublicPriceSummary: builder.query<
      SuccessResponse<PriceSummaryResponse>,
      { publicToken: string }
    >({
      query: ({ publicToken }) => ({
        url: `/public-access/price-summary`,
        method: "GET",
        headers: {
          "X-PUBLIC-TOKEN": publicToken,
        },
      }),
    }),

    // Public twin of GET /campaigns/{id} — carries targeting.demographics
    // (gender + behaviour) and goals (goalType/targetValue).
    getPublicCampaign: builder.query<
      SuccessResponse<CampaignCreateResponse>,
      { publicToken: string }
    >({
      query: ({ publicToken }) => ({
        url: `/public-access/campaign`,
        method: "GET",
        headers: {
          "X-PUBLIC-TOKEN": publicToken,
        },
      }),
    }),

    // Public twin of /selected-inventory/all — per-item performance overlay
    // (spotRate / estimatedCost / estimatedReach) keyed by referenceId.
    getPublicAllSelectedInventory: builder.query<
      SuccessResponse<SelectedInventoryPerformanceItem[]>,
      { publicToken: string }
    >({
      query: ({ publicToken }) => ({
        url: `/public-access/selected-inventory/all`,
        method: "GET",
        headers: {
          "X-PUBLIC-TOKEN": publicToken,
        },
      }),
    }),
  }),
});

export const {
  useLazyGetPublicInventoriesQuery,
  useGetPublicMediaPlanQuery,
  useGetPublicCostSplitByQuery,
  useGetPublicForecastQuery,
  useGetPublicPriceSummaryQuery,
  useGetPublicCampaignQuery,
  useGetPublicAllSelectedInventoryQuery,
} = publicInventoryApi;
