import { LoginResponse } from "@services/auth/authSlice";
import storage from "@utils/storage";
import axios, {
  AxiosInstance,
  InternalAxiosRequestConfig,
  AxiosResponse,
  AxiosError,
} from "axios";

const DEFAULT_LOGIN_PATH = "/login";
const AUTH_TOKEN_KEY = "auth_token";

function clearAuthAndRedirect(): void {
  storage.removeItem(AUTH_TOKEN_KEY);
  storage.removeItem("user_profile");
  storage.removeAll();
  sessionStorage.clear();
  window.location.href = DEFAULT_LOGIN_PATH;
}

function getStoredTokenState(): LoginResponse | null {
  const raw = storage.getItem(AUTH_TOKEN_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as LoginResponse;
  } catch {
    return null;
  }
}

interface StoredProfileCompanyState {
  activeCompanyId?: string;
  current_company?: { id?: string };
}

// Read the active company id straight from persisted storage (same source
// userSlice writes to on every profile fetch/company switch) rather than the
// Redux store, so this low-level module never imports store.ts.
function getStoredActiveCompanyId(): string | undefined {
  const raw = storage.getItem("user_profile");
  if (!raw) return undefined;
  try {
    const profile = JSON.parse(raw) as StoredProfileCompanyState;
    return profile?.activeCompanyId || profile?.current_company?.id;
  } catch {
    return undefined;
  }
}

async function refreshAccessToken(
  baseURL: string,
  refreshToken: string,
): Promise<LoginResponse> {
  const url = `${baseURL}/auth/refresh`;
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });

  if (!res.ok) {
    const err = new Error(`Token refresh failed: ${res.status}`);
    (err as Error & { status?: number }).status = res.status;
    throw err;
  }

  const data = (await res.json()) as { data?: LoginResponse } | LoginResponse;
  const payload =
    "data" in data && data.data ? data.data : (data as LoginResponse);
  if (!payload?.access_token) {
    throw new Error("Invalid refresh response");
  }
  // Server omits refresh_token in response — merge with stored token to preserve it.
  const stored = getStoredTokenState();
  return { ...stored, ...payload } as LoginResponse;
}

export interface CreateAxiosInstanceOptions {
  // Injects X-Company-Id (active company) into every request on this
  // instance, unless the request already set it explicitly.
  injectActiveCompanyId?: boolean;
}

export const createAxiosInstance = (
  baseURL: string,
  options: CreateAxiosInstanceOptions = {},
): AxiosInstance => {
  const { injectActiveCompanyId = false } = options;
  const axiosInstance: AxiosInstance = axios.create({
    baseURL,
    headers: {
      "Content-Type": "application/json",
      // Without this, the browser's own HTTP cache (not RTK Query's cache)
      // can serve a stale GET response for an identical URL - e.g.
      // navigating away and back to the same list page - even though the
      // app explicitly asked for a fresh refetch. A hard reload bypasses
      // that cache, which is why data "shows up after a hard refresh" but
      // not through the app's own reload logic.
      "Cache-Control": "no-cache",
      Pragma: "no-cache",
    },
    timeout: 60000,
  });

  let refreshPromise: Promise<LoginResponse | null> | null = null;

  axiosInstance.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
      const tokenState = getStoredTokenState();
      if (tokenState?.access_token) {
        config.headers = config.headers || {};
        config.headers.Authorization = `Bearer ${tokenState.access_token}`;
      }

      if (injectActiveCompanyId && !config.headers?.["X-Company-Id"]) {
        const activeCompanyId = getStoredActiveCompanyId();
        if (activeCompanyId) {
          config.headers = config.headers || {};
          config.headers["X-Company-Id"] = activeCompanyId;
        }
      }

      if (config.data instanceof FormData) {
        config.headers["Content-Type"] = "multipart/form-data";
      }

      return config;
    },
    (error: AxiosError) => {
      return Promise.reject(error);
    },
  );

  axiosInstance.interceptors.response.use(
    (response: AxiosResponse): AxiosResponse => {
      return response;
    },
    async (error: AxiosError): Promise<AxiosError> => {
      const originalRequest = error.config as InternalAxiosRequestConfig & {
        _retry?: boolean;
      };

      const isProfileRequest =
        typeof originalRequest?.url === "string" &&
        originalRequest.url.includes("userinfo");

      // Stale-session guard: if the IAM no longer knows the user behind this
      // token (e.g. a pre-Admin-Console demo session, or a user deleted in the
      // Admin Console), every request fails with "User not found with
      // ID/Username: ..." — refreshing won't help because the refresh token
      // carries the same unknown user. Clear the session and send the user
      // back to the login picker instead of leaving them on a broken screen.
      const responseMessage = (
        error.response?.data as { error?: { message?: string }; message?: string } | undefined
      );
      const errorMessage =
        responseMessage?.error?.message ?? responseMessage?.message ?? "";
      if (/User not found with ID\/Username/i.test(errorMessage)) {
        clearAuthAndRedirect();
        return Promise.reject(new Error("Session user no longer exists"));
      }

      if (
        error.response?.status === 401 &&
        originalRequest &&
        !originalRequest._retry &&
        !isProfileRequest
      ) {
        originalRequest._retry = true;

        const tokenState = getStoredTokenState();
        const refreshToken = tokenState?.refresh_token ?? null;

        if (!refreshToken) {
          clearAuthAndRedirect();
          return Promise.reject(new Error("Session expired"));
        }

        try {
          refreshPromise ??= refreshAccessToken(baseURL, refreshToken);
          const newTokenState = await refreshPromise;
          refreshPromise = null;

          if (!newTokenState) {
            clearAuthAndRedirect();
            return Promise.reject(new Error("Session expired"));
          }

          storage.setItem(AUTH_TOKEN_KEY, JSON.stringify(newTokenState));

          originalRequest.headers = originalRequest.headers || {};
          originalRequest.headers.Authorization = `Bearer ${newTokenState.access_token}`;
          return axiosInstance.request(originalRequest);
        } catch (refreshError) {
          refreshPromise = null;
          clearAuthAndRedirect();
          return Promise.reject(
            refreshError instanceof Error
              ? refreshError
              : new Error("Session expired"),
          );
        }
      }

      return Promise.reject(error);
    },
  );

  return axiosInstance;
};
