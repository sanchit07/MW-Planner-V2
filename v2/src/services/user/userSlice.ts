import axiosBaseQuery, {
  CustomErrorResponse,
  SuccessResponse,
} from "@api/axiosBaseQuery";
import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import { createApi } from "@reduxjs/toolkit/query/react";
import storage from "@utils/storage";

import { RootState } from "../../store";

type Permissions = Record<string, string[]>;

function getActiveCompanyId(
  profile: {
    activeCompanyId?: string;
    current_company?: { id: string };
  } | null,
): string | undefined {
  return profile?.activeCompanyId || profile?.current_company?.id;
}

export interface UpdateUserPayload {
  first_name?: string;
  last_name?: string;
  email?: string;
  phone?: string;
  username?: string;
  password?: string;
  preferred_locale?: string;
  email_verified?: boolean;
  phone_verified?: boolean;
  is_active?: boolean;
  is_global_admin?: boolean;
}
export interface UserMembershipSubscriptions {
  subscription_id: string;
  product_id: string;
  product_name: string;
  is_active: boolean;
}
export interface UserMemberships {
  company_id: string;
  company_name: string;
  role_id: string;
  role_name: string;
  is_active: boolean;
  subscriptions: UserMembershipSubscriptions[];
  company_type?: UserCompanyType;
}

export interface ChildCompanyFromUserInfo {
  id: string;
  name: string;
  companyType: {
    id: string;
    name: string;
    code: string;
    is_supplier_side: boolean;
    is_demand_side: boolean;
  };
  grantedScopes: string[];
  scopes: string[];
}

export interface UserCurrentCompany {
  id: string;
  name: string;
  seat_id: number;
  company_type: {
    id: string;
    name: string;
    code: string;
    is_supplier_side: boolean;
    is_demand_side: boolean;
  };
  role_id: string;
  role_name: string;
  childCompanies?: {
    items: ChildCompanyFromUserInfo[];
    totalCount: number;
    hasMore: boolean;
  };
}

export interface UserProfile {
  id: string;
  user_id: string;
  sub: string;
  email: string;
  username: string;
  first_name: string;
  last_name: string;
  email_verified: boolean;
  phone_verified: boolean;
  is_global_admin: boolean;
  has_system_role: boolean;
  system_permissions: string[] | null;
  permissions: Permissions | null;
  /**
   * Per-company authority map from the Admin Console-backed IAM
   * (companyId -> ["planner:plans:read", ...]). Absent on legacy profiles.
   */
  company_permissions?: Record<string, string[]>;
  memberships: UserMemberships[];
  avatar: string | null;
  phone?: string;
  locale: string;
  activated: boolean;
  current_company: UserCurrentCompany;
  activeCompanyId?: string;
  updatedAt?: string;
  primary_company_id?: string;
}

export interface UserCurrentCompany {
  company_type: UserCompanyType;
  id: string;
  name: string;
  role_id: string;
  role_name: string;
  seat_id: number;
}

export interface UserCompanyType {
  id: string;
  code: string;
  name: string;
  is_demand_side: boolean;
  is_supplier_side: boolean;
}

// User state
export interface UserState {
  profile: UserProfile | null;
  // True while additional pages of the user's companies are being fetched
  // in the background after login. Not persisted — always starts false.
  isFetchingCompanies?: boolean;
}

const getInitialState = (): UserState => {
  const userProfileStr = storage.getItem("user_profile");

  if (userProfileStr) {
    try {
      const profile = JSON.parse(userProfileStr);
      return { profile: profile, isFetchingCompanies: false };
    } catch {
      // Clear invalid storage if available
      storage.removeItem("user_profile");
      return {
        profile: null,
        isFetchingCompanies: false,
      };
    }
  }

  return {
    profile: null,
    isFetchingCompanies: false,
  };
};

// User slice
export const userSlice = createSlice({
  name: "user",
  initialState: getInitialState(),
  reducers: {
    setUserProfile(state, action: PayloadAction<SuccessResponse<UserProfile>>) {
      const newProfile = action.payload.data || null;
      const previousActiveCompanyId = state.profile?.activeCompanyId;

      // activeCompanyId is a purely client-side field (the backend never
      // returns it) — preserve it across a fresh profile fetch so switching
      // companies survives a token refresh/re-auth, as long as the new
      // profile still grants access to that company.
      if (newProfile && previousActiveCompanyId) {
        const isStillAccessible =
          newProfile.current_company?.id === previousActiveCompanyId ||
          newProfile.memberships?.some(
            (m) => m.company_id === previousActiveCompanyId,
          ) ||
          newProfile.current_company?.childCompanies?.items?.some(
            (c) => c.id === previousActiveCompanyId,
          );

        if (isStillAccessible) {
          newProfile.activeCompanyId = previousActiveCompanyId;
        }
      }

      state.profile = newProfile;

      // Store in localStorage if available
      if (newProfile) {
        storage.setItem("user_profile", JSON.stringify(newProfile));
      }
    },
    clearUserProfile(state) {
      state.profile = null;

      // Clear localStorage if available
      storage.removeItem("user_profile");
    },
    updateUserProfile(state, action: PayloadAction<Partial<UserProfile>>) {
      if (state.profile) {
        state.profile = { ...state.profile, ...action.payload };

        // Update storage with the modified profile
        storage.setItem("user_profile", JSON.stringify(state.profile));
      }
    },
    setFetchingCompanies(state, action: PayloadAction<boolean>) {
      state.isFetchingCompanies = action.payload;
    },
  },
});

export const {
  setUserProfile,
  clearUserProfile,
  updateUserProfile,
  setFetchingCompanies,
} = userSlice.actions;

// User API
export const userApi = createApi({
  reducerPath: "userApi",
  baseQuery: axiosBaseQuery(),
  endpoints: (builder) => ({
    getUserById: builder.query<
      SuccessResponse<UserProfile> | CustomErrorResponse,
      string
    >({
      queryFn: async (id, api, _extraOptions, baseQuery) => {
        const state = api.getState() as RootState;
        const activeCompanyId = getActiveCompanyId(state.profile?.profile);
        return baseQuery({
          url: `v1/users/${id}`,
          method: "GET",
          ...(activeCompanyId && {
            headers: { "X-Company-Id": activeCompanyId },
          }),
        }) as ReturnType<typeof baseQuery>;
      },
    }),
    getProfile: builder.query<
      SuccessResponse<UserProfile> | CustomErrorResponse,
      void
    >({
      queryFn: async (_arg, api, _extraOptions, baseQuery) => {
        const state = api.getState() as RootState;
        const activeCompanyId = getActiveCompanyId(state.profile?.profile);
        return baseQuery({
          url: "/users/userinfo",
          method: "GET",
          ...(activeCompanyId && {
            headers: { "X-Company-Id": activeCompanyId },
          }),
        }) as ReturnType<typeof baseQuery>;
      },
    }),
  }),
});

export const {
  useGetUserByIdQuery,
  useLazyGetUserByIdQuery,
  useGetProfileQuery,
  useLazyGetProfileQuery,
} = userApi;
export default userSlice.reducer;
