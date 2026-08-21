import axiosBaseQuery, { SuccessResponse } from "@api/axiosBaseQuery";
import { createApi } from "@reduxjs/toolkit/query/react";

import { IntercomTokenResponse } from "./types";

export const intercomApi = createApi({
  reducerPath: "intercomApi",
  baseQuery: axiosBaseQuery("ACCOUNT_PROXY_URL"),
  endpoints: (builder) => ({
    getIntercomJwt: builder.query<SuccessResponse<IntercomTokenResponse>, void>(
      {
        query: () => ({ url: "/external/intercom/jwt", method: "GET" }),
      },
    ),
  }),
});

export const { useLazyGetIntercomJwtQuery } = intercomApi;
