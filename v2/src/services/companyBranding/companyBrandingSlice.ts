import axiosBaseQuery, {
  CustomErrorResponse,
  SuccessResponse,
} from "@api/axiosBaseQuery";
import { createApi } from "@reduxjs/toolkit/query/react";

export interface CompanyBranding {
  companyId: string;
  whiteLabel: boolean;
  logoUrl?: string | null;
}

export const companyBrandingApi = createApi({
  reducerPath: "companyBrandingApi",
  baseQuery: axiosBaseQuery(),
  tagTypes: ["CompanyBranding"],
  endpoints: (builder) => ({
    getCompanyBranding: builder.query<
      SuccessResponse<CompanyBranding> | CustomErrorResponse,
      { companyId: string }
    >({
      query: ({ companyId }) => ({
        url: `/companies/${companyId}/branding`,
        method: "GET",
      }),
      providesTags: ["CompanyBranding"],
    }),
    updateCompanyBranding: builder.mutation<
      SuccessResponse<CompanyBranding> | CustomErrorResponse,
      { companyId: string; update: Partial<CompanyBranding> }
    >({
      query: ({ companyId, update }) => ({
        url: `/companies/${companyId}/branding`,
        method: "PATCH",
        data: update,
      }),
      invalidatesTags: ["CompanyBranding"],
    }),
  }),
});

export const { useGetCompanyBrandingQuery, useUpdateCompanyBrandingMutation } =
  companyBrandingApi;
