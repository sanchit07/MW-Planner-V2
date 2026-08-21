import axiosBaseQuery, {
  CustomErrorResponse,
  SuccessResponse,
} from "@api/axiosBaseQuery";
import { createApi } from "@reduxjs/toolkit/query/react";

export type CreativeFormat = "VIDEO" | "STATIC" | "AUDIO" | "HTML5";

// Tier 1 (internal) approval status — every upload starts Processing; a manager (or anyone with
// approval permission) transitions it to Accepted or Inadequate. Only Accepted creatives may be
// assigned to a line item.
export type CreativeTier1Status = "PROCESSING" | "ACCEPTED" | "INADEQUATE" | "ARCHIVE";

export interface Creative {
  id: string;
  companyId: string;
  brandId?: string;
  name: string;
  format: CreativeFormat;
  mimeType?: string;
  fileUrl?: string;
  thumbnailUrl?: string;
  fileSizeBytes?: number;
  pixelWidth?: number;
  pixelHeight?: number;
  aspectRatio?: string;
  durationSeconds?: number;
  tags?: string[];
  isActive: boolean;
  tier1Status: CreativeTier1Status;
  tier1RejectionReason?: string;
  tier1ApprovedBy?: string;
  tier1ApprovedAt?: string;
}

export interface CreativeTier1Summary {
  processing: number;
  accepted: number;
  inadequate: number;
  totalCreatives: number;
  images: number;
  videos: number;
  imagesAcceptedPercent: number;
  videosAcceptedPercent: number;
}

export type CreativeBindingStatus =
  | "BOUND"
  | "FORCED_MATCH"
  | "PENDING_REAPPROVAL"
  | "REJECTED";

export interface CreativeAssignment {
  id: string;
  creativeId: string;
  lineItemId: string;
  campaignId: string;
  mediaOwnerId?: string;
  inventoryId?: string;
  bindingStatus: CreativeBindingStatus;
  forcedMatch: boolean;
  forcedMatchReason?: string;
}

export interface CreativeStatusTracker {
  totalLineItems: number;
  byFormat: { format: CreativeFormat; totalBound: number }[];
  missing: { lineItemId: string; campaignId: string; inventoryId: string }[];
}

export interface CreativeUploadInput {
  file: File;
  name: string;
  format: CreativeFormat;
  brandId?: string;
  pixelWidth?: number;
  pixelHeight?: number;
  durationSeconds?: number;
  tags?: string[];
}

export const creativeApi = createApi({
  reducerPath: "creativeApi",
  baseQuery: axiosBaseQuery(),
  tagTypes: [
    "Creative",
    "CreativeAssignment",
    "CreativeStatusTracker",
    "CreativeTier1Summary",
  ],
  endpoints: (builder) => ({
    listCreatives: builder.query<
      SuccessResponse<Creative[]> | CustomErrorResponse,
      { tier1Status?: CreativeTier1Status } | void
    >({
      query: (args) => ({
        url: "/creatives",
        method: "GET",
        params: args?.tier1Status ? { tier1Status: args.tier1Status } : undefined,
      }),
      providesTags: ["Creative"],
    }),
    updateCreativeTier1Status: builder.mutation<
      SuccessResponse<Creative> | CustomErrorResponse,
      { id: string; tier1Status: CreativeTier1Status; rejectionReason?: string }
    >({
      query: ({ id, tier1Status, rejectionReason }) => ({
        url: `/creatives/${id}/tier1-status`,
        method: "PATCH",
        data: { tier1Status, rejectionReason },
      }),
      invalidatesTags: ["Creative", "CreativeTier1Summary"],
    }),
    uploadCreative: builder.mutation<
      SuccessResponse<Creative> | CustomErrorResponse,
      CreativeUploadInput
    >({
      query: ({ file, name, format, brandId, pixelWidth, pixelHeight, durationSeconds, tags }) => {
        const formData = new FormData();
        formData.append("file", file);
        formData.append("name", name);
        formData.append("format", format);
        if (brandId) formData.append("brandId", brandId);
        if (pixelWidth != null) formData.append("pixelWidth", String(pixelWidth));
        if (pixelHeight != null) formData.append("pixelHeight", String(pixelHeight));
        if (durationSeconds != null)
          formData.append("durationSeconds", String(durationSeconds));
        (tags ?? []).forEach((tag) => formData.append("tags", tag));
        // Content-Type is intentionally left unset — axios/the browser must generate it
        // (including the multipart boundary) from the FormData instance itself; setting it
        // explicitly here would omit the boundary and break upload parsing server-side.
        return {
          url: "/creatives",
          method: "POST",
          data: formData,
        };
      },
      invalidatesTags: ["Creative"],
    }),
    deactivateCreative: builder.mutation<
      SuccessResponse<void> | CustomErrorResponse,
      { id: string }
    >({
      query: ({ id }) => ({ url: `/creatives/${id}`, method: "DELETE" }),
      invalidatesTags: ["Creative"],
    }),
    bindCreative: builder.mutation<
      SuccessResponse<CreativeAssignment> | CustomErrorResponse,
      { creativeId: string; lineItemId: string; forceMatch?: boolean }
    >({
      query: (data) => ({
        url: "/creative-assignments",
        method: "POST",
        data,
      }),
      invalidatesTags: ["CreativeAssignment", "CreativeStatusTracker"],
    }),
    getAssignmentForLineItem: builder.query<
      SuccessResponse<CreativeAssignment> | CustomErrorResponse,
      { lineItemId: string }
    >({
      query: ({ lineItemId }) => ({
        url: `/creative-assignments/line-items/${lineItemId}`,
        method: "GET",
      }),
      providesTags: ["CreativeAssignment"],
    }),
    listAssignmentsForCampaign: builder.query<
      SuccessResponse<CreativeAssignment[]> | CustomErrorResponse,
      { campaignId: string }
    >({
      query: ({ campaignId }) => ({
        url: `/creative-assignments/campaigns/${campaignId}`,
        method: "GET",
      }),
      providesTags: ["CreativeAssignment"],
    }),
    getCreativeStatusTracker: builder.query<
      SuccessResponse<CreativeStatusTracker> | CustomErrorResponse,
      void
    >({
      query: () => ({ url: "/dashboard/creative-status-tracker", method: "GET" }),
      providesTags: ["CreativeStatusTracker"],
    }),
    getCreativeTier1Summary: builder.query<
      SuccessResponse<CreativeTier1Summary> | CustomErrorResponse,
      void
    >({
      query: () => ({ url: "/dashboard/creative-tier1-summary", method: "GET" }),
      providesTags: ["CreativeTier1Summary"],
    }),
  }),
});

export const {
  useListCreativesQuery,
  useUploadCreativeMutation,
  useDeactivateCreativeMutation,
  useUpdateCreativeTier1StatusMutation,
  useBindCreativeMutation,
  useGetAssignmentForLineItemQuery,
  useListAssignmentsForCampaignQuery,
  useGetCreativeStatusTrackerQuery,
  useGetCreativeTier1SummaryQuery,
} = creativeApi;
