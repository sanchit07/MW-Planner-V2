import axiosBaseQuery, { SuccessResponse } from "@api/axiosBaseQuery";
import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import { createApi } from "@reduxjs/toolkit/query/react";

import {
  Brand,
  IamBrand,
  IamBrandCreateRequest,
  IamBrandsQueryParams,
  IabCategory,
  IamIabCategory,
  IabTaxonomyVersion,
  IabTaxonomyNode,
} from "../../types/brand.types";

// ── Redux UI Slice (unchanged) ─────────────────────────────────────────────
export interface BrandState {
  selectedBrand: Brand | null;
  isCreating: boolean;
  createError: string | null;
  brands: Brand[];
  isLoading: boolean;
  loadError: string | null;
}

const initialState: BrandState = {
  selectedBrand: null,
  isCreating: false,
  createError: null,
  brands: [],
  isLoading: false,
  loadError: null,
};

export const brandSlice = createSlice({
  name: "brand",
  initialState,
  reducers: {
    setSelectedBrand: (state, action: PayloadAction<Brand | null>) => {
      state.selectedBrand = action.payload;
    },
    setIsCreating: (state, action: PayloadAction<boolean>) => {
      state.isCreating = action.payload;
    },
    setCreateError: (state, action: PayloadAction<string | null>) => {
      state.createError = action.payload;
    },
    setBrands: (state, action: PayloadAction<Brand[]>) => {
      state.brands = action.payload;
    },
    setIsLoading: (state, action: PayloadAction<boolean>) => {
      state.isLoading = action.payload;
    },
    setLoadError: (state, action: PayloadAction<string | null>) => {
      state.loadError = action.payload;
    },
    resetBrandState: (state) => {
      state.selectedBrand = null;
      state.isCreating = false;
      state.createError = null;
      state.loadError = null;
    },
  },
});

export const {
  setSelectedBrand,
  setIsCreating,
  setCreateError,
  setBrands,
  setIsLoading,
  setLoadError,
  resetBrandState,
} = brandSlice.actions;

// ── IAM Brand API (ACCOUNT_PROXY_URL) ─────────────────────────────────────

// Exported so it can be unit-tested independently of the RTK Query plumbing.
export function transformBrandsResponse(response: unknown): IamBrand[] {
  const pickArray = (v: unknown): IamBrand[] | null =>
    Array.isArray(v) ? (v as IamBrand[]) : null;
  const direct = pickArray(response);
  if (direct) return direct;
  if (response && typeof response === "object") {
    const obj = response as Record<string, unknown>;
    for (const key of ["brands", "data", "items", "content", "result"]) {
      const arr = pickArray(obj[key]);
      if (arr) return arr;
    }
    if (obj.data && typeof obj.data === "object") {
      const inner = obj.data as Record<string, unknown>;
      for (const key of ["brands", "items", "content", "result"]) {
        const arr = pickArray(inner[key]);
        if (arr) return arr;
      }
    }
  }
  return [];
}

