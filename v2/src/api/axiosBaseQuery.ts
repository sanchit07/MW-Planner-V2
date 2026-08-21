import { CONFIG, UrlKey } from "@config/index";
import { SerializedError } from "@reduxjs/toolkit";
import { BaseQueryFn } from "@reduxjs/toolkit/query";
import axios, {
  AxiosRequestConfig,
  AxiosInstance,
  isAxiosError,
  AxiosError,
} from "axios";
import { toast } from "react-toastify";

import { createAxiosInstance } from "./axiosInstance";

const showError = (message: string) => toast.error(message);

export interface SuccessResponse<T> {
  success: boolean;
  data?: T;
}

const excludedErrorCodes = ["ERR_9010"];

export interface CustomErrorResponse extends SerializedError {
  success: boolean;
  error?: {
    errorCode: string;
    message: string;
    path: string;
    timestamp: string;
    details: {
      password: string;
    };
  };
}

const axiosInstances: Record<string, AxiosInstance> = {
  // Planner backend — X-Company-Id (active company) selects the acting company for
  // BOTH permissions and data scoping on the server (create/list attribution, approval
  // inbox persona, execution workspace). Without it, a company switch would silently
  // fall back to the JWT's primary company.
  BACKEND_URL: createAxiosInstance(`${CONFIG.BACKEND_URL}/api/v1`, {
    injectActiveCompanyId: true,
  }),
  BACKEND_URL_PROXY: createAxiosInstance(`${CONFIG.BACKEND_URL}/proxy/`, {
    injectActiveCompanyId: true,
  }),
  ACCOUNT_URL: createAxiosInstance(`${CONFIG.ACCOUNT_URL}/api/v1`),
  // These two carry an X-Company-Id header (active company) on every
  // request — e.g. companies/agencies, companies/{id}/brands.
  ACCOUNT_PROXY_URL: createAxiosInstance(`${CONFIG.ACCOUNT_PROXY_URL}/api/v1`, {
    injectActiveCompanyId: true,
  }),
  ACCOUNT_API_URL: createAxiosInstance(`${CONFIG.ACCOUNT_API_URL}/api/v1`, {
    injectActiveCompanyId: true,
  }),
  // Recommendation engine — direct gateway call (not via the planner proxy)
  RECOMMENDATION_URL: createAxiosInstance(
    `${CONFIG.RECOMMENDATION_URL}/recommendation-engine/api/v1`,
  ),
  // Reach & frequency service — separate host, no auth/token injection
  FREQUENCY_URL: axios.create({
    baseURL: `${CONFIG.FREQUENCY_URL}/v2`,
    headers: { "Content-Type": "application/json" },
    timeout: 60000,
  }),
  // Public endpoints — no Bearer token, no 401→logout chain
  PUBLIC_URL: axios.create({
    baseURL: `${CONFIG.PUBLIC_URL}/api/v1`,
    headers: { "Content-Type": "application/json" },
    timeout: 60000,
  }),
};

const createErrorResponse = (
  message: string,
): { error: CustomErrorResponse } => ({
  error: {
    success: false,
    error: {
      message,
    },
  } as CustomErrorResponse,
});

const handleNetworkError = (silent = false) => {
  if (!silent)
    showError(
      "Network error: Unable to connect to the server. Please check your internet connection.",
    );
  return createErrorResponse("Network error: Unable to connect to the server");
};

const handleCorsError = (silent = false) => {
  if (!silent)
    showError(
      "CORS error: Cross-origin request blocked. Please contact support.",
    );
  return createErrorResponse("CORS error: Cross-origin request blocked");
};

const handleTimeoutError = (silent = false) => {
  if (!silent)
    showError(
      "Request timeout: The server took too long to respond. Please try again.",
    );
  return createErrorResponse(
    "Request timeout: The server took too long to respond",
  );
};

const isNetworkError = (axiosError: AxiosError): boolean =>
  (axiosError.code ?? "") === "ERR_NETWORK" ||
  axiosError.message.includes("Network Error");

const isCorsError = (axiosError: AxiosError): boolean =>
  (axiosError.code ?? "") === "ERR_CANCELED" ||
  axiosError.message.includes("CORS");

const isTimeoutError = (axiosError: AxiosError): boolean =>
  (axiosError.code ?? "") === "ECONNABORTED" ||
  axiosError.message.includes("timeout");

const handleServerResponseError = (
  axiosError: AxiosError,
  silent = false,
): { error: CustomErrorResponse } | null => {
  if (axiosError.response) {
    const errorResp = axiosError.response.data as CustomErrorResponse;

    if (
      !silent &&
      errorResp &&
      !excludedErrorCodes.includes(errorResp.error?.errorCode ?? "")
    ) {
      if (errorResp?.message) {
        showError(errorResp.message);
      } else {
        showError(errorResp.error?.message ?? "Something went wrong");
      }
    }
    return { error: errorResp };
  }
  return null;
};

const handleGeneralAxiosError = (
  axiosError: AxiosError,
  silent = false,
): { error: CustomErrorResponse } => {
  if (!silent) showError(axiosError.message ?? "An unexpected error occurred");
  return createErrorResponse(
    axiosError.message ?? "An unexpected error occurred",
  );
};

const axiosBaseQuery =
  (
    urlKey: UrlKey = "BACKEND_URL",
  ): BaseQueryFn<
    {
      url: string;
      method: AxiosRequestConfig["method"];
      data?: AxiosRequestConfig["data"];
      params?: AxiosRequestConfig["params"];
      headers?: Record<string, string>;
      timeout?: number;
      // Opt-in: when true, failures for this request are returned to RTK Query
      // (isError still fires) but no error toast is shown. Used by endpoints
      // that degrade gracefully via fallback data.
      suppressErrorToast?: boolean;
    },
    SuccessResponse<unknown>,
    CustomErrorResponse
  > =>
  async <T>({
    url,
    method,
    data,
    params,
    headers,
    timeout,
    suppressErrorToast = false,
  }: {
    url: string;
    method: AxiosRequestConfig["method"];
    data?: AxiosRequestConfig["data"];
    params?: AxiosRequestConfig["params"];
    headers?: Record<string, string>;
    timeout?: number;
    suppressErrorToast?: boolean;
  }) => {
    try {
      const result = await axiosInstances[urlKey].request<
        SuccessResponse<T> | CustomErrorResponse
      >({
        url,
        method,
        data,
        params,
        headers,
        timeout,
      });
      return { data: result.data };
    } catch (axiosError: unknown) {
      if (!isAxiosError(axiosError) || !axiosError) {
        // If it's not an Axios error, return a generic error
        if (!suppressErrorToast) showError("An unexpected error occurred");
        return createErrorResponse("An unexpected error occurred");
      }

      // Handle preflight errors (CORS issues)
      if (isNetworkError(axiosError)) {
        return handleNetworkError(suppressErrorToast);
      }

      // Handle CORS preflight errors
      if (isCorsError(axiosError)) {
        return handleCorsError(suppressErrorToast);
      }

      // Handle timeout errors
      if (isTimeoutError(axiosError)) {
        return handleTimeoutError(suppressErrorToast);
      }

      // Handle server response errors
      const serverError = handleServerResponseError(
        axiosError,
        suppressErrorToast,
      );
      if (serverError) {
        return serverError;
      }

      // Handle other axios errors
      return handleGeneralAxiosError(axiosError, suppressErrorToast);
    }
  };

export default axiosBaseQuery;

/** Test-only: exposes the shared instances so header-injection wiring can be asserted. */
export const __axiosInstancesForTests = axiosInstances;
