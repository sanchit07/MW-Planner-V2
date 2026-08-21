import axiosBaseQuery, { SuccessResponse } from "@api/axiosBaseQuery";
import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import { createApi } from "@reduxjs/toolkit/query/react";
import storage from "@utils/storage";

export type UserRole = "admin" | "advertiser" | "agency" | "internal";

export interface AuthUser {
  id: string;
  name: string;
  email: string;
  role: UserRole;
}

export interface TokenState {
  access_token: string;
  token_type: string;
  refresh_token: string;
  expires_in: number;
  scope: string;
  jti: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  access_token: string;
  token_type: string;
  refresh_token: string;
  expires_in: number;
  scope: string;
  state: string;
}

export interface ProfileResponse {
  id: string;
  username: string;
  email: string;
  companies: Array<{
    id: string;
    name: string;
    businessType: string;
  }>;
  phone?: string;
  locale: string;
  activated: boolean;
  firstName?: string;
  lastName?: string;
  companyPermissions: Record<string, Array<string>>;
  activeCompanyId?: string;
  updatedAt?: string;
}

// Auth state
export interface AuthState {
  isAuthenticated: boolean;
  hasPlannerAccess?: boolean;
  token: string | null;
  refreshToken: string | null;
}

const getInitialState = (): AuthState => {
  const tokenStateString = storage.getItem("auth_token");
  let tokenState = {} as LoginResponse;
  if (tokenStateString) {
    try {
      tokenState = JSON.parse(tokenStateString) || {};
    } catch {
      storage.removeItem("auth_token");
      return { isAuthenticated: false, token: null, refreshToken: null };
    }
  }
  if (tokenState?.access_token) {
    if (
      tokenState.expires_in &&
      Date.now() > tokenState.expires_in &&
      !tokenState.refresh_token
    ) {
      // Do not remove auth_token here — removing it fires a cross-tab storage
      // event that logs out other open tabs. Leave the expired token in storage;
      // the axiosInstance 401 interceptor will refresh it on the first API call.
      return {
        isAuthenticated: false,
        token: null,
        refreshToken: null,
        hasPlannerAccess: false,
      };
    }
    // Access token looks expired but a refresh token is present — trust it
    // optimistically rather than bouncing through /login. The axiosInstance
    // 401 interceptor will silently refresh on the first API call that needs it.
    try {
      return {
        isAuthenticated: true,
        hasPlannerAccess: true,
        token: tokenState.access_token,
        refreshToken: tokenState.refresh_token,
      };
    } catch {
      // Clear invalid storage if available
      storage.removeItem("auth_token");
      storage.removeItem("user_profile");
      return {
        isAuthenticated: false,
        token: null,
        refreshToken: null,
        hasPlannerAccess: false,
      };
    }
  }

  return {
    isAuthenticated: false,
    token: null,
    refreshToken: null,
  };
};

// Auth slice
export const authSlice = createSlice({
  name: "auth",
  initialState: getInitialState(),
  reducers: {
    // Add new action to set token directly (for SSO callback)
    setToken(state, action: PayloadAction<LoginResponse>) {
      state.token = action.payload.access_token;
      state.refreshToken = action.payload.refresh_token;
      state.isAuthenticated = true;
      storage.setItem("auth_token", JSON.stringify(action.payload));
    },
    setHasPlannerAccess(state, action: PayloadAction<boolean>) {
      state.hasPlannerAccess = action.payload;
    },
    logout(state) {
      state.isAuthenticated = false;
      state.token = null;
      storage.removeAll();
    },
  },
});

export const { setToken, logout, setHasPlannerAccess } = authSlice.actions;

// Auth API
export const authApi = createApi({
  reducerPath: "authApi",
  baseQuery: axiosBaseQuery(),
  endpoints: (builder) => ({
    callback: builder.mutation<
      SuccessResponse<LoginResponse>,
      { code: string; state: string; redirect_uri: string }
    >({
      query: (params) => ({
        url: "auth/oauth/callback",
        method: "GET",
        params,
      }),
    }),

    logout: builder.query<SuccessResponse<string>, { refresh_token: string }>({
      query: ({ refresh_token }) => ({
        url: "auth/logout",
        method: "POST",
        data: { refresh_token },
      }),
    }),
  }),
});

export const { useCallbackMutation, useLazyLogoutQuery } = authApi;
export default authSlice.reducer;