export const iamBrandApi = createApi({
  reducerPath: "iamBrandApi",
  baseQuery: axiosBaseQuery("ACCOUNT_PROXY_URL"),
  tagTypes: ["IamBrand"],
  endpoints: (builder) => ({
    // Sources brands from the company-scoped endpoint. This is the canonical
    // catalogue for the brand picker — it carries logo_url natively and reflects
    // what the company has actually onboarded.
    getAllBrands: builder.query<IamBrand[], IamBrandsQueryParams>({
      query: (params = {}) => ({
        url: `/metadata/brands`,
        method: "GET",
        params: {
          ...(params.search && { search: params.search }),
          ...(params.active_only !== undefined && {
            active_only: params.active_only,
          }),
          ...(params.iab_category && { iab_category: params.iab_category }),
          ...(params.include && { include: params.include }),
        },
      }),
      // /metadata/brands returns a flat JSON array; transformBrandsResponse also
      // tolerates the common { data: { brands: [...] } } envelope shapes.
      transformResponse: transformBrandsResponse,
      providesTags: ["IamBrand"],
    }),
    getCompanyBrands: builder.query<
      SuccessResponse<{
        brands: IamBrand[];
        total: number;
        page: number;
        limit: number;
      }>,
      { companyId: string; params?: IamBrandsQueryParams }
    >({
      query: ({ companyId, params = {} }) => ({
        url: `/companies/${companyId}/brands`,
        method: "GET",
        params: {
          include: params.include ?? "iab_categories",
          ...(params.search && { search: params.search }),
          ...(params.active_only !== undefined && {
            active_only: params.active_only,
          }),
          ...(params.iab_category && { iab_category: params.iab_category }),
          ...(params.page !== undefined && { page: params.page }),
          ...(params.limit !== undefined && { limit: params.limit }),
        },
      }),
      providesTags: ["IamBrand"],
    }),
    createBrand: builder.mutation<
      IamBrand,
      { brandData: IamBrandCreateRequest; activeCompanyId: string }
    >({
      query: ({ brandData, activeCompanyId }) => ({
        url: `/metadata/brands`,
        method: "POST",
        data: brandData,
        headers: { "X-Company-Id": activeCompanyId },
      }),
      invalidatesTags: ["IamBrand"],
    }),
    linkBrandToCompany: builder.mutation<
      void,
      { companyId: string; brandId: string }
    >({
      query: ({ companyId, brandId }) => ({
        url: `/companies/${companyId}/brands/${brandId}`,
        method: "POST",
      }),
      invalidatesTags: ["IamBrand"],
    }),
    // IAB categories straight from IAM — needed because brand creation requires
    // the category UUID (`id`), which the Planner backend's /config/brand/categories
    // does NOT expose (it only returns IAB codes like "IAB4"). Sending a code as
    // an iab_category_id makes IAM fail with "invalid UUID length".
    getIamIabCategories: builder.query<IamIabCategory[], void>({
      query: () => ({
        url: `/metadata/iab-categories`,
        method: "GET",
      }),
      // Reuse the same envelope-tolerant extraction the brand list uses — IAM
      // sometimes wraps array payloads in { data | items | content | ... }.
      transformResponse: (response: unknown): IamIabCategory[] =>
        transformBrandsResponse(response) as unknown as IamIabCategory[],
    }),

    // IAB taxonomy versions — GET /metadata/iab-taxonomy-versions.
    // Used to resolve the ID of version "3.1" before fetching the hierarchy.
    getIabTaxonomyVersions: builder.query<IabTaxonomyVersion[], void>({
      query: () => ({
        url: `/metadata/iab-taxonomy-versions`,
        method: "GET",
        // Degrades gracefully to hardcoded taxonomy fallback — don't toast.
        suppressErrorToast: true,
      }),
      transformResponse: (response: unknown): IabTaxonomyVersion[] => {
        if (Array.isArray(response)) return response as IabTaxonomyVersion[];
        if (response && typeof response === "object") {
          const obj = response as Record<string, unknown>;
          for (const key of ["data", "items", "content", "result"]) {
            if (Array.isArray(obj[key]))
              return obj[key] as IabTaxonomyVersion[];
          }
        }
        return [];
      },
    }),

    // IAB taxonomy hierarchy — GET /metadata/iab-taxonomy-versions/{id}/hierarchy.
    // Returns a flat list of nodes; each node's `children` is an array of child
    // node IDs. Top-level nodes (tier 1) have no parent; their children form tier 2.
    getIabTaxonomyHierarchy: builder.query<IabTaxonomyNode[], string>({
      query: (versionId) => ({
        url: `/metadata/iab-taxonomy-versions/${versionId}/hierarchy`,
        method: "GET",
        // Degrades gracefully to hardcoded taxonomy fallback — don't toast.
        suppressErrorToast: true,
      }),
      transformResponse: (response: unknown): IabTaxonomyNode[] => {
        if (Array.isArray(response)) return response as IabTaxonomyNode[];
        if (response && typeof response === "object") {
          const obj = response as Record<string, unknown>;
          for (const key of ["data", "items", "content", "result"]) {
            if (Array.isArray(obj[key])) return obj[key] as IabTaxonomyNode[];
          }
        }
        return [];
      },
    }),
  }),
});

export const {
  useGetAllBrandsQuery,
  useLazyGetAllBrandsQuery,
  useGetCompanyBrandsQuery,
  useLazyGetCompanyBrandsQuery,
  useCreateBrandMutation,
  useLinkBrandToCompanyMutation,
  useGetIamIabCategoriesQuery,
  useGetIabTaxonomyVersionsQuery,
  useGetIabTaxonomyHierarchyQuery,
} = iamBrandApi;

// ── Planner Config API (BACKEND_URL) — IAB categories only ────────────────
export const brandApi = createApi({
  reducerPath: "brandApi",
  baseQuery: axiosBaseQuery(),
  endpoints: (builder) => ({
    getIabCategories: builder.query<SuccessResponse<IabCategory[]>, void>({
      query: () => ({
        url: `config/brand/categories`,
        method: "GET",
      }),
    }),
  }),
});

export const { useGetIabCategoriesQuery, useLazyGetIabCategoriesQuery } =
  brandApi;

export default brandSlice.reducer;
