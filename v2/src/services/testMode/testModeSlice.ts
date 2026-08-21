import axiosBaseQuery from "@api/axiosBaseQuery";
import { createApi } from "@reduxjs/toolkit/query/react";

export interface TestModeState {
  testMode: boolean;
  effectiveDataMode: "live" | "demo";
  locked: boolean;
}

interface SuccessEnvelope<T> {
  data?: T;
}

export const testModeApi = createApi({
  reducerPath: "testModeApi",
  baseQuery: axiosBaseQuery(),
  tagTypes: ["TestMode"],
  endpoints: (builder) => ({
    getTestMode: builder.query<SuccessEnvelope<TestModeState>, void>({
      query: () => ({ url: "/users/test-mode", method: "GET" }),
      providesTags: ["TestMode"],
    }),
    updateTestMode: builder.mutation<SuccessEnvelope<TestModeState>, boolean>({
      query: (testMode) => ({
        url: "/users/test-mode",
        method: "PUT",
        data: { testMode },
      }),
      invalidatesTags: ["TestMode"],
    }),
  }),
});

export const { useGetTestModeQuery, useUpdateTestModeMutation } = testModeApi;
