import axiosBaseQuery, {
  CustomErrorResponse,
  SuccessResponse,
} from "@api/axiosBaseQuery";
import { createAxiosInstance } from "@api/axiosInstance";
import { CONFIG } from "@config/index";
import { createApi } from "@reduxjs/toolkit/query/react";
import {
  UpdateUserPayload,
  UserMemberships,
  UserProfile,
} from "@services/user/userSlice";

export interface AccountUser {
  id: string;
  first_name: string;
  last_name: string;
  username: string;
  email: string;
  is_active: boolean;
}

export interface TenantCompany {
  access_type: string;
  child_count: number;
  company_id: string;
  company_name: string;
  company_seat_id: number;
  // The API sometimes returns this as null — callers must fall back rather
  // than assume it exists (see mapTenantCompanyToMembership).
  company_type: {
    id: string;
    name: string;
    company_type_code: string;
  } | null;
  has_parent: boolean;
  internal_support: boolean;
  is_active: boolean;
  is_primary_company: boolean;
  tenant_switch: boolean;
}

export interface GetUserCompaniesResponse {
  items: TenantCompany[];
  total: number;
  limit: number;
  offset: number;
  is_mw_internal: boolean;
}

// One-shot fetch used at login time. Uses a plain axios call so that a failure
// is silently swallowed — no toast is shown and login is never blocked.
const accountProxyInstance = createAxiosInstance(
  `${CONFIG.ACCOUNT_PROXY_URL}/api/v1`,
);

export interface TenantCompaniesPage {
  items: TenantCompany[];
  total: number;
}

export async function fetchTenantCompaniesPage(
  limit: number,
  offset: number,
): Promise<TenantCompaniesPage> {
  const res = await accountProxyInstance.get<
    SuccessResponse<GetUserCompaniesResponse>
  >("/users/me/companies", { params: { limit, offset } });
  const items = res.data?.data?.items ?? [];
  const total = res.data?.data?.total ?? items.length;
  return { items, total };
}

// One-shot, fully-paginated fetch — used where the caller can afford to wait
// for the entire company list (e.g. a background refresh, not the login path).
export async function fetchTenantCompanies(
  limit = 500,
): Promise<TenantCompany[]> {
  const items: TenantCompany[] = [];
  let offset = 0;
  let total = Infinity;

  try {
    while (items.length < total) {
      const page = await fetchTenantCompaniesPage(limit, offset);
      items.push(...page.items);
      total = page.total;
      offset += limit;

      if (page.items.length === 0) break;
    }
  } catch {
    return items;
  }

  return items;
}

export function mapTenantCompanyToMembership(
  company: TenantCompany,
): UserMemberships {
  const code = company.company_type?.company_type_code ?? "";
  return {
    company_id: company.company_id,
    company_name: company.company_name,
    role_id: "",
    role_name: "",
    is_active: company.is_active,
    subscriptions: [],
    company_type: {
      id: company.company_type?.id ?? "",
      code,
      name: company.company_type?.name ?? "",
      is_supplier_side: code === "MEDIA_OWNER",
      is_demand_side: code === "AGENCY" || code === "ADVERTISER",
    },
  };
}

export const accountApi = createApi({
  reducerPath: "accountApi",
  baseQuery: axiosBaseQuery("ACCOUNT_PROXY_URL"),
  endpoints: (builder) => ({
    getUsers: builder.query<
      SuccessResponse<AccountUser[]>,
      { company_id: string }
    >({
      query: ({ company_id }) => ({
        url: "/users",
        method: "GET",
        params: { company_id },
      }),
    }),
  }),
});

export const { useGetUsersQuery } = accountApi;

export const accountUserApi = createApi({
  reducerPath: "accountUserApi",
  baseQuery: axiosBaseQuery("ACCOUNT_API_URL"),
  endpoints: (builder) => ({
    updateUser: builder.mutation<
      SuccessResponse<UserProfile> | CustomErrorResponse,
      { id: string; data: UpdateUserPayload }
    >({
      query: ({ id, data }) => ({
        url: `/users/${id}`,
        method: "PUT",
        data,
      }),
    }),
    setPassword: builder.mutation<
      SuccessResponse<void> | CustomErrorResponse,
      { id: string; password: string }
    >({
      query: ({ id, password }) => ({
        url: `/users/${id}/set-password`,
        method: "POST",
        data: { password },
      }),
    }),
  }),
});

export const { useUpdateUserMutation, useSetPasswordMutation } = accountUserApi;
