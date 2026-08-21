import axiosBaseQuery from "@api/axiosBaseQuery";
import { createApi } from "@reduxjs/toolkit/query/react";

import {
  AgenciesResponse,
  AgenciesQueryParams,
  CreateAgencyResponse,
  CreateAgencyRequest,
  LinkAgencyRequest,
  ChildCompany,
  LinkAgencyResponse,
} from "../../types/campaign.types"; // or move to agency.types.ts if you prefer

export const agencyApi = createApi({
  reducerPath: "agencyApi",
  baseQuery: axiosBaseQuery("ACCOUNT_PROXY_URL"),
  tagTypes: ["AgenciesList"],
  endpoints: (builder) => ({
    getAgencies: builder.query<AgenciesResponse, AgenciesQueryParams>({
      query: (params = {}) => ({
        url: `/companies/agencies`,
        method: "GET",
        params: {
          offset: (params.page || 0) * (params.size || 10),
          limit: params.size || 10,
          ...(params.search && { search: params.search }),
          ...(params.all
            ? { all: true }
            : { company_id: params.company_id || "" }),
        },
      }),
    }),
    createAgency: builder.mutation<CreateAgencyResponse, CreateAgencyRequest>({
      query: (agencyData) => ({
        url: `companies/agencies`,
        method: "POST",
        data: {
          name: agencyData.name,
          notification_email: agencyData.companyEmail,
          domain: agencyData.domain,
        },
      }),
    }),
    linkAgency: builder.mutation<
      LinkAgencyResponse, // Adjust the response type as needed
      { agencyData: LinkAgencyRequest; id: string | undefined }
    >({
      query: ({ agencyData, id }) => ({
        url: `companies/${id}/agencies`,
        method: "POST",
        data: agencyData,
      }),
    }),
    getChildCompanies: builder.query<ChildCompany, { id: string | undefined }>({
      query: ({ id }) => ({
        url: `/companies/${id}/children`,
        method: "GET",
      }),
    }),
  }),
});

export const {
  useGetAgenciesQuery,
  useLazyGetAgenciesQuery,
  useCreateAgencyMutation,
  useLinkAgencyMutation,
  useGetChildCompaniesQuery,
} = agencyApi;
