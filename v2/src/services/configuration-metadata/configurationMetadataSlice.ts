import axiosBaseQuery, {
  CustomErrorResponse,
  SuccessResponse,
} from "@api/axiosBaseQuery";
import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import { createApi } from "@reduxjs/toolkit/query/react";

export interface DemographicMetaData {
  demoKey: string;
  name: string;
  stringValue?: string;
  description?: string;
  children?: DemographicMetaData[];
  demoType?: string;
}

export interface DemographicsMetaData {
  [key: string]: DemographicMetaData[];
}

export interface ConfigurationsMetaData {
  campaign_status: string[];
  demographics: DemographicsMetaData;
}

const initialState: ConfigurationsMetaData = {
  campaign_status: [],
  demographics: {
    age: [],
    gender: [],
    income: [],
    interests: [],
    venues: [],
    behavior: [],
  },
};

const configurationMetadataSlice = createSlice({
  name: "configurationMetadata",
  initialState,
  reducers: {
    setConfigurationMetaData(
      state,
      action: PayloadAction<SuccessResponse<ConfigurationsMetaData>>,
    ) {
      const data = action.payload.data;
      if (data) {
        state.campaign_status = data.campaign_status || [];
        state.demographics = data.demographics || initialState.demographics;
      }
    },
  },
});

export const configurationMetadataAPI = createApi({
  reducerPath: "configurationMetadataAPI",
  baseQuery: axiosBaseQuery(),
  endpoints: (builder) => ({
    configurationMetadata: builder.query<
      SuccessResponse<ConfigurationsMetaData> | CustomErrorResponse,
      { language?: string }
    >({
      query: ({ language } = {}) => ({
        url: "/config",
        method: "GET",
        headers: language ? { "Accept-Language": language } : undefined,
      }),
    }),
  }),
});

export const { useConfigurationMetadataQuery } = configurationMetadataAPI;

export const { setConfigurationMetaData } = configurationMetadataSlice.actions;
export const configurationMetadataReducer = configurationMetadataSlice.reducer;
