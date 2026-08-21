import axiosBaseQuery, {
  CustomErrorResponse,
  SuccessResponse,
} from "@api/axiosBaseQuery";
import { createApi } from "@reduxjs/toolkit/query/react";

export type ReservationStatus =
  | "PENDING"
  | "HOLD_REQUESTED"
  | "RESERVED"
  | "EXPIRED"
  | "RELEASED"
  | "DECLINED"
  | "BOOKED";

export interface ReservationComment {
  userId?: string;
  companyId?: string;
  text: string;
  createdAt?: string;
}

export interface Reservation {
  id: string;
  campaignId: string;
  mediaOwnerId: string;
  inventoryId: string;
  lineItemId: string;
  requestedBy: string;
  status: ReservationStatus;
  reservedAt?: string;
  expiresAt?: string;
  extensionCount: number;
  declineReason?: string;
  comments?: ReservationComment[];
}

export interface ReservationDashboardWidgets {
  pendingHoldRequests: number;
  expiringHolds: number;
}

export const reservationApi = createApi({
  reducerPath: "reservationApi",
  baseQuery: axiosBaseQuery(),
  tagTypes: ["Reservation", "ReservationDashboardWidgets"],
  endpoints: (builder) => ({
    listReservationsForCampaign: builder.query<
      SuccessResponse<Reservation[]> | CustomErrorResponse,
      { campaignId: string }
    >({
      query: ({ campaignId }) => ({
        url: `/reservations/campaigns/${campaignId}`,
        method: "GET",
      }),
      providesTags: ["Reservation"],
    }),
    listReservationsForMediaOwner: builder.query<
      SuccessResponse<Reservation[]> | CustomErrorResponse,
      void
    >({
      query: () => ({ url: "/reservations/media-owner", method: "GET" }),
      providesTags: ["Reservation"],
    }),
    approveReservation: builder.mutation<
      SuccessResponse<Reservation> | CustomErrorResponse,
      { id: string }
    >({
      query: ({ id }) => ({ url: `/reservations/${id}/approve`, method: "POST" }),
      invalidatesTags: ["Reservation", "ReservationDashboardWidgets"],
    }),
    approveReservationWithConditions: builder.mutation<
      SuccessResponse<Reservation> | CustomErrorResponse,
      { id: string; comment: string }
    >({
      query: ({ id, comment }) => ({
        url: `/reservations/${id}/approve-with-conditions`,
        method: "POST",
        data: { comment },
      }),
      invalidatesTags: ["Reservation"],
    }),
    declineReservation: builder.mutation<
      SuccessResponse<Reservation> | CustomErrorResponse,
      { id: string; reason: string }
    >({
      query: ({ id, reason }) => ({
        url: `/reservations/${id}/decline`,
        method: "POST",
        data: { reason },
      }),
      invalidatesTags: ["Reservation", "ReservationDashboardWidgets"],
    }),
    extendReservation: builder.mutation<
      SuccessResponse<Reservation> | CustomErrorResponse,
      { id: string; additionalDays: number }
    >({
      query: ({ id, additionalDays }) => ({
        url: `/reservations/${id}/extend`,
        method: "POST",
        data: { additionalDays },
      }),
      invalidatesTags: ["Reservation"],
    }),
    releaseReservation: builder.mutation<
      SuccessResponse<Reservation> | CustomErrorResponse,
      { id: string }
    >({
      query: ({ id }) => ({ url: `/reservations/${id}/release`, method: "POST" }),
      invalidatesTags: ["Reservation", "ReservationDashboardWidgets"],
    }),
    convertReservationToBooking: builder.mutation<
      SuccessResponse<Reservation> | CustomErrorResponse,
      { id: string }
    >({
      query: ({ id }) => ({
        url: `/reservations/${id}/convert-to-booking`,
        method: "POST",
      }),
      invalidatesTags: ["Reservation"],
    }),
    getReservationDashboardWidgets: builder.query<
      SuccessResponse<ReservationDashboardWidgets> | CustomErrorResponse,
      void
    >({
      query: () => ({ url: "/reservations/dashboard-widgets", method: "GET" }),
      providesTags: ["ReservationDashboardWidgets"],
    }),
  }),
});

export const {
  useListReservationsForCampaignQuery,
  useListReservationsForMediaOwnerQuery,
  useApproveReservationMutation,
  useApproveReservationWithConditionsMutation,
  useDeclineReservationMutation,
  useExtendReservationMutation,
  useReleaseReservationMutation,
  useConvertReservationToBookingMutation,
  useGetReservationDashboardWidgetsQuery,
} = reservationApi;
